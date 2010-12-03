package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import api.base.types.ScTypeElement
import com.intellij.util.ArrayFactory
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import parser.ScalaElementTypes
import stubs.{ScValueStub, ScVariableStub}
import org.jetbrains.plugins.scala.lang.psi.types.Any
import psi.types.result.TypingContext
import com.intellij.psi.PsiElementVisitor
import api.ScalaElementVisitor

/**
 * @author Alexander Podkhalyuzin
 */

class ScVariableDefinitionImpl extends ScalaStubBasedElementImpl[ScVariable] with ScVariableDefinition {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScVariableStub) = {this (); setStub(stub); setNode(null)}

  override def toString: String = "ScVariableDefinition"

  def bindings: Seq[ScBindingPattern] = {
    val plist = this.pList
    if (plist != null) plist.patterns.flatMap((p: ScPattern) => p.bindings) else Seq.empty
  }

  def getType(ctx: TypingContext) = typeElement match {
    case Some(te) => te.getType(ctx)
    case None => expr.getType(TypingContext.empty)
  }

  def typeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScVariableStub].getTypeElement
    }
    else findChild(classOf[ScTypeElement])
  }

  def pList: ScPatternList = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(ScalaElementTypes.PATTERN_LIST, new ArrayFactory[ScPatternList] {
        def create(count: Int): Array[ScPatternList] = new Array[ScPatternList](count)
      }).apply(0)
    } else findChildByClass(classOf[ScPatternList])
  }
}