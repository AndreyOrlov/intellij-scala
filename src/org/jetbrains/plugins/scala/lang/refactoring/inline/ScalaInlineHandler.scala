package org.jetbrains.plugins.scala
package lang
package refactoring
package inline


import collection.mutable.ArrayBuffer
import com.intellij.lang.refactoring.InlineHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiReference, PsiElement}
import com.intellij.refactoring.util.{CommonRefactoringUtil, RefactoringMessageDialog}
import com.intellij.refactoring.HelpID
import com.intellij.psi.util.PsiTreeUtil
import java.lang.String
import lexer.ScalaTokenTypes
import psi.api.base.patterns.ScBindingPattern
import psi.api.expr.ScExpression
import psi.api.statements._
import psi.api.toplevel.templates.ScTemplateBody
import util.ScalaRefactoringUtil
import com.intellij.usageView.UsageInfo
import psi.ScalaPsiUtil
import psi.api.base.ScStableCodeReferenceElement
import collection.JavaConverters.iterableAsScalaIterableConverter
import com.intellij.lang.refactoring.InlineHandler.Settings

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.06.2009
 */

class ScalaInlineHandler extends InlineHandler {
  def removeDefinition(element: PsiElement, settings: InlineHandler.Settings) {
    element match {
      case rp: ScBindingPattern => {
        PsiTreeUtil.getParentOfType(rp, classOf[ScDeclaredElementsHolder]) match {
          case v: ScValue if !v.getParent.isInstanceOf[ScTemplateBody] && v.declaredElements.length == 1 => {
            val children = new ArrayBuffer[PsiElement]
            var psiElement = v.getNextSibling
            while (psiElement != null && (psiElement.getNode.getElementType == ScalaTokenTypes.tSEMICOLON || psiElement.getText.trim == "")) {
              children += psiElement
              psiElement = psiElement.getNextSibling
            }
            for (child <- children) {
              child.getParent.getNode.removeChild(child.getNode)
            }
            v.getParent.getNode.removeChild(v.getNode)
          }
          case v: ScVariable if !v.getParent.isInstanceOf[ScTemplateBody] && v.declaredElements.length == 1 => {
            val children = new ArrayBuffer[PsiElement]
            var psiElement = v.getNextSibling
            while (psiElement != null && (psiElement.getNode.getElementType == ScalaTokenTypes.tSEMICOLON || psiElement.getText.trim == "")) {
              children += psiElement
              psiElement = psiElement.getNextSibling
            }
            for (child <- children) {
              child.getParent.getNode.removeChild(child.getNode)
            }
            v.getParent.getNode.removeChild(v.getNode)
          }
          case _ => return
        }
      }
      case _ => return
    }
  }

  def createInliner(element: PsiElement, settings: InlineHandler.Settings): InlineHandler.Inliner = {
    val expr = ScalaRefactoringUtil.unparExpr(element match {
      case rp: ScBindingPattern => {
        PsiTreeUtil.getParentOfType(rp, classOf[ScDeclaredElementsHolder]) match {
          case v: ScPatternDefinition if !v.getParent.isInstanceOf[ScTemplateBody] && v.declaredElements.length == 1 => {
            v.expr
          }
          case v: ScVariableDefinition if !v.getParent.isInstanceOf[ScTemplateBody] && v.declaredElements.length == 1 => {
            v.expr
          }
          case _ => return null
        }
      }
      case _ => return null
    })
    new InlineHandler.Inliner {
      def inlineUsage(usage: UsageInfo, referenced: PsiElement) {
        val reference = usage.getReference
        reference match {
          case expression: ScExpression => {
            val ne = expression.replaceExpression(expr, true)
            val project = ne.getProject
            val  manager = FileEditorManager.getInstance(project)
            val editor = manager.getSelectedTextEditor
            ScalaRefactoringUtil.highlightOccurrences(project, Array[PsiElement](ne), editor)
            WindowManager.getInstance().getStatusBar(project).setInfo(ScalaBundle.message("press.escape.to.remove.the.highlighting"))
            CodeStyleManager.getInstance(project).reformatRange(ne.getContainingFile, ne.getTextRange.getStartOffset - 1,
              ne.getTextRange.getEndOffset + 1) //to prevent situations like this 2 ++2 (+2 was inlined)
          }
          case _ =>
        }
      }

      def getConflicts(reference: PsiReference, referenced: PsiElement): com.intellij.util.containers.MultiMap[PsiElement, String] =
        new com.intellij.util.containers.MultiMap[PsiElement, String]()
    }
  }

  def prepareInlineElement(element: PsiElement, editor: Editor, invokedOnReference: Boolean): InlineHandler.Settings = {
    def getSettingsForLocal(v: ScDeclaredElementsHolder): InlineHandler.Settings = {
      val bind = v.declaredElements.apply(0)
      val refs: java.util.Collection[PsiReference] = ReferencesSearch.search(bind).findAll
      val buffer: ArrayBuffer[PsiElement] = new ArrayBuffer[PsiElement]()
      val iterator = refs.iterator
      while (iterator.hasNext) {
        buffer += iterator.next.getElement
      }
      ScalaRefactoringUtil.highlightOccurrences(element.getProject, buffer.toArray, editor)
      if (refs.size == 0) {
        CommonRefactoringUtil.showErrorHint(element.getProject, editor, "Variable is never used.", "Scala Inline Variable", HelpID.INLINE_VARIABLE)
        return Settings.CANNOT_INLINE_SETTINGS
      }
      if (refs.asScala.exists(ref => ScalaPsiUtil.getParentOfType(ref.getElement, classOf[ScStableCodeReferenceElement]) != null)) {
        CommonRefactoringUtil.showErrorHint(element.getProject, editor, "Value is used in a stable reference and cannot be inlined", "Scala Inline Variable", HelpID.INLINE_VARIABLE)
        return Settings.CANNOT_INLINE_SETTINGS
      }
      val application = ApplicationManager.getApplication;
      if (!application.isUnitTestMode) {
        val question = "Inline local variable?"
        val dialog = new RefactoringMessageDialog(
          "Scala Inline Variable",
          question,
          HelpID.INLINE_VARIABLE,
          "OptionPane.questionIcon",
          true,
          element.getProject)
        dialog.show()
        if (!dialog.isOK) {
          WindowManager.getInstance().getStatusBar(element.getProject).setInfo(ScalaBundle.message("press.escape.to.remove.the.highlighting"))
          return null
        }
      }
      new InlineHandler.Settings {
        def isOnlyOneReferenceToInline: Boolean = false
      }
    }
    element match {
      case rp: ScBindingPattern => {
        PsiTreeUtil.getParentOfType(rp, classOf[ScDeclaredElementsHolder]) match {
          case v: ScValue if !v.getParent.isInstanceOf[ScTemplateBody] && v.declaredElements.length == 1 => {
            getSettingsForLocal(v)
          }
          /*case v: ScVariable if !v.getParent.isInstanceOf[ScTemplateBody] && v.declaredElements.length == 1 => {
            getSettingsForLocal(v)
          }*/
          case _ => null
        }
      }
      case _ => null
    }
  }
}
