package org.jetbrains.plugins.scala.lang.psi.api.statements

import toplevel.typedef._
import base.ScIdList
import base.types.ScTypeElement

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:44:29
*/

trait ScValueDeclaration extends ScValue with ScDeclaration {
  def getIdList = findChildByClass(classOf[ScIdList])
}