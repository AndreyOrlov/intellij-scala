package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import api.statements.{ScFunction, ScFunctionDefinition}
import api.toplevel.imports.usages.ImportUsed
import caches.CachesUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.util.{PsiTreeUtil, CachedValue, PsiModificationTracker}
import lang.resolve.{ScalaResolveResult, ResolveTargets}

import types._
import _root_.scala.collection.Set
import result.TypingContext
import api.statements.params.ScTypeParam
import com.intellij.psi._
import collection.mutable.{ArrayBuffer, HashMap, HashSet}
import lang.resolve.processor.BaseProcessor
import api.expr.{ScTypedStmt, ScExpression}
import api.toplevel.ScTypedDefinition
import api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTypeDefinition}

/**
 * @author ilyas
 *
 * Mix-in implementing functionality to collect [and possibly apply] implicit conversions
 */

trait ScImplicitlyConvertible extends ScalaPsiElement {
  self: ScExpression =>

  /**
   * Get all implicit types for given expression
   */
  def getImplicitTypes : List[ScType] = {
      implicitMap.map(_._1).toList
  }

  /**
   * returns class which contains function for implicit conversion to type t.
   */
  def getClazzForType(t: ScType): Option[PsiClass] = {
    implicitMap.find(tp => t.equiv(tp._1)) match {
      case Some((_, fun, _))=> return {
        fun.getContainingClass match {
          case null => None
          case x => Some(x)
        }
      }
      case _ => None
    }
  }

  /**
   *  Get all imports used to obtain implicit conversions for given type
   */
  def getImportsForImplicit(t: ScType): Set[ImportUsed] = {
    implicitMap.find(tp => t.equiv(tp._1)).map(s => s._3) match {
      case Some(s) => s
      case None => Set()
    }
  }

  @volatile
  private var cachedImplicitMap: Seq[(ScType, ScFunctionDefinition, Set[ImportUsed])] = null

  @volatile
  private var modCount: Long = 0

  def implicitMap: Seq[(ScType, ScFunctionDefinition, Set[ImportUsed])] = {
    expectedType match {
      case Some(expected) => return buildImplicitMap
      case None =>
    }
    var tp = cachedImplicitMap
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (tp != null && modCount == curModCount) {
      return tp
    }
    tp = buildImplicitMap
    cachedImplicitMap = tp
    modCount = curModCount
    return tp
  }

  private def buildImplicitMap : Seq[(ScType, ScFunctionDefinition, Set[ImportUsed])] = {
    val processor = new CollectImplicitsProcessor

    // Collect implicit conversions from bottom to up
    def treeWalkUp(place: PsiElement, lastParent: PsiElement) {
      place match {
        case null =>
        case p => {
          if (!p.processDeclarations(processor,
            ResolveState.initial,
            lastParent, this)) return
          if (!processor.changedLevel) return
          treeWalkUp(place.getContext, place)
        }
      }
    }
    treeWalkUp(this, null)

    val typez: ScType = getType(TypingContext.empty).getOrElse(return Seq.empty)

    val expandedType: ScType = expectedType match {
      case Some(expected) => new ScFunctionType(expected, Seq(typez), getProject, getResolveScope)
      case None => typez
    }
    for ((clazz, _) <- ScalaPsiUtil.collectImplicitClasses(expandedType, this)) {
      clazz match {
        case o: ScObject => {
          clazz.processDeclarations(processor, ResolveState.initial, null, this)
        }
        case td: ScTemplateDefinition => ScalaPsiUtil.getCompanionModule(td) match {
          case Some(td: ScTypeDefinition) => {
            td.processDeclarations(processor, ResolveState.initial, null, this)
          }
          case _ =>
        }
        case _ =>
      }
    }

    val result = new ArrayBuffer[(ScType, ScFunctionDefinition, Set[ImportUsed])]
    if (typez == Nothing) return result.toSeq
    if (typez.isInstanceOf[ScUndefinedType]) return result.toSeq
    
    val sigsFound = processor.signatures.filter((sig: Signature) => {
      ProgressManager.checkCanceled
      val types = sig.types
      sig.paramLength == 1 && typez.conforms(sig.substitutor.subst(types(0)))
    }).map((sig: Signature) => sig match {
      case phys: PhysicalSignature => {
        var uSubst = Conformance.undefinedSubst(sig.substitutor.subst(sig.types.apply(0)), typez)  //todo: add missed implicit params
        phys.method match {
          case fun: ScFunction => {
            for (tParam <- fun.typeParameters) {
              val lowerType: ScType = tParam.lowerBound.getOrElse(Nothing)
              if (lowerType != Nothing) uSubst = uSubst.addLower((tParam.getName, ScalaPsiUtil.getPsiElementId(tParam)),
                sig.substitutor.subst(lowerType))
              val upperType: ScType = tParam.upperBound.getOrElse(Any)
              if (upperType != Any) uSubst = uSubst.addUpper((tParam.getName, ScalaPsiUtil.getPsiElementId(tParam)),
                sig.substitutor.subst(upperType))
            }
          }
          case method: PsiMethod => //nothing to do
        }
        uSubst.getSubstitutor match {
          case Some(s) =>  (new PhysicalSignature(phys.method, phys.substitutor.followed(s)), true)
          case _ => (sig, false)
        }
      }
      case _ => (sig, true)
    })

    //to prevent infinite recursion
    val functionContext = PsiTreeUtil.getContextOfType(this, classOf[ScFunction], false)

    for ((sig, pass) <- sigsFound if pass && (sig match {case ps: PhysicalSignature => ps.method != functionContext; case _ => false})) {
      val set = processor.sig2Method(sig match {case ps: PhysicalSignature => ps.method.asInstanceOf[ScFunctionDefinition]})
      for ((imports, fun) <- set) {
        val rt = sig.substitutor.subst(fun.returnType.getOrElse(Any))

        def register(t: ScType) = {
            result += Tuple(t, fun, imports)
        }
        rt match {
          // This is needed to pass OptimizeImportsImplicitsTest.testImplicitReference2
          case ct: ScCompoundType => {
            register(ct)
            for (t <- ct.components)
              register(t)
          }
          case t => register(t)
        }
      }
    }
    result.toSeq
  }


  import ResolveTargets._
  private class CollectImplicitsProcessor extends BaseProcessor(Set(METHOD)) {
    private val signatures2ImplicitMethods = new HashMap[ScFunctionDefinition, Set[Pair[Set[ImportUsed], ScFunctionDefinition]]]

    private val signaturesSet: HashSet[Signature] = new HashSet[Signature] //signatures2ImplicitMethods.keySet.toArray[Signature]

    def signatures: Array[Signature] = signaturesSet.toArray

    def sig2Method = signatures2ImplicitMethods

    def execute(element: PsiElement, state: ResolveState) = {

      val subst: ScSubstitutor = state.get(ScSubstitutor.key) match {
        case null => ScSubstitutor.empty
        case s => s
      }

      element match {
        case named: PsiNamedElement if kindMatches(element) => named match {
          case f: ScFunctionDefinition
            // Collect implicit conversions only
            if f.hasModifierProperty("implicit") &&
                    f.getParameterList.getParametersCount == 1 => {
            val sign = new PhysicalSignature(f, subst.followed(inferMethodTypesArgs(f, subst)))
            if (!signatures2ImplicitMethods.contains(f)) {
              val newFSet = Set((getImports(state), f))
              signatures2ImplicitMethods += ((f -> newFSet))
              signaturesSet += sign
            } else {
              signatures2ImplicitMethods += ((f -> (signatures2ImplicitMethods(f) + Pair(getImports(state), f))))
              signaturesSet += sign
            }

            candidatesSet += new ScalaResolveResult(f, getSubst(state), getImports(state))
          }
          //todo add implicit objects
          case _ =>
        }
        case _ =>
      }
      true

    }

    /**
     Pick all type parameters by method maps them to the appropriate type arguments, if they are
     */
    def inferMethodTypesArgs(fun: ScFunction, classSubst: ScSubstitutor) = {
      fun.typeParameters.foldLeft(ScSubstitutor.empty) {
        (subst, tp) => subst.bindT((tp.getName, ScalaPsiUtil.getPsiElementId(tp)), ScUndefinedType(new ScTypeParameterType(tp: ScTypeParam, classSubst)))
      }
    }
  }

  protected object MyImplicitCollector {
  }

}

object ScImplicitlyConvertible {
  val IMPLICIT_RESOLUTION_KEY: Key[PsiClass] = Key.create("implicit.resolution.key")
  val IMPLICIT_CONVERSIONS_KEY: Key[CachedValue[collection.Map[ScType, Set[(ScFunctionDefinition, Set[ImportUsed])]]]] = Key.create("implicit.conversions.key")

  case class Implicit(tp: ScType, fun: ScTypedDefinition, importsUsed: Set[ImportUsed])
}
