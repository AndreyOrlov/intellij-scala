package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import api.statements.{ScVariableDefinition, ScVariable, ScVariableDeclaration}
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.util.io.StringRef
import impl.ScVariableStubImpl
import index.ScalaIndexKeys
import com.intellij.util.IncorrectOperationException

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.10.2008
 */

abstract class ScVariableElementType[Variable <: ScVariable](debugName: String)
extends ScStubElementType[ScVariableStub, ScVariable](debugName) {
  def createStubImpl[ParentPsi <: PsiElement](psi: ScVariable, parentStub: StubElement[ParentPsi]): ScVariableStub = {
    val isDecl = psi.isInstanceOf[ScVariableDeclaration]
    val typeText = psi.typeElement match {
      case Some(te) => te.getText
      case None => ""
    }
    val bodyText = if (!isDecl) {
      val expr = psi.asInstanceOf[ScVariableDefinition].expr
      if (expr == null) {
        throw new IncorrectOperationException("Expr is null for variable: " + psi.getText)
      }
      expr.getText
    } else ""
    val containerText = if (isDecl) psi.asInstanceOf[ScVariableDeclaration].getIdList.getText
      else psi.asInstanceOf[ScVariableDefinition].pList.getText
    new ScVariableStubImpl[ParentPsi](parentStub, this,
      (for (elem <- psi.declaredElements) yield elem.name).toArray,
      isDecl, typeText, bodyText, containerText, psi.getContainingClass == null)
  }

  def serialize(stub: ScVariableStub, dataStream: StubOutputStream) {
    dataStream.writeBoolean(stub.isDeclaration)
    val names = stub.getNames
    dataStream.writeInt(names.length)
    for (name <- names) dataStream.writeName(name)
    dataStream.writeName(stub.getTypeText)
    dataStream.writeName(stub.getBodyText)
    dataStream.writeName(stub.getBindingsContainerText)
    dataStream.writeBoolean(stub.isLocal)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScVariableStub = {
    val isDecl = dataStream.readBoolean
    val namesLength = dataStream.readInt
    val names = new Array[String](namesLength)
    for (i <- 0 to (namesLength - 1)) names(i) = StringRef.toString(dataStream.readName)
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    val typeText = StringRef.toString(dataStream.readName)
    val bodyText = StringRef.toString(dataStream.readName)
    val bindingsText = StringRef.toString(dataStream.readName)
    val isLocal = dataStream.readBoolean()
    new ScVariableStubImpl(parent, this, names, isDecl, typeText, bodyText, bindingsText, isLocal)
  }

  def indexStub(stub: ScVariableStub, sink: IndexSink) {
    val names = stub.getNames
    for (name <- names if name != null) {
      sink.occurrence(ScalaIndexKeys.VARIABLE_NAME_KEY, name)
    }
  }
}