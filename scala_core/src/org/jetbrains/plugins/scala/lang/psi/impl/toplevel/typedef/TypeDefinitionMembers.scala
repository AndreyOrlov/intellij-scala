/**
* @author ven
*/
package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import api.base.{ScFieldId, ScPrimaryConstructor}
import com.intellij.psi.scope.{PsiScopeProcessor, ElementClassHint}
import com.intellij.psi._
import types._
import api.toplevel.typedef._
import api.statements._
import types.PhysicalSignature
import _root_.scala.collection.mutable.ListBuffer
import com.intellij.openapi.util.Key
import util._
import _root_.scala.collection.mutable.HashMap

object TypeDefinitionMembers {
  object MethodNodes extends MixinNodes {
    type T = PhysicalSignature
    def equiv(s1: PhysicalSignature, s2: PhysicalSignature) = s1 equiv s2
    def computeHashCode(s: PhysicalSignature) = s.name.hashCode * 31 + s.types.length
    def isAbstract(s: PhysicalSignature) = s.method match {
      case _: ScFunctionDeclaration => true
      case m if m.hasModifierProperty(PsiModifier.ABSTRACT) => true
      case _ => false
    }

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map) =
      for (method <- clazz.getMethods) {
        val sig = new PhysicalSignature(method, subst)
        map += ((sig, new Node(sig, subst)))
      }

    def processScala(td: ScTypeDefinition, subst: ScSubstitutor, map: Map) =
      for (member <- td.members) {
        member match {
          case method: ScFunction => {
            val sig = new PhysicalSignature(method, subst)
            map += ((sig, new Node(sig, subst)))
          }
          case _ =>
        }
      }
  }

  import com.intellij.psi.PsiNamedElement

  object ValueNodes extends MixinNodes {
    type T = PsiNamedElement
    def equiv(n1: PsiNamedElement, n2: PsiNamedElement) = n1.getName == n2.getName
    def computeHashCode(named: PsiNamedElement) = named.getName.hashCode
    def isAbstract(named: PsiNamedElement) = named match {
      case _: ScFieldId => true
      case f: PsiField if f.hasModifierProperty(PsiModifier.ABSTRACT) => true
      case _ => false
    }

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map) =
      for (field <- clazz.getFields) {
        map += ((field, new Node(field, subst)))
      }

    def processScala(td: ScTypeDefinition, subst: ScSubstitutor, map: Map) =
      for (member <- td.members) {
        member match {
          case obj: ScObject => map += ((obj, new Node(obj, subst)))
          case _var: ScVariable =>
            for (dcl <- _var.declaredElements) {
              map += ((dcl, new Node(dcl, subst)))
            }
          case _val: ScValue =>
            for (dcl <- _val.declaredElements) {
              map += ((dcl, new Node(dcl, subst)))
            }
          case constr : ScPrimaryConstructor =>
            for (param <- constr.parameters) {
              map += ((param, new Node(param, subst)))
            }
          case _ =>
        }
      }
  }

  import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

  object TypeNodes extends MixinNodes {
    type T = PsiNamedElement //class or type alias
    def equiv(t1: PsiNamedElement, t2: PsiNamedElement) = t1.getName == t2.getName
    def computeHashCode(t: PsiNamedElement) = t.getName.hashCode
    def isAbstract(t: PsiNamedElement) = t match {
      case _: ScTypeAliasDeclaration => true
      case _ => false
    }

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map) =
      for (inner <- clazz.getInnerClasses) {
        map += ((inner, new Node(inner, subst)))
      }

    def processScala(td: ScTypeDefinition, subst: ScSubstitutor, map: Map) = {
      for (member <- td.members) {
        member match {
          case alias: ScTypeAlias => map += ((alias, new Node(alias, subst)))
          case _ =>
        }
      }

      for (inner <- td.innerTypeDefinitions) {
        inner match {
          case _ : ScObject =>
          case _ => map += ((inner, new Node(inner, subst)))
        }
      }
    }
  }

  val valsKey: Key[CachedValue[ValueNodes.Map]] = Key.create("vals key")
  val methodsKey: Key[CachedValue[MethodNodes.Map]] = Key.create("methods key")
  val typesKey: Key[CachedValue[TypeNodes.Map]] = Key.create("types key")
  val signaturesKey: Key[CachedValue[HashMap[Signature, ScType]]] = Key.create("types key")

  def getVals(td: PsiClass) = get(td, valsKey, new MyProvider(td, {
    td => ValueNodes.build(td)
  }))
  def getMethods(td: PsiClass) = get(td, methodsKey, new MyProvider(td, {
    td => MethodNodes.build(td)
  }))
  def getTypes(td: PsiClass) = get(td, typesKey, new MyProvider(td, {
    td => TypeNodes.build(td)
  }))

  def getSignatures(td: PsiClass) = get(td, signaturesKey, new SignaturesProvider(td))

  private def get[T](td: PsiClass, key: Key[CachedValue[T]], provider: => CachedValueProvider[T]) = {
    var computed = td.getUserData(key)
    if (computed == null) {
      val manager = PsiManager.getInstance(td.getProject).getCachedValuesManager
      computed = manager.createCachedValue(provider, false)
      td.putUserData(key, computed)
    }
    computed.getValue
  }

  class MyProvider[T](td: PsiClass, builder: PsiClass => T) extends CachedValueProvider[T] {
    def compute() = new CachedValueProvider.Result(builder(td),
    Array[Object](PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT))
  }

  class SignaturesProvider(td: PsiClass) extends CachedValueProvider[HashMap[Signature, ScType]] {
    def compute() = {
      val res = new HashMap[Signature, ScType] {
        override def elemHashCode(s: Signature) = s.name.hashCode * 31 + s.types.length
      }
      for ((_, n) <- getVals(td)) {
        val subst = n.substitutor
        n.info match {
          case _var: ScVariable =>
            for (dcl <- _var.declaredElements) {
              val t = dcl.calcType
              res += ((new Signature(dcl.name, Seq.empty, Array(), subst), t))
              res += ((new Signature(dcl.name + "_", Seq.singleton(t), Array(), subst), Unit))
            }
          case _val: ScValue =>
            for (dcl <- _val.declaredElements) {
              res += ((new Signature(dcl.name, Seq.empty, Array(), subst), dcl.calcType))
            }
          case _ =>
        }
      }
      for ((s, _) <- getMethods(td)) {
        import s.substitutor.subst
        val retType = s.method match {
          case func : ScFunction => func.calcType
          case method => method.getReturnType match {
            case null => Unit
            case rt => ScType.create(rt, method.getProject)
          }
        }
        res += ((s, s.substitutor.subst(retType)))
      }
      new CachedValueProvider.Result(res, Array[Object](PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT))
    }
  }

  def processDeclarations(clazz : PsiClass,
                          processor: PsiScopeProcessor,
                          state: ResolveState,
                          lastParent: PsiElement,
                          place: PsiElement) : Boolean = {
    val substK = state.get(ScSubstitutor.key)
    val subst = if (substK == null) ScSubstitutor.empty else substK
    if (shouldProcessVals(processor)) {
      for ((_, n) <- getVals(clazz)) {
        if (!processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
      }
    }
    if (shouldProcessMethods(processor)) {
      for ((_, n) <- getMethods(clazz)) {
        if (!processor.execute(n.info.method, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
      }
    }
    if (shouldProcessTypes(processor)) {
      for ((_, n) <- getTypes(clazz)) {
        if (!processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
      }
    }

    true
  }

  import scala.lang.resolve._, scala.lang.resolve.ResolveTargets._

  def shouldProcessVals(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => (kinds contains VAR) || (kinds contains VAL) || (kinds contains OBJECT)
    case _ => {
      val hint = processor.getHint(classOf[ElementClassHint])
      hint == null || hint.shouldProcess(classOf[PsiVariable])
    }
  }

  def shouldProcessMethods(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => kinds contains METHOD
    case _ => {
      val hint = processor.getHint(classOf[ElementClassHint])
      hint == null || hint.shouldProcess(classOf[PsiMethod])
    }
  }

  def shouldProcessTypes(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => kinds contains CLASS
    case _ => false //important: do not process inner classes!
  }
}