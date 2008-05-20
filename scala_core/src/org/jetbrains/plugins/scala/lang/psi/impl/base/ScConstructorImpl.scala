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
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.impl.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScConstructorImpl(node: ASTNode) extends ScFunctionImpl(node) with ScConstructor {

  override def toString: String = "Constructor"

  def getFunctionsAndTypeDefs = Seq.empty

  override def getId: PsiElement = {
    val name = super.getId
    if (name == null) {
      if (node.getTreeParent.getElementType == ScalaElementTypes.TEMPLATE_BODY) {
        val parent = node.getTreeParent.getTreeParent.getTreeParent
        if (!parent.getPsi.isInstanceOf[ScTypeDefinition]) return null
        return parent.getPsi.asInstanceOf[ScTypeDefinition].getNameIdentifierScala
      } else null
    } else name
  }

  override def getName = "this"

  override def isConstructor = true

}