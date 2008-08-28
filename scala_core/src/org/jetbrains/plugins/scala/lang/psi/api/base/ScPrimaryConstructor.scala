package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScPrimaryConstructor extends ScMember {
  /**
   *  Returns does constructor have annotation
   *
   *  @return has annotation
   */
  def hasAnnotation: Boolean

  /**
   *  Returns does constructor have access modifier
   *
   *  @return has access modifier
   */
  def hasModifier: Boolean
  def getClassNameText: String

  def parameterList = findChildByClass(classOf[ScParameters])
  def parameters = parameterList.params
}