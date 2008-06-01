package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:49:36
*/

trait ScFunctionDefinition extends ScFunction with ScTypeDefinitionOwner {

  def body: Option[ScExpression] = findChild(classOf[ScExpression])

  def parameters: Seq[ScParameter]
}