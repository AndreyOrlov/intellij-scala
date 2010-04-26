package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import psi.api.base.ScReferenceElement
import psi.api.statements._
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import psi.types._

import nonvalue.{Parameter, TypeParameter, ScTypePolymorphicType, ScMethodType}
import psi.api.base.types.ScTypeElement
import result.{TypingContext}
import scala._
import collection.mutable.{HashSet, ListBuffer, ArrayBuffer}
import scala.collection.Set
import psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import psi.api.expr.{ScMethodCall, ScGenericCall}
import psi.implicits.{ScImplicitlyConvertible}
import psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}
import psi.ScalaPsiUtil
import psi.api.toplevel.typedef.{ScClass, ScObject}
import psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportWildcardSelectorUsed, ImportUsed}
import psi.impl.toplevel.synthetic.{ScSyntheticClass, ScSyntheticFunction}
import Compatibility.Expression
import psi.impl.ScPackageImpl

//todo: remove all argumentClauses, we need just one of them
class MethodResolveProcessor(override val ref: PsiElement,
                             refName: String,
                             argumentClauses: List[Seq[Expression]],
                             typeArgElements: Seq[ScTypeElement],
                             kinds: Set[ResolveTargets.Value] = StdKinds.methodRef,
                             expectedOption: => Option[ScType] = None,
                             isUnderscore: Boolean = false) extends ResolveProcessor(kinds, ref, refName) {

  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    val implicitConversionClass: Option[PsiClass] = state.get(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY) match {
      case null => None
      case x => Some(x)
    }
    if (nameAndKindMatch(named, state)) {
      if (!isAccessible(named, ref)) return true
      val s = getSubst(state)
      element match {
        case m: PsiMethod => {
          //all this code for implicit overloading reesolution
          //todo: this is bad code, should be rewrited
          val res = new ScalaResolveResult(m, s, getImports(state), None, implicitConversionClass)
          ((candidatesSet ++ levelSet).find(p => p.hashCode == res.hashCode), implicitConversionClass) match {
            case (Some(oldRes: ScalaResolveResult), Some(newClass)) => {
              val oldClass = oldRes.implicitConversionClass
              oldClass match {
                case Some(clazz: PsiClass) if clazz.isInheritor(newClass, true) =>
                case _ => {
                  candidatesSet.remove(oldRes)
                  levelSet.remove(oldRes)
                  levelSet += res
                }
              }
            }
            case _ => addResult(res)
          }
          true
        }
        case cc: ScClass if cc.isCase && ref.getParent.isInstanceOf[ScMethodCall] ||
                ref.getParent.isInstanceOf[ScGenericCall] => {
          addResult(new ScalaResolveResult(cc, s, getImports(state), None, implicitConversionClass))
          true
        }
        case cc: ScClass if cc.isCase && !ref.getParent.isInstanceOf[ScReferenceElement] &&
                ScalaPsiUtil.getCompanionModule(cc) == None => {
          addResult(new ScalaResolveResult(cc.constructor.getOrElse(return true), s, getImports(state), None,
            implicitConversionClass))
          true
        }
        case cc: ScClass if cc.isCase && ScalaPsiUtil.getCompanionModule(cc) == None => {
          addResult(new ScalaResolveResult(named, s, getImports(state), None, implicitConversionClass))
        }
        case cc: ScClass => true
        case o: ScObject if o.isPackageObject => {
          addResult(new ScalaResolveResult(o, s, getImports(state), None, implicitConversionClass))
          return true
        }
        case o: ScObject if ref.getParent.isInstanceOf[ScMethodCall] || ref.getParent.isInstanceOf[ScGenericCall] => {
          for (sign: PhysicalSignature <- o.signaturesByName("apply")) {
            val m = sign.method
            val subst = sign.substitutor
            addResult(new ScalaResolveResult(m, s.followed(subst), getImports(state), None, implicitConversionClass))
          }
          true
        }
        case synthetic: ScSyntheticFunction => {
          addResult(new ScalaResolveResult(synthetic, s, getImports(state), None, implicitConversionClass))
        }
        case pack: PsiPackage =>
          addResult(new ScalaResolveResult(ScPackageImpl(pack), s, getImports(state), None, implicitConversionClass))
        case _ => {
          addResult(new ScalaResolveResult(named, s, getImports(state), None, implicitConversionClass))
          true
        }
      }
    }
    return true
  }

  private def isApplicable(c: ScalaResolveResult, checkWithImplicits: Boolean): Boolean = {
    def getTypeArgumentsSubstitutor: ScSubstitutor = {
      if (typeArgElements.length  != 0) {
        c.element match {
          case t: ScTypeParametersOwner if t.typeParameters.length == typeArgElements.length => {
            ScalaPsiUtil.genericCallSubstitutor(t.typeParameters.map(p => (p.name, ScalaPsiUtil.getPsiElementId(p))), typeArgElements)
          }
          case p: PsiTypeParameterListOwner if p.getTypeParameters.length == typeArgElements.length => {
            ScalaPsiUtil.genericCallSubstitutor(p.getTypeParameters.map(p => (p.getName, ScalaPsiUtil.getPsiElementId(p))), typeArgElements)
          }
          case _ => ScSubstitutor.empty
        }
      } else ScSubstitutor.empty
    }

    val substitutor: ScSubstitutor =
    getType(c.element) match {
      case ScTypePolymorphicType(_, typeParams) => {
        val s: ScSubstitutor = typeParams.foldLeft(ScSubstitutor.empty) {
          (subst: ScSubstitutor, tp: TypeParameter) =>
            subst.bindT((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)), new ScUndefinedType(new ScTypeParameterType(tp.ptp, ScSubstitutor.empty)))
        }
        c.substitutor.followed(s).followed(getTypeArgumentsSubstitutor)
      }
      case _ => c.substitutor.followed(getTypeArgumentsSubstitutor)
    }

    def checkFunction(fun: PsiNamedElement): Boolean = {
      fun match {
        case fun: ScFunction if fun.parameters.length == 0 || isUnderscore => true
        case fun: ScFun if fun.paramTypes.length == 0 || isUnderscore => true
        case method: PsiMethod if method.getParameterList.getParameters.length == 0 || isUnderscore => true
        case _ => {
          expectedOption match {
            case Some(ScFunctionType(retType, params)) => {
              val args = params.map(new Expression(_))
              Compatibility.compatible(fun, substitutor, List(args), false, ref.getResolveScope)._1
            }
            case Some(p@ScParameterizedType(des, typeArgs)) if p.getFunctionType != None => {
              val args = typeArgs.slice(0, typeArgs.length - 1).map(new Expression(_))
              Compatibility.compatible(fun, substitutor, List(args), false, ref.getResolveScope)._1
            }
            case _ => false
          }
        }
      }

    }

    c.element match {
      //todo: add values and objects
      //Implicit Application
      case fun: ScFunction  if (typeArgElements.length == 0 ||
              typeArgElements.length == fun.typeParameters.length) && fun.paramClauses.clauses.length == 1 &&
              fun.paramClauses.clauses.apply(0).isImplicit &&
              argumentClauses.length == 0 => true //special case for cases like Seq.toArray
      //eta expansion
      case fun: ScTypeParametersOwner if (typeArgElements.length == 0 ||
              typeArgElements.length == fun.typeParameters.length) && argumentClauses.length == 0 &&
              fun.isInstanceOf[PsiNamedElement] => {
        if (fun.isInstanceOf[ScFunction] && fun.asInstanceOf[ScFunction].isConstructor) return false
        checkFunction(fun.asInstanceOf[PsiNamedElement])
      }
      case fun: PsiTypeParameterListOwner if (typeArgElements.length == 0 ||
              typeArgElements.length == fun.getTypeParameters.length) && argumentClauses.length == 0 &&
              fun.isInstanceOf[PsiNamedElement] => {
        checkFunction(fun.asInstanceOf[PsiNamedElement])
      }
      //simple application including empty application
      case tp: ScTypeParametersOwner if (typeArgElements.length == 0 ||
              typeArgElements.length == tp.typeParameters.length) && tp.isInstanceOf[PsiNamedElement] => {
        val args = argumentClauses.headOption.toList
        Compatibility.compatible(tp.asInstanceOf[PsiNamedElement], substitutor, args, checkWithImplicits, ref.getResolveScope)._1
      }
      case tp: PsiTypeParameterListOwner if (typeArgElements.length == 0 ||
              typeArgElements.length == tp.getTypeParameters.length) &&
              tp.isInstanceOf[PsiNamedElement] => {
        val args = argumentClauses.headOption.toList
        Compatibility.compatible(tp.asInstanceOf[PsiNamedElement], substitutor, args, checkWithImplicits, ref.getResolveScope)._1
      }
      case _ => false
    }
  }

  override def candidates[T >: ScalaResolveResult : ClassManifest]: Array[T] = {
    val set = candidatesSet ++ levelSet
    var filtered = set.filter(isApplicable(_, false))
    val withImplicit = filtered.isEmpty
    if (filtered.isEmpty) filtered = set.filter(isApplicable(_, true)) //do not try implicit conversions if exists something without it
    val applicable: Set[ScalaResolveResult] = filtered

    if (applicable.isEmpty) set.toArray.map(r =>
      if (r.element.isInstanceOf[PsiMethod] || r.element.isInstanceOf[ScFun]) r.copy(applicable = false) else r)
    else {
      mostSpecific(applicable) match {
        case Some(r) => Array(r)
        case None => applicable.toArray
      }
    }
  }

  private def isAsSpecificAs(r1: ScalaResolveResult, r2: ScalaResolveResult): Boolean = {
    def lastRepeated(params: Seq[Parameter]): Boolean = {
      params.lastOption.getOrElse(return false).isRepeated
    }
    (r1.element, r2.element) match {
      case (m1@(_: PsiMethod | _: ScFun), m2@(_: PsiMethod | _: ScFun)) => {
        val (t1, t2) = (getType(m1), getType(m2))
        def calcParams(tp: ScType): Seq[Parameter] = {
          tp match {
            case ScMethodType(_, params, _) => params
            case ScTypePolymorphicType(ScMethodType(_, params, _), typeParams) => {
              val s: ScSubstitutor = typeParams.foldLeft(ScSubstitutor.empty) {
                (subst: ScSubstitutor, tp: TypeParameter) =>
                  subst.bindT((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)), new ScExistentialArgument(tp.name, List.empty, tp.lowerType, tp.upperType))
              }
              params.map(p => Parameter(p.name, s.subst(p.paramType), p.isDefault, p.isRepeated))
            }
            case _ => Seq.empty
          }
        }
        val (params1, params2) = (calcParams(t1), calcParams(t2))
        if (lastRepeated(params1) && !lastRepeated(params2)) return false
        val i: Int = if (argumentClauses.length > 0 && params1.length > 0) 0.max(argumentClauses.apply(0).
                length - params1.length) else 0
        val default: Expression = new Expression(if (params1.length > 0) params1.last.paramType else Nothing)
        val exprs: Seq[Expression] = params1.map(p => new Expression(p.paramType)) ++ Seq.fill(i)(default)
        return Compatibility.checkConformance(false, params2, exprs, false)._1
      }
      case (_, m2: PsiMethod) => return true
      case (e1, e2) => return Compatibility.compatible(getType(e1), getType(e2))
    }
  }

  private def getClazz(r: ScalaResolveResult): Option[PsiClass] = {
    val element = ScalaPsiUtil.nameContext(r.element)
    element match {
      case memb: PsiMember => {
        val clazz = memb.getContainingClass
        if (clazz == null) None else Some(clazz)
      }
      case _ => None
    }
  }

  private def isDerived(c1: Option[PsiClass], c2: Option[PsiClass]): Boolean = {
    (c1, c2) match {
      case (Some(c1), Some(c2)) => {
        if (c1 == c2) return false
        if (c1.isInheritor(c2, true)) return true
        ScalaPsiUtil.getCompanionModule(c1) match {
          case Some(c1) => if (c1.isInheritor(c2, true)) return true
          case _ =>
        }
        ScalaPsiUtil.getCompanionModule(c2) match {
          case Some(c2) => if (c1.isInheritor(c2, true)) return true
          case _ =>
        }
        return false
      }
      case _ => false
    }
  }

  private def relativeWeight(r1: ScalaResolveResult, r2: ScalaResolveResult): Int = {
    val s1 = if (isAsSpecificAs(r1, r2)) 1 else 0
    val s2 = if (isDerived(getClazz(r1), getClazz(r2))) 1 else 0
    s1 + s2
  }

  private def isMoreSpecific(r1: ScalaResolveResult, r2: ScalaResolveResult): Boolean = {
    (r1.implicitConversionClass, r2.implicitConversionClass) match {
      case (Some(t1), Some(t2)) => if (t1.isInheritor(t2, true)) return true
      case _ =>
    }
    relativeWeight(r1, r2) > relativeWeight(r2, r1)
  }

  private def mostSpecific(applicable: Set[ScalaResolveResult]): Option[ScalaResolveResult] = {
    for (a1 <- applicable) {
      var break = false
      for (a2 <- applicable if a1 != a2 && !break) {
        if (!isMoreSpecific(a1, a2)) break = true
      }
      if (!break) return Some(a1)
    }
    return None
  }

  //todo: implement existential dual
  private def getType(e: PsiNamedElement): ScType = e match {
    case fun: ScFun => fun.polymorphicType
    case f: ScFunction => f.polymorphicType
    case m: PsiMethod => ResolveUtils.javaPolymorphicType(m, ScSubstitutor.empty, ref.getResolveScope)
    case refPatt: ScReferencePattern => refPatt.getParent /*id list*/ .getParent match {
      case pd: ScPatternDefinition if (PsiTreeUtil.isAncestor(pd, ref, true)) =>
        pd.declaredType match {case Some(t) => t; case None => Nothing}
      case vd: ScVariableDefinition if (PsiTreeUtil.isAncestor(vd, ref, true)) =>
        vd.declaredType match {case Some(t) => t; case None => Nothing}
      case _ => refPatt.getType(TypingContext.empty).getOrElse(Any)
    }
    case typed: ScTypedDefinition => typed.getType(TypingContext.empty).getOrElse(Any)
    case _ => Nothing
  }
}
