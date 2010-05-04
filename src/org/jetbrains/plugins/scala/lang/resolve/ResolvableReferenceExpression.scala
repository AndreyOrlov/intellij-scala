package org.jetbrains.plugins.scala
package lang
package resolve

import _root_.com.intellij.openapi.progress.ProgressManager
import _root_.com.intellij.psi.impl.PsiManagerEx
import processor.{ConstructorResolveProcessor, MethodResolveProcessor, CompletionProcessor, BaseProcessor}
import psi.implicits.ScImplicitlyConvertible
import _root_.com.intellij.psi.{PsiClass, ResolveState, ResolveResult, PsiElement}
import psi.api.toplevel.imports.usages.ImportUsed
import psi.types.result.TypingContext
import psi.types.{ScSubstitutor, ScType}
import psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import psi.types.Compatibility.Expression
import psi.api.base.types.{ScTypeElement, ScParameterizedTypeElement}
import psi.api.base.{ScPrimaryConstructor, ScConstructor, ScReferenceElement}

trait ResolvableReferenceExpression extends ScReferenceExpression {
  private object Resolver extends ReferenceExpressionResolver(this)
  
  def multiResolve(incomplete: Boolean) = {
    //val now = System.currentTimeMillis
    val resolve = getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, Resolver, true, incomplete)
    /*val spent = System.currentTimeMillis - now
    if (spent > 8000) {
      println("spent: " + spent)
    }*/
    resolve
  }

  def isAssignmentOperator = {
    val context = getContext
    (context.isInstanceOf[ScInfixExpr] || context.isInstanceOf[ScMethodCall]) &&
            refName.endsWith("=") &&
            !(refName.startsWith("=") || Seq("!=", "<=", ">=").contains(refName) || refName.exists(_.isLetterOrDigit))
  }

  def isUnaryOperator = {
    getContext match {
      case pref: ScPrefixExpr if pref.operation == this => true
      case _ => false
    }
  }

  def rightAssoc = refName.endsWith(":")

  def doResolve(ref: ResolvableReferenceExpression, processor: BaseProcessor): Array[ResolveResult] = {
    ref.qualifier match {
      case None => resolveUnqalified(ref, processor)
      case Some(superQ : ScSuperReference) => ResolveUtils.processSuperReference(superQ, processor, this)
      case Some(q) => processTypes(q, processor)
    }
    processor.candidates
  }

  private def resolveUnqalified(ref: ResolvableReferenceExpression, processor: BaseProcessor) = {
    ref.getContext match {
      case inf: ScInfixExpr if ref == inf.operation => {
        val thisOp = if (ref.rightAssoc) inf.rOp else inf.lOp
        processTypes(thisOp, processor)
      }
      case postf: ScPostfixExpr if ref == postf.operation => processTypes(postf.operand, processor)
      case pref: ScPrefixExpr if ref == pref.operation => processTypes(pref.operand, processor)
      case _ => resolveUnqualifiedExpression(ref, processor)
    }
  }

  private def resolveUnqualifiedExpression(ref: ResolvableReferenceExpression, processor: BaseProcessor) = {
    def treeWalkUp(place: PsiElement, lastParent: PsiElement) {
      place match {
        case null =>
        case p => {
          if (!p.processDeclarations(processor,
            ResolveState.initial(),
            lastParent, ref)) return
          if (!processor.changedLevel) return
          treeWalkUp(place.getContext, place)
        }
      }
    }
    ref.getContext match {
      case assign: ScAssignStmt if assign.getLExpression == ref &&
              assign.getContext.isInstanceOf[ScArgumentExprList] => processAssignment(assign, ref, processor)
      case _ =>
    }
    treeWalkUp(ref, null)
  }

  private def processAssignment(assign: ScAssignStmt, ref: ResolvableReferenceExpression, processor: BaseProcessor) {
    assign.getContext match { //trying to resolve naming parameter
      case args: ScArgumentExprList => {
        val exprs = args.exprs
        args.callReference match {
          case Some(callReference) => processCallReference(args, callReference, ref, processor)
          case None => processConstructorReference(args, ref, processor)
        }
      }
    }
  }

  private def processConstructorReference(args: ScArgumentExprList, ref: ResolvableReferenceExpression,
                                          baseProcessor: BaseProcessor): Unit = {
    args.getContext match {
      case constr: ScConstructor => {
        val te: ScTypeElement = constr.typeElement
        val tp: ScType = te.getType(TypingContext.empty).getOrElse(return)
        val typeArgs: Seq[ScTypeElement] = te match {
          case p: ScParameterizedTypeElement => p.typeArgList.typeArgs
          case _ => Seq.empty[ScTypeElement]
        }
        ScType.extractClassType(tp) match {
          case Some((clazz, subst)) => {
            val processor: ConstructorResolveProcessor = new ConstructorResolveProcessor(constr,
              constr.arguments.toList.map(_.exprs.map(Expression(_))), typeArgs)
            val state = ResolveState.initial.put(ScSubstitutor.key, subst)
            for (constr <- clazz.getConstructors) {
              processor.execute(constr, state)
            }
            for (candidate <- processor.candidates) {
              candidate match {
                case ScalaResolveResult(fun: ScFunction, subst: ScSubstitutor) => {
                  fun.getParamByName(ref.refName, constr.arguments.indexOf(args)) match {
                    case Some(param) => baseProcessor.execute(param, ResolveState.initial.put(ScSubstitutor.key, subst))
                    case None =>
                  }
                }
                case ScalaResolveResult(constructor: ScPrimaryConstructor, _) => {
                  constructor.getParamByName(ref.refName, constr.arguments.indexOf(args)) match {
                    case Some(param) => baseProcessor.execute(param, ResolveState.initial.put(ScSubstitutor.key, subst))
                    case None =>
                  }
                }
                case _ =>
              }
            }
          }
          case _ =>
        }
      }
      case _ =>
    }
  }

  private def processCallReference(args: ScArgumentExprList, callReference: ScReferenceElement,
          ref: ResolvableReferenceExpression, processor: BaseProcessor) = {
    for (variant <- callReference.multiResolve(false)) {
      variant match {
        case ScalaResolveResult(fun: ScFunction, subst: ScSubstitutor) => {
          fun.getParamByName(ref.refName, args.invocationCount - 1) match {
            case Some(param) => processor.execute(param, ResolveState.initial.put(ScSubstitutor.key, subst))
            case None =>
          }
        }
        case _ =>
      }
    }
  }

  private def processTypes(e: ScExpression, processor: BaseProcessor) {
    ProgressManager.checkCanceled
    val result = e.getType(TypingContext.empty)
    if (result.isDefined) {
      processType(result.get, e, processor)
    }
  }

  private def processType(aType: ScType, e: ScExpression, processor: BaseProcessor) {
    processor.processType(aType, e, ResolveState.initial)

    val candidates = processor.candidates

    if (candidates.length == 0 || candidates.forall(!_.applicable) || (processor.isInstanceOf[CompletionProcessor] &&
            processor.asInstanceOf[CompletionProcessor].collectImplicits)) {
      collectImplicits(e, processor)
    }
  }

  private def collectImplicits(e: ScExpression, processor: BaseProcessor) {
    for (t <- e.getImplicitTypes) {
        ProgressManager.checkCanceled
        val importsUsed = e.getImportsForImplicit(t)
        var state = ResolveState.initial.put(ImportUsed.key, importsUsed)
        e.getClazzForType(t) match {
          case Some(cl: PsiClass) => state = state.put(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY, cl)
          case _ =>
        }
        processor.processType(t, e, state)
      }
  }
}