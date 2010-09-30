package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import statements._
import com.intellij.psi._
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.util.Iconable
import types.{PhysicalSignature, Signature, ScType, ScSubstitutor}

/**
 * @author AlexanderPodkhalyuzin
 */

trait ScTypeDefinition extends ScTemplateDefinition with ScMember
    with NavigationItem with PsiClass with ScTypeParametersOwner with Iconable with ScDocCommentOwner with ScAnnotationsHolder {

  def isCase : Boolean = false

  def isTopLevel = !parentsInFile.exists(_.isInstanceOf[ScTypeDefinition]) 
  
  def getPath: String = {
    val qualName = getQualifiedName;
    val index = qualName.lastIndexOf('.');
    if (index < 0) "" else qualName.substring(0, index);
  }

  def getQualifiedNameForDebugger: String


  
  def signaturesByName(name: String): Iterable[PhysicalSignature]

  def isPackageObject = false

  override def accept(visitor: ScalaElementVisitor) = visitor.visitTypeDefintion(this)
}