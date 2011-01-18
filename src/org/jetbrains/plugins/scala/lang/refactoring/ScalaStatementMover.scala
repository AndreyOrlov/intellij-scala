package org.jetbrains.plugins.scala
package lang.refactoring

import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover.MoveInfo
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.editor.Editor
import com.intellij.codeInsight.editorActions.moveUpDown.{LineRange, LineMover}
import com.intellij.psi.{PsiWhiteSpace, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import lang.psi.api.toplevel.typedef.ScMember
import lang.psi.ScalaPsiElement
import lang.psi.api.base.patterns.ScCaseClause
import com.intellij.lang.ASTNode

/**
 * Pavel Fatin
 */

class ScalaStatementMover extends LineMover {
  override def checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean = {
    if(!super.checkAvailable(editor, file, info, down)) return false
    if(editor.getSelectionModel.hasSelection) return false
    if(!file.isInstanceOf[ScalaFile]) return false

    def aim[T <: ScalaPsiElement](cl: Class[T]): Option[(LineRange, LineRange)] = {
      findElementAt(cl, editor, file, info.toMove.startLine).flatMap { source =>
        val siblings = if(down) source.nextSiblings else source.prevSiblings
        val r = siblings.findByType(cl).map(target => (rangeOf(source, editor), rangeOf(target, editor)))
        r
      }
    }

    val destination = aim(classOf[ScCaseClause]).orElse(aim(classOf[ScMember]))

    destination.foreach { it =>
      info.toMove = it._1
      info.toMove2 = it._2
    }

    destination.isDefined
  }

  private def rangeOf(e: PsiElement, editor: Editor) = {
    val memberRange = e.getTextRange
    new LineRange(editor.offsetToLogicalPosition(memberRange.getStartOffset).line,
      editor.offsetToLogicalPosition(memberRange.getEndOffset).line + 1)
  }

  private def findElementAt[T <: ScalaPsiElement](cl: Class[T], editor: Editor, file: PsiFile, line: Int): Option[T] = {
    val edges = edgeLeafsOf(line, editor, file)

    val left = edges._1.flatMap(PsiTreeUtil.getParentOfType(_, cl, false).toOption)
    val right = edges._2.flatMap(PsiTreeUtil.getParentOfType(_, cl, false).toOption)

    left.zip(right)
            .filter(p => p._1 == p._2)
            .map(_._1)
            .filter(it => editor.offsetToLogicalPosition(it.getTextOffset).line == line)
            .headOption
  }

  private def edgeLeafsOf(line: Int, editor: Editor, file: PsiFile): (Option[PsiElement], Option[PsiElement]) = {
    val document = editor.getDocument

    val start = document.getLineStartOffset(line)
    val end = start.max(document.getLineEndOffset(line) - 1)

    val span = start.to(end)

    def isWhitespace(node: ASTNode) = node.getPsi.isInstanceOf[PsiWhiteSpace]

    def firstLeafOf(seq: Seq[Int]) = seq.view.flatMap(file.getNode.findLeafElementAt(_).toOption.toSeq)
            .filter(!isWhitespace(_)).map(_.getPsi).headOption

    (firstLeafOf(span), firstLeafOf(span.reverse))
  }
}