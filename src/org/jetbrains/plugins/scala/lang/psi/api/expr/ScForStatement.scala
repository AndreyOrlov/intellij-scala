package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScForStatement extends ScExpression {
  def getDesugarisedExpr: Option[ScExpression]
  def isYield: Boolean
  def enumerators: Option[ScEnumerators]
  def patterns: Seq[ScPattern]
  def body: Option[ScExpression] = findChild(classOf[ScExpression])
  override def accept(visitor: ScalaElementVisitor) = visitor.visitForExpression(this)
}