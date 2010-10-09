package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import api.toplevel.ScTypedDefinition
import java.lang.String
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import psi.stubs.ScTemplateDefinitionStub
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import types.{ScDesignatorType, ScSubstitutor, ScType}
/**
 * @author Alexander Podkhalyuzin
 * Date: 20.02.2008
 */

class ScObjectImpl extends ScTypeDefinitionImpl with ScObject with ScTemplateDefinition {
  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScTemplateDefinitionStub) = {this (); setStub(stub); setNode(null)}

  override def toString: String = if (isPackageObject) "ScPackageObject" else "ScObject"

  override def getIconInner = if (isPackageObject) Icons.PACKAGE_OBJECT else Icons.OBJECT

  override def hasModifierProperty(name: String): Boolean = {
    if (name == "final") return true
    super[ScTypeDefinitionImpl].hasModifierProperty(name)
  }


  override def isPackageObject: Boolean = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScTemplateDefinitionStub].isPackageObject
    } else findChildByType(ScalaTokenTypes.kPACKAGE) != null
  }

  override def isCase = hasModifierProperty("case")

  override def getContainingClass() = null

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)
  }

  // TODO SCL-2386 Add synthetic apply method with same signature as case companion class primary constructor.
  //               Also need to resolve the same method even if the companion object is synthetic.
  // override def syntheticMembers(): scala.Seq[FakePsiMethod]
}
