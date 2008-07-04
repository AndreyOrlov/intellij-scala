package org.jetbrains.plugins.scala.lang.refactoring

import psi.api.base.patterns.ScReferencePattern
import psi.api.statements.ScVariable
import psi.api.expr.ScReferenceExpression
import psi.api.statements.ScValue
import psi.api.toplevel.typedef.ScMember
import psi.api.base.ScStableCodeReferenceElement
import psi.impl.ScalaPsiElementFactory
import psi.api.expr.ScMethodCall
import psi.api.expr.ScFinallyBlock
import psi.api.expr.ScDoStmt
import psi.api.expr.ScWhileStmt
import psi.api.expr.ScIfStmt
import psi.api.expr.ScForStatement
import psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScParenthesisedExpr
import com.intellij.codeInsight.PsiEquivalenceUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import java.util.Comparator
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.util.ReflectionCache
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTryBlock
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock
import com.intellij.psi.PsiElement
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.PsiFile
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.lexer._
import psi.api.toplevel.ScCodeBlock

/**
* User: Alexander Podkhalyuzin
* Date: 23.06.2008
*/

object ScalaRefactoringUtil {
  def trimSpacesAndComments(editor: Editor, file: PsiFile) {
    var start = editor.getSelectionModel.getSelectionStart
    var end = editor.getSelectionModel.getSelectionEnd
    while (file.findElementAt(start).isInstanceOf[PsiWhiteSpace] ||
            file.findElementAt(start).isInstanceOf[PsiComment] ||
            file.getText.charAt(start) == '\n' ||
            file.getText.charAt(start) == ' ') start = start + 1
    while (file.findElementAt(end - 1).isInstanceOf[PsiWhiteSpace] ||
            file.findElementAt(end - 1).isInstanceOf[PsiComment] ||
           file.getText.charAt(end - 1) == '\n' ||
           file.getText.charAt(end - 1) == ' ') end = end - 1
    editor.getSelectionModel.setSelection(start, end)
  }

  def getExprFrom(expr: ScExpression): ScExpression = {
    val e = unparExpr(expr)
    e match {
      case x: ScReferenceExpression => {
        x.resolve match {
          case _: ScReferencePattern => return e
          case _ =>
        }
      }
      case _ =>
    }
    e.getParent match {
      case x: ScMethodCall if x.args.exprs.size > 0 =>
        return ScalaPsiElementFactory.createExpressionFromText(e.getText + " _", e.getManager)
      case _ => return e
    }
  }

  def getExpression(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int): Option[ScExpression] = {
    val element1 = file.findElementAt(startOffset)
    val element2 = file.findElementAt(endOffset - 1)
    if (element1 == null || element2 == null) {
      return None
    }
    val common = PsiTreeUtil.findCommonParent(element1, element2)
    val element: ScExpression = if (common.isInstanceOf[ScExpression])
      common.asInstanceOf[ScExpression] else PsiTreeUtil.getParentOfType(common, classOf[ScExpression]);
    if (element == null || element.getTextRange.getStartOffset != startOffset) {
      return None
    }
    return Some(element)
  }

  def getEnclosingContainer(expr: ScExpression): PsiElement = {
    def get(parent: PsiElement): PsiElement = {
      parent match {
        case null =>
        case x: ScCodeBlock if x != expr =>
        case _: ScExpression => parent.getParent match {
          case _: ScFunctionDefinition |
                  _: ScForStatement |
                  _: ScIfStmt |
                  _: ScWhileStmt |
                  _: ScDoStmt |
                  _: ScFinallyBlock => 
          case x => return get(x)
        }
        case _ => return get(parent.getParent)
      }
      return parent
    }
    return get(expr)
  }
  def ensureFileWritable(project: Project, file: PsiFile): Boolean = {
    val virtualFile = file.getVirtualFile()
    val readonlyStatusHandler = ReadonlyStatusHandler.getInstance(project)
    val operationStatus = readonlyStatusHandler.ensureFilesWritable(Array(virtualFile))
    return !operationStatus.hasReadonlyFiles()
  }
  def getOccurrences(expr: ScExpression, enclosingContainer: PsiElement): Array[ScExpression] = {
    val occurrences: ArrayBuffer[ScExpression] = new ArrayBuffer[ScExpression]()
    if (enclosingContainer == expr) occurrences += enclosingContainer.asInstanceOf[ScExpression]
    else
      for (child <- enclosingContainer.getChildren) {
        if (PsiEquivalenceUtil.areElementsEquivalent(child, expr, comparator, false)) {
          child match {
            case x: ScExpression => {
              x.getParent match {
                case y: ScMethodCall if y.args.exprs.size == 0 => occurrences += y
                case _ => occurrences += child.asInstanceOf[ScExpression]
              }
            }
            case _ =>
          }
        } else {
          occurrences ++= getOccurrences(expr, child)
        }
      }
    return occurrences.toArray
  }
  def unparExpr(expr: ScExpression): ScExpression = {
    expr match {
      case x: ScParenthesisedExpr => {
        x.expr
      }
      case _ => expr
    }
  }
  private val comparator = new Comparator[PsiElement]() {
    def compare(element1: PsiElement, element2: PsiElement): Int = {
      if (element1 == element2) return 0
      if (element1.isInstanceOf[ScParameter] && element2.isInstanceOf[ScParameter]) {
        val name1 = element1.asInstanceOf[ScParameter].getName
        val name2 = element2.asInstanceOf[ScParameter].getName
        if (name1 != null && name2 != null) {
          return name1.compareTo(name2)
        }
      }
      return 1
    }
  }
}