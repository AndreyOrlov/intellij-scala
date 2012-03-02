package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import base.types.ScTypeElement
import caches.CachesUtil
import com.intellij.psi.util.{PsiModificationTracker, CachedValueProvider, CachedValue}
import com.intellij.psi.{PsiManager, PsiElement}
import stubs.ScTypeAliasStub
import toplevel.ScNamedElement
import types.result.{TypeResult, Failure, TypingContext}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScTypeAliasDefinition extends ScTypeAlias {
  def aliasedTypeElement = findChildByClassScala(classOf[ScTypeElement])

  def aliasedType(ctx: TypingContext): TypeResult[ScType] = {
    if (ctx.visited.contains(this)) {
      new Failure(ScalaBundle.message("circular.dependency.detected", name), Some(this)) {override def isCyclic = true}
    } else {
      val stub = this.asInstanceOf[ScalaStubBasedElementImpl[_ <: PsiElement]].getStub
      if (stub != null) {
        stub.asInstanceOf[ScTypeAliasStub].getTypeElement.getType(ctx(this))
      } else 
        aliasedTypeElement.getType(ctx(this))
    }
  }

  def aliasedType: TypeResult[ScType] = CachesUtil.get(
      this, CachesUtil.ALIASED_KEY,
      new CachesUtil.MyProvider(this, {ta: ScTypeAliasDefinition => aliasedType(TypingContext.empty)})
        (PsiModificationTracker.MODIFICATION_COUNT)
    )

  def lowerBound: TypeResult[ScType] = aliasedType(TypingContext.empty)
  def upperBound: TypeResult[ScType] = aliasedType(TypingContext.empty)
}