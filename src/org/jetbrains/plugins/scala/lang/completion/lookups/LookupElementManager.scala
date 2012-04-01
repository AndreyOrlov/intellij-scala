package org.jetbrains.plugins.scala.lang.completion.lookups

import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.extensions.toPsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.types.{Nothing, ScType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext, TypingContextOwner}
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.codeInsight.lookup.LookupElement

/**
 * @author Alefas
 * @since 19.03.12
 */
object LookupElementManager {
  def getKeywrodLookupElement(keyword: String, position: PsiElement): LookupElement = {
    new ScalaKeywordLookupItem(keyword, position)
  }

  def getLookupElement(resolveResult: ScalaResolveResult,
                qualifierType: ScType = Nothing,
                isClassName: Boolean = false,
                isInImport: Boolean = false,
                isOverloadedForClassName: Boolean = false,
                shouldImport: Boolean = false,
                isInStableCodeReference: Boolean = false): Seq[ScalaLookupItem] = {
    val element = resolveResult.element
    val substitutor = resolveResult.substitutor
    val isRenamed: Option[String] = resolveResult.isRenamed match {
      case Some(x) if element.name != x => Some(x)
      case _ => None
    }
    def getLookupElementInternal(isAssignment: Boolean, name: String): ScalaLookupItem = {
      var lookupItem: ScalaLookupItem = new ScalaLookupItem(element, name, ScType.extractClass(qualifierType, Some(resolveResult.element.getProject)))
      lookupItem.isClassName = isClassName
      var isBold = false
      var isDeprecated = false
      ScType.extractDesignated(qualifierType) match {
        case Some((named, _)) => {
          val clazz: PsiClass = named match {
            case cl: PsiClass => cl
            case tp: TypingContextOwner => tp.getType(TypingContext.empty).map(ScType.extractClass(_)) match {
              case Success(Some(cl), _) => cl
              case _ => null
            }
            case _ => null
          }
          if (clazz != null)
            ScalaPsiUtil.nameContext(element) match {
              case m: PsiMember => {
                if (m.getContainingClass == clazz) isBold = true
              }
              case _ =>
            }
        }
        case _ =>
      }
      val isUnderlined = resolveResult.implicitFunction != None
      element match {
        case doc: PsiDocCommentOwner if doc.isDeprecated => isDeprecated = true
        case _ =>
      }
      lookupItem.isNamedParameter = resolveResult.isNamedParameter
      lookupItem.isDeprecated = isDeprecated
      lookupItem.isOverloadedForClassName = isOverloadedForClassName
      lookupItem.isRenamed = isRenamed
      lookupItem.isUnderlined = isUnderlined
      lookupItem.isAssignment = isAssignment
      lookupItem.isInImport = isInImport
      lookupItem.bold = isBold
      lookupItem.shouldImport = shouldImport
      lookupItem.isInStableCodeReference = isInStableCodeReference
      lookupItem.substitutor = substitutor
      lookupItem
    }

    val name: String = isRenamed.getOrElse(element.name)
    val Setter = """(.*)_=""".r
    name match {
      case Setter(prefix) =>
        Seq(getLookupElementInternal(true, prefix), getLookupElementInternal(false, name))
      case _ => Seq(getLookupElementInternal(false, name))
    }
  }
}
