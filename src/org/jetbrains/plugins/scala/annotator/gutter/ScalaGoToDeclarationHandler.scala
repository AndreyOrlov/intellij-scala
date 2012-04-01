package org.jetbrains.plugins.scala
package annotator
package gutter

import lang.lexer.ScalaTokenTypes
import lang.psi.api.statements.ScFunction
import lang.psi.ScalaPsiUtil
import lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScObject, ScClass}
import lang.psi.api.statements.params.ScParameter
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import lang.resolve.ResolvableReferenceElement
import com.intellij.psi.{PsiNamedElement, PsiElement}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.actionSystem.DataContext
import lang.psi.api.expr.{ScAssignStmt, ScMethodCall, ScSelfInvocation}

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.11.2008
 */

class ScalaGoToDeclarationHandler extends GotoDeclarationHandler {

  def getActionText(context: DataContext): String = null

  def getGotoDeclarationTargets(sourceElement: PsiElement, offset: Int, editor: Editor): Array[PsiElement] = {
    if (sourceElement == null) return null
    if (sourceElement.getLanguage != ScalaFileType.SCALA_LANGUAGE) return null;

    if (sourceElement.getNode.getElementType == ScalaTokenTypes.tASSIGN) {
      return sourceElement.getParent match {
        case assign: ScAssignStmt =>
          assign.getLExpression match {
            case ref: ResolvableReferenceElement => ref.bind() match {
              case Some(x) if x.isSetterFunction => Array(x.element)
              case _ => null
            }
            case methodCall: ScMethodCall =>
              methodCall.applyOrUpdateElement match {
                case Some(x) => return Array(x)
                case None => null
              }
            case _ => null
          }
        case _ => null
      }
    }

    if (sourceElement.getNode.getElementType == ScalaTokenTypes.kTHIS) {
      sourceElement.getParent match {
        case self: ScSelfInvocation => {
          self.bind match {
            case Some(elem) => return Array(elem)
            case None => return null
          }
        }
        case _ => return null
      }
    }

    if (sourceElement.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER) {
      val file = sourceElement.getContainingFile
      val ref = file.findReferenceAt(sourceElement.getTextRange.getStartOffset)
      if (ref == null) return null
      val resolved = ref.resolve()
      if (resolved == null) return null

      val mainTargets = goToTargets(resolved)
      val extraTargets: Seq[PsiElement] = ref match {
        case resRef: ResolvableReferenceElement =>
          // `x` should resolve to the apply method and the val in :
          //
          // object A { def apply() }; val x = A; x()
          val inner: Option[PsiNamedElement] = resRef.bind().flatMap(_.innerResolveResult).map(_.getElement)
          inner.map(goToTargets).getOrElse(Seq())
        case _ => Seq()
      }
      return (mainTargets ++ extraTargets).toArray
    }
    null
  }

  private def goToTargets(element: PsiElement): Seq[PsiElement] = {
    element match {
      case fun: ScFunction =>
        val clazz = fun.getContainingClass
        if (fun.name == "copy" && fun.isSyntheticCopy) {
          clazz match {
            case td: ScClass if td.isCase =>
              return Seq(td)
            case _ =>
          }
        }
        ScalaPsiUtil.getCompanionModule(clazz) match {
          case Some(td: ScClass) if td.isCase && td.fakeCompanionModule != None =>
            return Seq(td)
          case _ =>
        }

        clazz match {
          case o: ScObject if o.objectSyntheticMembers.contains(fun) =>
            ScalaPsiUtil.getCompanionModule(clazz) match {
              case Some(c: ScClass) => Seq(o, c) // Offer navigation to the class and object for apply/unapply.
              case _ => Seq(o)
            }
          case td: ScTypeDefinition if td.syntheticMembers.contains(fun) => Seq(td)
          case _ => Seq(element)
        }
      case o: ScObject =>
        ScalaPsiUtil.getCompanionModule(o) match {
          case Some(td: ScClass) if td.isCase && td.fakeCompanionModule != None => Seq(td)
          case _ => Seq(element)
        }
      case param: ScParameter =>
        ScalaPsiUtil.parameterForSyntheticParameter(param).map(Seq[PsiElement](_)).getOrElse(Seq[PsiElement](element))
      case _ => Seq(element)
    }
  }
}