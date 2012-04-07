package org.jetbrains.plugins.scala
package codeInsight
package intention
package matcher

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import lang.psi.types.result.TypingContext
import collection.Seq
import java.lang.String
import lang.psi.types.{ScSubstitutor, ScType}
import lang.psi.ScalaPsiUtil
import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScObject, ScClass}
import lang.psi.api.base.patterns.{ScPattern, ScCaseClause}
import lang.psi.api.expr.{ScExpression, ScMatchStmt}
import lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi._
import lang.psi.api.base.ScReferenceElement
import extensions._

final class CreateCaseClausesIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = "Generate case clauses"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    findSurroundingMatch(element) match {
      case Some((_, scrutineeType)) =>
        setText(getFamilyName + " for variants of " + scrutineeType)
        true
      case None =>
        false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    findSurroundingMatch(element) match {
      case Some((action, _)) =>
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        if (!CodeInsightUtilBase.prepareFileForWrite(element.getContainingFile)) return
        IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()
        action(project, editor, element)
      case None =>
    }
  }


  private def addMatchClausesForSealedClass(matchStmt: ScMatchStmt, expr: ScExpression, cls: ScClass)(project: Project, editor: Editor, element: PsiElement) {
    val inheritors = inheritorsOf(cls)
    val (caseClauseTexts, bindTos) = inheritors.map(caseClauseText).unzip
    val newMatchStmt = ScalaPsiElementFactory.createMatch(expr.getText, caseClauseTexts, element.getManager)
    matchStmt.replace(newMatchStmt)
    bindReferences(newMatchStmt, bindTos)
  }

  private def addMatchClausesForEnum(matchStmt: ScMatchStmt, expr: ScExpression, cls: PsiClass)(project: Project, editor: Editor, element: PsiElement) {
    val enumConsts: Array[PsiEnumConstant] = cls.getFields.collect {
      case enumConstant: PsiEnumConstant => enumConstant
    }
    val caseClauseTexts = enumConsts.map(ec => "case %s.%s =>".format(cls.name, ec.name))
    val newMatchStmt = ScalaPsiElementFactory.createMatch(expr.getText, caseClauseTexts, element.getManager)
    matchStmt.replace(newMatchStmt)
    bindReferences(newMatchStmt, (_ => cls))
  }

  private def bindReferences(newMatchStmt: ScMatchStmt, bindTargets: Int => PsiNamedElement) {
    for {
      (caseClause, i) <- newMatchStmt.caseClauses.zipWithIndex
    } {
      val bindTo = bindTargets(i)
      bindReference(caseClause, bindTo)
    }
  }

  private def bindReference(caseClause: ScCaseClause, bindTo: PsiNamedElement) {
    val pattern: ScPattern = caseClause.pattern.get
    val ref = pattern.depthFirst.collect {
      case x: ScReferenceElement if x.refName == bindTo.name => x
    }.next()
    ref.bindToElement(bindTo)
  }

  private def inheritorsOf(cls: ScClass): Seq[ScTypeDefinition] = {
    val found: Array[ScTypeDefinition] = ClassInheritorsSearch.search(cls, cls.getResolveScope, false).toArray(PsiClass.EMPTY_ARRAY).collect {
      case x: ScTypeDefinition => x
    }
    found.sortBy(_.getNavigationElement.getTextRange.getStartOffset)
  }

  /**
   * @return (caseClauseText, elementToBind)
   */
  private def caseClauseText(td: ScTypeDefinition): (String, PsiNamedElement) = {
    val refText = td.name
    val (pattern, bindTo) = td match {
      case obj: ScObject => (refText, obj)
      case cls: ScClass if cls.isCase =>
        val companionObj = ScalaPsiUtil.getCompanionModule(cls).get
        val text = cls.constructor match {
          case Some(primaryConstructor) =>
            val parameters = primaryConstructor.effectiveFirstParameterSection
            val bindings = parameters.map(_.name).mkString("( ", ", ", ")")
            refText + bindings
          case None =>
            refText + "()"
        }
        (text, companionObj)
      case _ =>
        val text = "_ : " + refText
        (text, td)
    }
    val text = "case %s =>".format(pattern)
    (text, bindTo)
  }


  /**
   * @return (matchStmt, matchExpression, matchExpressionClass)
   */
  private def findSurroundingMatch(element: PsiElement): Option[((Project, Editor, PsiElement) => Unit, String)] = {
    element.getParent match {
      case x: ScMatchStmt if x.caseClauses.isEmpty =>
        val classType: Option[(PsiClass, ScSubstitutor)] = x.expr.flatMap(_.getType(TypingContext.empty).toOption).
                flatMap(t => ScType.extractClassType(t, Some(element.getProject)))

        classType match {
          case Some((cls: ScClass, subst)) if cls.hasModifierProperty("sealed") => Some(addMatchClausesForSealedClass(x, x.expr.get, cls), "Sealed Type")
          case Some((cls: PsiClass, subst)) if cls.isEnum => Some(addMatchClausesForEnum(x, x.expr.get, cls), "Java Enumeration")
          case _ => None
        }
      case _ => None
    }
  }
}