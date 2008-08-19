package org.jetbrains.plugins.scala.lang.psi.impl.statements.params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.psi._
import com.intellij.psi.search._

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScParametersImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScParameters {

  override def toString: String = "Parameters"

  def params: Seq[ScParameter] = clauses.flatMap((clause: ScParameterClause) => clause.parameters)

  def clauses: Seq[ScParameterClause] = findChildrenByClass(classOf[ScParameterClause])

  def getParameterIndex(p: PsiParameter) = params.indexOf(List(p))

  def getParametersCount = params.length

  override def getParameters = params.toArray
}