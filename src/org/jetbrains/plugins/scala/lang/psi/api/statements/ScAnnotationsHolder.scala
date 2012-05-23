package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import expr.{ScAnnotations, ScAnnotation}
import types.{ScDesignatorType, ScType}
import java.lang.String
import org.jetbrains.plugins.scala.lang.psi.types.Any
import types.result.TypingContext
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.TokenSet
import parser.ScalaElementTypes
import com.intellij.util.ArrayFactory
import com.intellij.psi._
import extensions.toPsiClassExt
import extensions._
import api.base.ScReferenceElement
import psi.impl.ScalaPsiElementFactory
import annotator.intention.ScalaImportClassFix

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.01.2009
 */

trait ScAnnotationsHolder extends ScalaPsiElement with PsiAnnotationOwner {
  def annotations: Seq[ScAnnotation] = {
    val stub: StubElement[_ <: PsiElement] = this match {
      case st: StubBasedPsiElement[_] if st.getStub != null =>
        st.getStub.asInstanceOf[StubElement[_ <: PsiElement]] // !!! Appeasing an unexplained compile error
      case file: PsiFileImpl if file.getStub != null => file.getStub
      case _ => null
    }
    if (stub != null) {
      val annots: Array[ScAnnotations] =
        stub.getChildrenByType(TokenSet.create(ScalaElementTypes.ANNOTATIONS), JavaArrayFactoryUtil.ScAnnotationsFactory)
      if (annots.length > 0) {
        return annots(0).getAnnotations.toSeq
      } else return Seq.empty
    }
    if (findChildByClassScala(classOf[ScAnnotations]) != null)
      findChildByClassScala(classOf[ScAnnotations]).getAnnotations.toSeq
    else Seq.empty
  }

  def annotationNames: Seq[String] = annotations.map((x: ScAnnotation) => {
    val text: String = x.annotationExpr.constr.typeElement.getText
    text.substring(text.lastIndexOf(".", 0) + 1, text.length)
  })

  def hasAnnotation(clazz: PsiClass): Boolean = hasAnnotation(clazz.qualifiedName) != None

  def hasAnnotation(qualifiedName: String): Option[ScAnnotation] = {
    annotations.find(_.typeElement.getType(TypingContext.empty).getOrAny match {
      case ScDesignatorType(clazz: PsiClass) => clazz.qualifiedName == qualifiedName
      case _ => false
    })
  }

  def addAnnotation(qualifiedName: String): PsiAnnotation = {
    val simpleName = qualifiedName.lastIndexOf('.') |> { i =>
      if (i >= 0) qualifiedName.drop(i + 1) else qualifiedName
    }

    val container = findChildByClassScala(classOf[ScAnnotations])

    val element = ScalaPsiElementFactory.createAnAnnotation(simpleName, getManager)

    container.add(element)
    container.add(ScalaPsiElementFactory.createNewLine(getManager))

    val unresolvedReferences = element.depthFirst
            .findByType(classOf[ScReferenceElement]).filter(_.resolve() == null)

    for (topReference <- unresolvedReferences.headOption;
         manager = JavaPsiFacade.getInstance(getProject);
         annotationClass = manager.findClass(qualifiedName, topReference.getResolveScope)) {
      val holder = ScalaImportClassFix.getImportHolder(this, getProject)
      holder.addImportForClass(annotationClass, topReference)
    }

    element
  }

  def findAnnotation(qualifiedName: String): PsiAnnotation = {
    hasAnnotation(qualifiedName) match {
      case Some(x) => x
      case None => null
    }
  }

  def getApplicableAnnotations: Array[PsiAnnotation] = getAnnotations //todo: understatnd and fix

  def getAnnotations: Array[PsiAnnotation] = annotations.toArray
}