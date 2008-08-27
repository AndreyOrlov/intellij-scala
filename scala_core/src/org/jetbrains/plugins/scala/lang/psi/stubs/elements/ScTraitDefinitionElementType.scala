package org.jetbrains.plugins.scala.lang.psi.stubs.elements
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import api.toplevel.typedef.ScTrait
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTraitImpl

/**
 * @author ilyas
 */

class ScTraitDefinitionElementType extends ScTypeDefinitionElementType[ScTrait]("trait definition") {

  def createElement(node: ASTNode): PsiElement = new ScTraitImpl(node)

  def createPsi(stub: ScTypeDefinitionStub) = ScTraitImpl(stub)
}
