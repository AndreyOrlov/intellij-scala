package org.jetbrains.plugins.scala.lang.psi.impl.base

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

import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.impl.statements._

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScPrimaryConstructorImpl(node: ASTNode) extends ScMemberImpl(node) with ScPrimaryConstructor {
  override def hasAnnotation: Boolean = {
    return !(node.getFirstChildNode.getFirstChildNode == null)
  }

  override def hasModifier: Boolean = {
    return node.getFirstChildNode.getTreeNext.getElementType != ScalaElementTypes.CLASS_PARAM_CLAUSES
  }

  def getClassNameText: String = {
    return node.getTreeParent.getPsi.asInstanceOf[ScTypeDefinition].getName
  }

  def paramClauses: ScClassParamClauses = {
    findChildByClass(classOf[ScClassParamClauses])
  }

  def typeParametersClause: ScTypeParamClause = {
    node.getTreeParent.getPsi.asInstanceOf[ScTypeDefinition].typeParametersClause
  }
  override def toString: String = "PrimaryConstructor"
}