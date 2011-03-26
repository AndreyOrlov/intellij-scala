package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:49:36
*/

trait ScFunctionDefinition extends ScFunction with ScControlFlowOwner {
  def body: Option[ScExpression]

  def parameters: Seq[ScParameter]

  def hasAssign: Boolean

  def assignment: Option[PsiElement]

  def removeAssignment()

  def getReturnUsages: Array[PsiElement]

  def isSecondaryConstructor: Boolean
}