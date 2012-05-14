package org.jetbrains.plugins.scala
package overrideImplement

import _root_.org.jetbrains.plugins.scala.lang.psi.types.{ScType, PhysicalSignature, ScSubstitutor}
import com.intellij.psi.{PsiMethod, PsiSubstitutor}
import com.intellij.codeInsight.generation.PsiElementClassMember
import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.codeInsight.generation.ClassMember
import lang.psi.api.statements.{ScValue, ScTypeAlias, ScVariable}
import lang.psi.api.toplevel.ScTypedDefinition
import lang.psi.ScalaPsiUtil
import lang.psi.types._
import lang.psi.types.result.TypingContext

/**
* User: Alexander Podkhalyuzin
* Date: 11.07.2008
*/

private[overrideImplement] trait ScalaNamedMembers {
  def name: String
}

class ScAliasMember(member: ScTypeAlias, val substitutor: ScSubstitutor)
  extends PsiElementClassMember[ScTypeAlias](member, member.name) with ScalaNamedMembers {
  def name: String = member.name
}

class ScMethodMember(val sign: PhysicalSignature) extends PsiElementClassMember[PsiMethod](sign.method,
  ScalaPsiUtil.getMethodPresentableText(sign.method)) with ScalaNamedMembers {
  def name: String = sign.name
}

class ScValueMember(member: ScValue, val element: ScTypedDefinition, val substitutor: ScSubstitutor) extends PsiElementClassMember[ScValue](member,
  element.name + ": " + ScType.presentableText(substitutor.subst(element.getType(TypingContext.empty).getOrAny))) with ScalaNamedMembers {
  def name: String = element.name
}

class ScVariableMember(member: ScVariable, val element: ScTypedDefinition, val substitutor: ScSubstitutor) extends PsiElementClassMember[ScVariable](member,
  element.name + ": " + ScType.presentableText(substitutor.subst(element.getType(TypingContext.empty).getOrAny))) with ScalaNamedMembers {
  def name: String = element.name
}