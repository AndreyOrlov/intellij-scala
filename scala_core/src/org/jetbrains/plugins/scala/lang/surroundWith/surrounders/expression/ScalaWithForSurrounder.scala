package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

/**
* @author Alexander.Podkhalyuzin
* Date: 28.04.2008
*/

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import lang.psi.api.expr._

class ScalaWithForSurrounder extends ScalaExpressionSurrounder {

  override def getExpressionTemplateAsString(expr: ASTNode) =
    if (!isNeedBraces(expr)) "for (val a <- as) yield " + expr.getText
    else "(" + "for (val a <- as) yield " + expr.getText + ")"

  override def getTemplateDescription = "for"

  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    return "for (a <- as) {" + super.getTemplateAsString(elements) + "}"
  }

  override def getSurroundSelectionRange(withForNode: ASTNode): TextRange = {
    val forStmt = withForNode.getPsi.asInstanceOf[ScForStatement]

    val enums = (forStmt.asInstanceOf[ScForStatement].enumerators: @unchecked) match {
      case Some(x) => x.getNode
    }

    val offset = enums.getTextRange.getStartOffset
    forStmt.getNode.removeChild(enums)

    new TextRange(offset, offset);
  }
}