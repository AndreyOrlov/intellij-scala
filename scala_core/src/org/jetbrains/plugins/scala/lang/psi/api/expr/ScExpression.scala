package org.jetbrains.plugins.scala.lang.psi.api.expr

import impl.ScalaPsiElementFactory
import com.intellij.psi.PsiInvalidElementAccessException
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, Nothing}

/** 
* @author Alexander Podkhalyuzin
* Date: 14.03.2008
*/

trait ScExpression extends ScalaPsiElement {
  def getType(): ScType = Nothing //todo

  def expectedType() : Option[ScType] = None //todo
  
  def replaceExpression(expr: ScExpression, removeParenthesis: Boolean): ScExpression = {
    val oldParent = getParent
    if (oldParent == null) throw new PsiInvalidElementAccessException(this)
    //todo: implement checking priority (when inline refactoring)
    if (removeParenthesis && oldParent.isInstanceOf[ScParenthesisedExpr]) {
      return oldParent.asInstanceOf[ScExpression].replaceExpression(expr, true)
    }
    val parentNode = oldParent.getNode
    val newNode = expr.copy.getNode
    parentNode.replaceChild(this.getNode, newNode)
    return newNode.getPsi.asInstanceOf[ScExpression]
  }
}