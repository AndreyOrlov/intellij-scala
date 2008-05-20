package org.jetbrains.plugins.scala.lang.psi.impl.statements

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
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import com.intellij.psi.scope.PsiScopeProcessor

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:56:07
*/

class ScVariableDefinitionImpl(node: ASTNode) extends ScMemberImpl(node) with ScVariableDefinition {

  override def toString: String = "ScVariableDefinition"
  override def getIcon(flags: Int) = Icons.VAR

  def bindings: Seq[ScBindingPattern] = {
    val plist = findChildByClass(classOf[ScPatternList])
    if (plist != null) plist.patterns.flatMap[ScBindingPattern]((p: ScPattern) => p.bindings) else Seq.empty
  }

  def ids = for (b <- bindings) yield b.nameId

  override def processDeclarations(processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement,
    place: PsiElement): Boolean = {
    import org.jetbrains.plugins.scala.lang.resolve._

    for (b <- bindings) if (!processor.execute(b, state)) return false
    true
  }
}