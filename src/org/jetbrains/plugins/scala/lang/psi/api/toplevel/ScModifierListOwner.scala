package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.psi._
import com.intellij.util.ArrayFactory
import org.jetbrains.plugins.scala.lang.psi.api.base._
import parser.ScalaElementTypes
import psi.stubs.ScModifiersStub
import stubs.{StubElement, NamedStub}
/**
* @author ilyas
*/

trait ScModifierListOwner extends ScalaPsiElement with PsiModifierListOwner {

  override def getModifierList: ScModifierList = {
    this match {
      case st: ScalaStubBasedElementImpl[_] => {
        val stub: StubElement[_ <: PsiElement] = st.getStub
        if (stub != null) {
          val array = stub.getChildrenByType(ScalaElementTypes.MODIFIERS, JavaArrayFactoryUtil.ScModifierListFactory)
          if (array.length == 0) return null
          else return array.apply(0)
        }
      }
      case _ =>
    }
    findChildByClassScala(classOf[ScModifierList])
  }

  def hasModifierProperty(name: String): Boolean = {
    this match {
      case st: ScalaStubBasedElementImpl[_] =>  {
        val stub: StubElement[_ <: PsiElement] = st.getStub
        if (stub != null) {
          val mods: Array[ScModifierList] =
            stub.getChildrenByType(ScalaElementTypes.MODIFIERS, JavaArrayFactoryUtil.ScModifierListFactory)
          if (mods.length > 0) return mods(0).hasModifierProperty(name: String)
          return false
        }
      }
      case _ =>
    }
    if (getModifierList != null)
      getModifierList.hasModifierProperty(name: String)
    else false
  }

  def setModifierProperty(name: String, value: Boolean) {
    getModifierList.setModifierProperty(name, value)
  }
}