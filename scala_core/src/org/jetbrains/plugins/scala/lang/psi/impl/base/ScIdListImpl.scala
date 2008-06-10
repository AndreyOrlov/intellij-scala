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
import _root_.scala.collection.mutable._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScIdListImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScIdList{
  override def toString: String = "ListOfIdentifiers"
  def getIdentifiers: Array[PsiElement] = {
    val res = new ArrayBuffer[PsiElement]
    for (child <- getNode.getChildren(null) if child.getElementType == ScalaTokenTypes.tIDENTIFIER)
        res.append(child.getPsi.asInstanceOf[PsiElement])
    return res.toArray  //todo
  }
}