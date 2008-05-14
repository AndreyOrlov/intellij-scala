package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import _root_.scala.collection.mutable._

/**
* @author ilyas
*/

class ScPatternArgumentListImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPatternArgumentList{

  override def toString: String = "Pattern Argument List"

  def getPatterns: Seq[ScPattern] = findChildrenByClass(classOf[ScPattern])

}