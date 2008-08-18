package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import _root_.org.jetbrains.plugins.scala.lang.psi.types.{ScTupleType, Nothing}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScTuplePattern extends ScPattern {
  def argList = findChild(classOf[ScPatternArgumentList])

  override def calcType = argList match {
    case Some(l) => new ScTupleType(l.patterns.map {_.calcType})
    case None => Nothing
  }
}