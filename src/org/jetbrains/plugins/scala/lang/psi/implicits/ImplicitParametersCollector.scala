package org.jetbrains.plugins.scala.lang.psi.implicits

import org.jetbrains.plugins.scala.lang.psi.types._
import nonvalue.{TypeParameter, ScTypePolymorphicType, Parameter, ScMethodType}
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.psi.{types, ScalaPsiUtil}
import processor.{ImplicitProcessor, MostSpecificUtil}
import result.{TypeResult, Success, TypingContext}
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import params.{ScClassParameter, ScParameter}
import util.PsiTreeUtil
import collection.immutable.HashSet
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScMember}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.extensions.{toPsiNamedElementExt, toPsiClassExt}
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.SafeCheckException
import annotation.tailrec
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScExistentialClause
import org.jetbrains.plugins.scala.lang.psi.types.Conformance.AliasType

/**
 * @param place        The call site
 * @param tp           Search for an implicit definition of this type. May have type variables.
 *
 * User: Alexander Podkhalyuzin
 * Date: 23.11.2009
 */
class ImplicitParametersCollector(place: PsiElement, tp: ScType, searchImplicitsRecursively: Int = ImplicitParametersCollector.SEARCH_ITERATIONS) {
  def collect: Seq[ScalaResolveResult] = {
    var processor = new ImplicitParametersProcessor(false)
    def treeWalkUp(placeForTreeWalkUp: PsiElement, lastParent: PsiElement) {
      if (placeForTreeWalkUp == null) return
      if (!placeForTreeWalkUp.processDeclarations(processor,
        ResolveState.initial(), lastParent, place)) return
      place match {
        case (_: ScTemplateBody | _: ScExtendsBlock) => //template body and inherited members are at the same level
        case _ => if (!processor.changedLevel) return
      }
      treeWalkUp(placeForTreeWalkUp.getContext, placeForTreeWalkUp)
    }
    treeWalkUp(place, null) //collecting all references from scope

    val candidates = processor.candidatesS.toSeq
    if (!candidates.isEmpty && !candidates.forall(r => !r.problems.isEmpty)) return candidates

    processor = new ImplicitParametersProcessor(true)

    for (obj <- ScalaPsiUtil.collectImplicitObjects(tp, place)) {
      processor.processType(obj, place, ResolveState.initial())
    }

    val secondCandidates = processor.candidatesS.toSeq
    if (secondCandidates.isEmpty) candidates
    else secondCandidates
  }

  class ImplicitParametersProcessor(withoutPrecedence: Boolean) extends ImplicitProcessor(StdKinds.refExprLastRef, withoutPrecedence) {
    protected def getPlace: PsiElement = place

    def execute(element: PsiElement, state: ResolveState): Boolean = {
      if (!kindMatches(element)) return true
      val named = element.asInstanceOf[PsiNamedElement]
      val subst = getSubst(state)
      named match {
        case o: ScObject if o.hasModifierProperty("implicit") =>
          if (!ResolveUtils.isAccessible(o, getPlace)) return true
          addResult(new ScalaResolveResult(o, subst, getImports(state)))
        case param: ScParameter if param.isImplicitParameter =>
          param match {
            case c: ScClassParameter =>
              if (!ResolveUtils.isAccessible(c, getPlace)) return true
            case _ =>
          }
          addResult(new ScalaResolveResult(param, subst, getImports(state)))
        case patt: ScBindingPattern => {
          val memb = ScalaPsiUtil.getContextOfType(patt, true, classOf[ScValue], classOf[ScVariable])
          memb match {
            case memb: ScMember if memb.hasModifierProperty("implicit") =>
              if (!ResolveUtils.isAccessible(memb, getPlace)) return true
              addResult(new ScalaResolveResult(named, subst, getImports(state)))
            case _ =>
          }
        }
        case function: ScFunction if function.hasModifierProperty("implicit") => {
          if (!ResolveUtils.isAccessible(function, getPlace)) return true
          addResult(new ScalaResolveResult(named, subst, getImports(state)))
        }
        case _ =>
      }
      true
    }

    override def candidatesS: scala.collection.Set[ScalaResolveResult] = {
      val clazz = ScType.extractClass(tp)
      def forFilter(c: ScalaResolveResult): Option[(ScalaResolveResult, ScSubstitutor)] = {
        def compute(): Option[(ScalaResolveResult, ScSubstitutor)] = {
          val subst = c.substitutor
          c.element match {
            case o: ScObject if !PsiTreeUtil.isContextAncestor(o, place, false) =>
              o.getType(TypingContext.empty) match {
                case Success(objType: ScType, _) =>
                  if (!subst.subst(objType).conforms(tp)) None
                  else Some(c, subst)
                case _ => None
              }
            case param: ScParameter if !PsiTreeUtil.isContextAncestor(param, place, false) =>
              param.getType(TypingContext.empty) match {
                case Success(paramType: ScType, _) =>
                  if (!subst.subst(paramType).conforms(tp)) None
                  else Some(c, subst)
                case _ => None
              }
            case patt: ScBindingPattern
              if !PsiTreeUtil.isContextAncestor(ScalaPsiUtil.nameContext(patt), place, false) => {
              patt.getType(TypingContext.empty) match {
                case Success(pattType: ScType, _) =>
                  if (!subst.subst(pattType).conforms(tp)) None
                  else Some(c, subst)
                case _ => None
              }
            }
            case fun: ScFunction if !PsiTreeUtil.isContextAncestor(fun, place, false) => {
              val oneImplicit = fun.paramClauses.clauses.length == 1 && fun.paramClauses.clauses.apply(0).isImplicit
              //to avoid checking implicit functions in case of simple implicit parameter search
              if (!oneImplicit && fun.paramClauses.clauses.length > 0) {
                clazz match {
                  case Some(cl) =>
                    val clause = fun.paramClauses.clauses(0)
                    val funNum = clause.parameters.length
                    val qName = "scala.Function" + funNum
                    val classQualifiedName = cl.qualifiedName
                    if (classQualifiedName != qName && classQualifiedName != "java.lang.Object" &&
                        classQualifiedName != "scala.ScalaObject") return None
                  case _ =>
                }
              }

              fun.getTypeNoImplicits(TypingContext.empty) match {
                case Success(funType: ScType, _) => {
                  def checkType(ret: ScType): Option[(ScalaResolveResult, ScSubstitutor)] = {
                    val typeParameters = fun.typeParameters
                    val lastImplicit = fun.effectiveParameterClauses.lastOption.flatMap {
                      case clause if clause.isImplicit => Some(clause)
                      case _ => None
                    }
                    if (typeParameters.isEmpty && lastImplicit.isEmpty) Some(c, subst)
                    else {
                      val methodType = lastImplicit.map(li => subst.subst(ScMethodType(ret, li.getSmartParameters, isImplicit = true)
                        (place.getProject, place.getResolveScope))).getOrElse(ret)
                      var nonValueType: TypeResult[ScType] =
                        Success(if (typeParameters.isEmpty) methodType
                        else ScTypePolymorphicType(methodType, typeParameters.map(tp =>
                          TypeParameter(tp.name, tp.lowerBound.getOrNothing, tp.upperBound.getOrAny, tp))), Some(place))
                      try {
                        val expected = Some(tp)
                        nonValueType = InferUtil.updateAccordingToExpectedType(nonValueType,
                          fromImplicitParameters = true, expected, place, check = true, checkAnyway = true)

                        if (lastImplicit.isDefined) {
                          val (resType, _) = InferUtil.updateTypeWithImplicitParameters(nonValueType.getOrElse(throw new SafeCheckException),
                            place, check = true, searchImplicitsRecursively - 1, checkAnyway = true)
                          Some(c.copy(implicitParameterType = Some(resType.inferValueType)), subst)
                        } else {
                          Some(c.copy(implicitParameterType = Some(nonValueType.getOrElse(throw new SafeCheckException).inferValueType)), subst)
                        }
                      } catch {
                        case e: SafeCheckException =>
                          return Some(c.copy(problems = Seq(WrongTypeParameterInferred)), subst)
                      }
                    }
                  }

                  val inferredSubst = subst.followed(ScalaPsiUtil.inferMethodTypesArgs(fun, subst))
                  val substedFunType: ScType = inferredSubst.subst(funType)
                  if (substedFunType conforms tp) checkType(substedFunType)
                  else {
                    ScType.extractFunctionType(substedFunType) match {
                      case Some(ScFunctionType(ret, params)) if params.length == 0 =>
                        if (!ret.conforms(tp)) None
                        else checkType(ret)
                      case _ => None
                    }
                  }
                }
                case _ => None
              }
            }
            case _ => None
          }
        }

        import org.jetbrains.plugins.scala.caches.ScalaRecursionManager._

        //todo: find recursion on the types in more complex algorithm
        val coreTypeForTp = coreType(tp)
        doComputations(place, (tp: Object, searches: Seq[Object]) => {
            searches.find{
              case t: ScType if tp.isInstanceOf[ScType] =>
                if (Equivalence.equivInner(t, tp.asInstanceOf[ScType], new ScUndefinedSubstitutor(), falseUndef = false)._1) true
                else dominates(t, tp.asInstanceOf[ScType])
              case _ => false
            } == None
          }, coreTypeForTp, compute(), IMPLICIT_PARAM_TYPES_KEY) match {
          case Some(res) => res
          case None => None
        }
      }

      val applicable = super.candidatesS.map(forFilter).flatten
      //todo: remove it when you will be sure, that filtering according to implicit parameters works ok
      val filtered = applicable.filter {
        case (res: ScalaResolveResult, subst: ScSubstitutor) =>
          res.problems match {
            case Seq(WrongTypeParameterInferred) => false
            case _ => true
          }
      }
      val actuals =
        if (filtered.isEmpty) applicable
        else filtered
      new MostSpecificUtil(place, 1).mostSpecificForImplicitParameters(actuals) match {
        case Some(r) => HashSet(r)
        case _ => applicable.map(_._1)
      }
    }
  }

  private def abstractsToUpper(tp: ScType): ScType = {
    tp.recursiveUpdate {
      case ScAbstractType(_, _, upper) => (true, upper)
      case tp => (false, tp)
    }
  }

  @tailrec
  private def coreType(tp: ScType): ScType = {
    tp match {
      case ScCompoundType(comps, _, _, subst) => abstractsToUpper(ScCompoundType(comps, Seq.empty, Seq.empty, subst)).removeUndefines()
      case ScExistentialType(quant, wilds) => abstractsToUpper(ScExistentialType(quant.recursiveUpdate((tp: ScType) => {
          tp match {
            case ScTypeVariable(name) => wilds.find(_.name == name).map(w => (true, w.upperBound)).getOrElse((false, tp))
            case ScDesignatorType(element) => element match {
              case a: ScTypeAlias if a.getContext.isInstanceOf[ScExistentialClause] =>
                wilds.find(_.name == a.name).map(w => (true, w.upperBound)).getOrElse((false, tp))
              case _ => (false, tp)
            }
            case _ => (false, tp)
          }
        }), wilds)).removeUndefines()
      case _ =>
        Conformance.isAliasType(tp) match {
          case Some(AliasType(_, lower, upper)) => coreType(upper.getOrAny)
          case _ => abstractsToUpper(tp).removeUndefines()
        }
    }
  }

  private def dominates(t: ScType, u: ScType): Boolean = {
//    println(t, u, "T complexity: ", complexity(t), "U complexity: ", complexity(u), "t set: ", topLevelTypeConstructors(t),
//      "u set", topLevelTypeConstructors(u), "intersection: ", topLevelTypeConstructors(t).intersect(topLevelTypeConstructors(u)))
    complexity(t) > complexity(u) && topLevelTypeConstructors(t).intersect(topLevelTypeConstructors(u)).nonEmpty
  }

  private def topLevelTypeConstructors(tp: ScType): Set[ScType] = {
    tp match {
      case ScProjectionType(_, element, _) => Set(ScDesignatorType(element))
      case ScParameterizedType(designator, _) => Set(designator)
      case ScDesignatorType(v: ScValue) =>
        val valueType: ScType = v.getType(TypingContext.empty).getOrAny
        topLevelTypeConstructors(valueType)
      case ScCompoundType(comps, _, _, _) => comps.flatMap(topLevelTypeConstructors(_)).toSet
      case tp => Set(tp)
    }
  }

  private def complexity(tp: ScType): Int = {
    tp match {
      case ScProjectionType(proj, _, _) => 1 + complexity(proj)
      case ScParameterizedType(des, args) => 1 + args.foldLeft(0)(_ + complexity(_))
      case ScDesignatorType(v: ScValue) =>
        val valueType: ScType = v.getType(TypingContext.empty).getOrAny
        1 + complexity(valueType)
      case ScCompoundType(comps, _, _, _) => comps.foldLeft(0)(_ + complexity(_))
      case _ => 1
    }
  }
}

object ImplicitParametersCollector {
  val SEARCH_ITERATIONS = 10
}