package org.jetbrains.plugins.scala
package annotator

import collection.mutable.HashSet
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi.util.PsiTreeUtil
import controlFlow.ControlFlowInspections
import highlighter.AnnotatorHighlighter
import importsTracker._
import lang.psi.api.expr._


import lang.psi.api.statements._
import lang.psi.api.statements.params.ScClassParameter
import lang.psi.api.toplevel.typedef._
import lang.psi.api.toplevel.templates.ScTemplateBody
import com.intellij.lang.annotation._

import lang.psi.api.toplevel.imports.usages.ImportUsed
import lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.resolve._
import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.annotator.intention._
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil
import patterns.{ScInfixPattern, ScBindingPattern}
import quickfix.ImplementMethodsQuickFix
import quickfix.modifiers.{RemoveModifierQuickFix, AddModifierQuickFix}
import modifiers.ModifierChecker
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner
import types.ScTypeElement
import scala.collection.Set
import scala.Some
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypingContext, TypeResult, Success}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import com.intellij.openapi.editor.markup.{EffectType, TextAttributes}
import java.awt.{Font, Color}
import org.jetbrains.plugins.scala.lang.psi.{PresentationUtil, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, Unit, FullSignature}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/**
 *    User: Alexander Podkhalyuzin
 *    Date: 23.06.2008
 */

class ScalaAnnotator extends Annotator with FunctionAnnotator with ScopeAnnotator with ReferenceAnnotator
        with ControlFlowInspections with DumbAware {
  override def annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element.isInstanceOf[ScExpression]) {
      checkExpressionType(element.asInstanceOf[ScExpression], holder)
      checkExpressionImplicitParameters(element.asInstanceOf[ScExpression], holder)
    }

    if (element.isInstanceOf[ScTypeElement]) {
      checkTypeElementForm(element.asInstanceOf[ScTypeElement], holder)
    }

    if (element.isInstanceOf[ScAnnotation]) {
      checkAnnotationType(element.asInstanceOf[ScAnnotation], holder)
    }

    if (element.isInstanceOf[ScForStatement]) {
      checkForStmtUsedTypes(element.asInstanceOf[ScForStatement], holder)
    }

    val advancedHighlighting = isAdvancedHighlightingEnabled(element)
    annotateScope(element, holder)

    element match {
      case f: ScFunctionDefinition => {
        f.getContainingFile match {
          case file: ScalaFile if(file.isCompiled) => // don't annotate compiled files
          case _ => annotateFunction(f, holder, advancedHighlighting)  
        }
      }
      
      case x: ScFunction if x.getParent.isInstanceOf[ScTemplateBody] => {
        //todo: unhandled case abstract override
        //checkOverrideMethods(x, holder)
      }
      case x: ScTemplateDefinition => {
        //todo: checkImplementedMethods(x, holder)
      }
      case ref: ScReferenceElement => {
        if(advancedHighlighting) annotateReference(ref, holder)
        ref.qualifier match {
          case None => checkNotQualifiedReferenceElement(ref, holder)
          case Some(_) => checkQualifiedReferenceElement(ref, holder)
        }
      }
      case impExpr: ScImportExpr => {
        checkImportExpr(impExpr, holder)
      }
      case ret: ScReturnStmt => {
        checkExplicitTypeForReturnStatement(ret, holder)
      }
      case ml: ScModifierList => {
        ModifierChecker.checkModifiers(ml, holder)
      }
      case sTypeParam: ScTypeBoundsOwner => {
        checkTypeParamBounds(sTypeParam, holder)
      }
      case _ => AnnotatorHighlighter.highlightElement(element, holder)
    }

    super[ControlFlowInspections].annotate(element, holder)
  }

  private def settings(element: PsiElement): ScalaCodeStyleSettings = {
    CodeStyleSettingsManager.getSettings(element.getProject)
            .getCustomSettings(classOf[ScalaCodeStyleSettings])
  }

  def isAdvancedHighlightingEnabled(element: PsiElement) = {
    settings(element).ENABLE_ERROR_HIGHLIGHTING
  }
  
  private def checkTypeParamBounds(sTypeParam: ScTypeBoundsOwner, holder: AnnotationHolder) = {}

  private def checkNotQualifiedReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {

    def getFix: Seq[IntentionAction] = {
      val facade = JavaPsiFacade.getInstance(refElement.getProject)
      val classes = ScalaImportClassFix.getClasses(refElement, refElement.getProject)
      if (classes.length == 0) return Seq.empty
      return Seq[IntentionAction](new ScalaImportClassFix(classes, refElement))
    }

    val resolve: Array[ResolveResult] = refElement.multiResolve(false)
    def processError(countError: Boolean, fixes: => Seq[IntentionAction]) = {
      //todo remove when resolve of unqualified expression will be fully implemented
      if (refElement.getManager.isInProject(refElement) && resolve.length == 0 &&
              (fixes.length > 0 || countError)) {
        val error = ScalaBundle.message("cannot.resolve", refElement.refName)
        val annotation = holder.createErrorAnnotation(refElement.nameId, error)
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        registerAddImportFix(refElement, annotation, fixes: _*)
      }
    }

    if (resolve.length != 1) {
      refElement match {
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScPrefixExpr] &&
                e.getParent.asInstanceOf[ScPrefixExpr].operation == e => //todo: this is hide !(Not Boolean)
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScInfixExpr] &&
                e.getParent.asInstanceOf[ScInfixExpr].operation == e => //todo: this is hide A op B
        case e: ScReferenceExpression => processError(false, getFix)
        case e: ScStableCodeReferenceElement if e.getParent.isInstanceOf[ScInfixPattern] &&
                e.getParent.asInstanceOf[ScInfixPattern].refernece == e => //todo: this is hide A op B in patterns
        case _ => refElement.getParent match {
          case s: ScImportSelector if resolve.length > 0 =>
          case _ => processError(true, getFix)
        }
      }
    }
    else {
      AnnotatorHighlighter.highlightReferenceElement(refElement, holder)
    }
    for (result <- resolve if result.isInstanceOf[ScalaResolveResult];
         scalaResult = result.asInstanceOf[ScalaResolveResult]) {
      registerUsedImports(refElement, scalaResult)
    }
    checkAccessForReference(resolve, refElement, holder)
    checkForwardReference(resolve, refElement, holder)

    if (settings(refElement).SHOW_IMPLICIT_CONVERSIONS && resolve.length == 1) {
      val resolveResult = resolve(0).asInstanceOf[ScalaResolveResult]
      refElement match {
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScPrefixExpr] &&
                e.getParent.asInstanceOf[ScPrefixExpr].operation == e => {
          resolveResult.implicitFunction match {
            case Some(fun) => {
              val pref = e.getParent.asInstanceOf[ScPrefixExpr]
              val expr = pref.operand
              highlightImplicitMethod(expr, resolveResult, refElement, fun, holder)
            }
            case _ =>
          }
        }
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScInfixExpr] &&
                e.getParent.asInstanceOf[ScInfixExpr].operation == e => {
          resolveResult.implicitFunction match {
            case Some(fun) => {
              val inf = e.getParent.asInstanceOf[ScInfixExpr]
              val expr = if (inf.isLeftAssoc) inf.rOp else inf.lOp
              highlightImplicitMethod(expr, resolveResult, refElement, fun, holder)
            }
            case _ =>
          }
        }
        case _ =>
      }
    }

    if (isAdvancedHighlightingEnabled(refElement) && resolve.length != 1) {
      refElement.getParent match {
        case s: ScImportSelector if resolve.length > 0 => return
        case _ =>
      }
      val error = ScalaBundle.message("cannot.resolve", refElement.refName)
      val annotation = holder.createErrorAnnotation(refElement.nameId, error)
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
    }
  }

  private def highlightImplicitMethod(expr: ScExpression, resolveResult: ScalaResolveResult, refElement: ScReferenceElement,
                              fun: ScFunctionDefinition, holder: AnnotationHolder): Unit = {
    val exprText = expr.getText
    import org.jetbrains.plugins.scala.lang.psi.types.Any
    val typeFrom = expr.getType(TypingContext.empty).getOrElse(Any)
    val typeTo = resolveResult.implicitType match {case Some(tp) => tp case _ => Any}
    val range = refElement.nameId.getTextRange
    showImplicitUsageAnnotation(exprText, typeFrom, typeTo, fun, range, holder,
      EffectType.LINE_UNDERSCORE, Color.LIGHT_GRAY)
  }

  private def checkQualifiedReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {
    AnnotatorHighlighter.highlightReferenceElement(refElement, holder)
    var resolve: Array[ResolveResult] = null
    resolve = refElement.multiResolve(false)
    for (result <- resolve if result.isInstanceOf[ScalaResolveResult];
         scalaResult = result.asInstanceOf[ScalaResolveResult]) {
      registerUsedImports(refElement, scalaResult)
    }
    checkAccessForReference(resolve, refElement, holder)
    if (refElement.isInstanceOf[ScExpression] &&
            settings(refElement).SHOW_IMPLICIT_CONVERSIONS && resolve.length == 1) {
      val resolveResult = resolve(0).asInstanceOf[ScalaResolveResult]
      resolveResult.implicitFunction match {
        case Some(fun) => {
          val qualifier = refElement.qualifier match {case Some(qual) => qual}
          val expr = qualifier.asInstanceOf[ScExpression]
          highlightImplicitMethod(expr, resolveResult, refElement, fun, holder)
        }
        case _ =>
      }
    }
    if (isAdvancedHighlightingEnabled(refElement) && resolve.length != 1) {
      refElement.getParent match {
        case _: ScImportSelector | _: ScImportExpr if resolve.length > 0 => return 
        case _ =>
      }
      val error = ScalaBundle.message("cannot.resolve", refElement.refName)
      val annotation = holder.createErrorAnnotation(refElement.nameId, error)
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
    }
  }

  private def checkForwardReference(resolve: Array[ResolveResult], refElement: ScReferenceElement, holder: AnnotationHolder) {
    //todo: add check if it's legal to use forward reference
  }

  private def checkAccessForReference(resolve: Array[ResolveResult], refElement: ScReferenceElement, holder: AnnotationHolder) {
    if (resolve.length != 1) return
    resolve.apply(0) match {
      case res: ScalaResolveResult => {
        val memb: PsiMember = res.getElement match {
          case member: PsiMember => member
          case bind: ScBindingPattern => {
            ScalaPsiUtil.nameContext(bind) match {
              case member: PsiMember => member
              case _ => return
            }
          }
          case _ => return
        }
        if (!ResolveUtils.isAccessible(memb, refElement)) {
          val error = ScalaBundle.message("element.is.not.accessible", refElement.refName)
          val annotation = holder.createErrorAnnotation(refElement.nameId, error)
          annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
          //todo: fixes for changing access
        }
      }
      case _ =>
    }
  }

  private def registerAddImportFix(refElement: ScReferenceElement, annotation: Annotation, actions: IntentionAction*) {
    for (action <- actions) {
      annotation.registerFix(action)
    }
  }

  private def registerUsedImports(element: PsiElement, result: ScalaResolveResult) {
    ImportTracker.getInstance(element.getProject).
            registerUsedImports(element.getContainingFile.asInstanceOf[ScalaFile], result.importsUsed)
  }

  private def checkImplementedMethods(clazz: ScTemplateDefinition, holder: AnnotationHolder) {
    clazz match {
      case _: ScTrait => return
      case _ =>
    }
    if (clazz.hasModifierProperty("abstract")) return
    if (ScalaOIUtil.getMembersToImplement(clazz).length > 0) {
      val error = clazz match {
        case _: ScClass => ScalaBundle.message("class.must.declared.abstract", clazz.getName)
        case _: ScObject => ScalaBundle.message("object.must.implement", clazz.getName)
        case _: ScNewTemplateDefinition => ScalaBundle.message("anonymous.class.must.declared.abstract")
      }
      val start = clazz.getTextRange.getStartOffset
      val eb = clazz.extendsBlock
      var end = eb.templateBody match {
        case Some(x) => {
          val shifted = eb.findElementAt(x.getStartOffsetInParent - 1) match {case w: PsiWhiteSpace => w case _ => x}
          shifted.getTextRange.getStartOffset
        }
        case None => eb.getTextRange.getEndOffset
      }

      val annotation: Annotation = holder.createErrorAnnotation(new TextRange(start, end), error)
      annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      annotation.registerFix(new ImplementMethodsQuickFix(clazz))
    }
  }

  private def checkOverrideMethods(method: ScFunction, holder: AnnotationHolder) {
    val signatures = method.superSignatures
    if (signatures.length == 0) {
      if (method.hasModifierProperty("override")) {
        val annotation: Annotation = holder.createErrorAnnotation(method.nameId.getTextRange,
          ScalaBundle.message("method.overrides.nothing", method.getName))
        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        annotation.registerFix(new RemoveModifierQuickFix(method, "override"))
      }
    } else {
      if (!method.hasModifierProperty("override") && !method.isInstanceOf[ScFunctionDeclaration]) {
        def isConcrete(signature: FullSignature): Boolean = if (signature.element.isInstanceOf[PsiNamedElement])
          ScalaPsiUtil.nameContext(signature.element.asInstanceOf[PsiNamedElement]) match {
            case _: ScFunctionDefinition => true
            case method: PsiMethod if !method.hasModifierProperty(PsiModifier.ABSTRACT) && !method.isConstructor => true
            case _: ScPatternDefinition => true
            case _: ScVariableDeclaration => true
            case _: ScClassParameter => true
            case _ => false
          } else false
        def isConcretes: Boolean = {
          for (signature <- signatures if isConcrete(signature)) return true
          return false
        }
        if (isConcretes) {
          val annotation: Annotation = holder.createErrorAnnotation(method.nameId.getTextRange,
            ScalaBundle.message("method.needs.override.modifier", method.getName))
          annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          annotation.registerFix(new AddModifierQuickFix(method, "override"))
        }
      }
    }
  }

  private def showImplicitUsageAnnotation(exprText: String, typeFrom: ScType, typeTo: ScType, fun: ScFunctionDefinition,
                                          range: TextRange, holder: AnnotationHolder, effectType: EffectType,
                                          color: Color): Unit = {
    val tooltip = ScalaBundle.message("implicit.usage.tooltip", exprText,
      ScType.presentableText(typeFrom),
      ScType.presentableText(typeTo),
      PresentationUtil.presentationString(fun))
    val message = ScalaBundle.message("implicit.usage.message", exprText,
      ScType.presentableText(typeFrom),
      ScType.presentableText(typeTo),
      PresentationUtil.presentationString(fun))
    val annotation: Annotation = holder.createInfoAnnotation(range
      /*new TextRange(expr.getTextRange.getEndOffset - 1, expr.getTextRange.getEndOffset)*/ , message)
    annotation.setEnforcedTextAttributes(new TextAttributes(null, null, color,
      effectType, Font.PLAIN))
    annotation.setAfterEndOfLine(false)
    annotation.setTooltip(tooltip)
  }

  private def checkExpressionType(expr: ScExpression, holder: AnnotationHolder) {
    expr match {
      case m: ScMatchStmt =>
      case bl: ScBlock if bl.lastStatement != None =>
      case i: ScIfStmt if i.elseBranch != None =>
      case fun: ScFunctionExpr =>
      case tr: ScTryStmt =>
      case _ => {
        if (expr.getParent.isInstanceOf[ScArgumentExprList]) return
        val tp = expr.expectedType match {
          case Some(tp: ScType) if tp equiv Unit => //do nothing
          case Some(tp: ScType) => {
            import org.jetbrains.plugins.scala.lang.psi.types._
            val expectedType = Success(tp, None)
            val ExpressionTypeResult(exprType, importUsed, implicitFunction) = expr.getTypeAfterImplicitConversion()
            if (settings(expr).SHOW_IMPLICIT_CONVERSIONS) {
              implicitFunction match {
                case Some(fun) => {
                  val typeFrom = expr.getType(TypingContext.empty).getOrElse(Any)
                  val typeTo = exprType.getOrElse(Any)
                  val exprText = expr.getText
                  val range = expr.getTextRange
                  //todo:
                  //showImplicitUsageAnnotation(exprText, typeFrom, typeTo, fun, range, holder,
                  // EffectType.LINE_UNDERSCORE, Color.LIGHT_GRAY)
                }
                case None => //do nothing
              }
            }
            val conformance = ScalaAnnotator.smartCheckConformance(expectedType, exprType)
            if (!conformance) {
              val error = ScalaBundle.message("expr.type.does.not.conform.expected.type",
                ScType.presentableText(exprType.getOrElse(Nothing)), ScType.presentableText(expectedType.get))
              val annotation: Annotation = holder.createErrorAnnotation(expr, error)
              annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            } else ImportTracker.getInstance(expr.getProject).
                    registerUsedImports(expr.getContainingFile.asInstanceOf[ScalaFile], importUsed)
          }
          case _ => //do nothing
        }
      }
    }
  }

  private def checkExpressionImplicitParameters(expr: ScExpression, holder: AnnotationHolder) {
    expr.findImplicitParameters match {
      case Some(seq) => {
        for (resolveResult <- seq) {
          if (resolveResult != null) {
            ImportTracker.getInstance(expr.getProject).registerUsedImports(
              expr.getContainingFile.asInstanceOf[ScalaFile], resolveResult.importsUsed)
          }
        }
      }
      case _ =>
    }
  }

  private def checkExplicitTypeForReturnStatement(ret: ScReturnStmt, holder: AnnotationHolder) {
    var fun: ScFunction = PsiTreeUtil.getParentOfType(ret, classOf[ScFunction])
    fun match {
      case null => {
        val error = ScalaBundle.message("return.outside.method.definition")
        val annotation: Annotation = holder.createErrorAnnotation(ret.returnKeyword, error)
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      }
      case _ if !fun.hasAssign || fun.returnType.exists(_ == Unit) => {
        return
      }
      case _ => fun.returnTypeElement match {
        case Some(x: ScTypeElement) => {
          import org.jetbrains.plugins.scala.lang.psi.types._
          val funType = fun.returnType
          funType match {
            case Success(tp: ScType, _) if tp equiv Unit => return //nothing to check
            case _ =>
          }
          val ExpressionTypeResult(exprType, importUsed, implicitFunction) = ret.expr match {
            case Some(e: ScExpression) => e.getTypeAfterImplicitConversion()
            case None => ExpressionTypeResult(Success(Unit, None), Set.empty, None)
          }
          ImportTracker.getInstance(ret.getProject).registerUsedImports(ret.getContainingFile.asInstanceOf[ScalaFile], importUsed)
        }
        case _ =>
      }
    }
  }

  private def checkForStmtUsedTypes(f: ScForStatement, holder: AnnotationHolder) {

    ImportTracker.getInstance(f.getProject).registerUsedImports(f.getContainingFile.asInstanceOf[ScalaFile],
      ScalaPsiUtil.getExprImports(f))
  }

  private def checkImportExpr(impExpr: ScImportExpr, holder: AnnotationHolder) {
    if (impExpr.qualifier == null) {
      val annotation: Annotation = holder.createErrorAnnotation(impExpr.getTextRange,
          ScalaBundle.message("import.expr.should.be.qualified"))
      annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
    }
  }

  private def checkTypeElementForm(typeElement: ScTypeElement, holder: AnnotationHolder) {
    //todo: check bounds conformance for parameterized type
  }

  private def checkAnnotationType(annotation: ScAnnotation, holder: AnnotationHolder) {
    //todo: check annotation is inheritor for class scala.Annotation
  }
}

object ScalaAnnotator {
  val usedImportsKey: Key[HashSet[ImportUsed]] = Key.create("used.imports.key")

  /**
   * This method will return checked conformance if it's possible to check it.
   * In other way it will return true to avoid red code.
   * Check conformance in case l = r.
   */
  def smartCheckConformance(l: TypeResult[ScType], r: TypeResult[ScType]): Boolean = {
    /*for (leftType <- l; rightType <- r) {
      if (!Conformance.conforms(leftType, rightType)) {
        return false
      } else return true
    }*/
    return true
  }
}