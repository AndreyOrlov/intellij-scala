package org.jetbrains.plugins.scala
package lang
package refactoring
package rename

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{Messages, DialogWrapper}
import com.intellij.refactoring.rename.RenameJavaMethodProcessor
import java.awt.{GridLayout, BorderLayout}

import javax.swing._
import psi.api.statements.ScFunction
import psi.impl.search.ScalaOverridengMemberSearch
import psi.api.base.ScPrimaryConstructor
import collection.mutable.ArrayBuffer
import psi.fake.FakePsiMethod
import extensions.toPsiNamedElementExt
import java.util.{Collection, Map}
import com.intellij.psi.{PsiReference, PsiElement}
import collection.JavaConverters.{asJavaCollectionConverter, iterableAsScalaIterableConverter}
import resolve.ResolvableReferenceElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.11.2008
 */

class RenameScalaMethodProcessor extends RenameJavaMethodProcessor {
  override def canProcessElement(element: PsiElement): Boolean = {
    (element.isInstanceOf[ScFunction] || element.isInstanceOf[ScPrimaryConstructor]) &&
      !element.isInstanceOf[FakePsiMethod]
  }

  override def findReferences(element: PsiElement) = ScalaRenameUtil.filterAliasedReferences(super.findReferences(element))

  override def prepareRenaming(element: PsiElement, newName: String, allRenames: Map[PsiElement, String]) {
    val function = element match {case x: ScFunction => x case _ => return}
    val buff = new ArrayBuffer[ScFunction]
    function.getGetterOrSetterFunction match {
      case Some(function2) => buff += function2
      case _ =>
    }
    for (elem <- ScalaOverridengMemberSearch.search(function, deep = true)) {
      val overriderName = elem.name
      val baseName = function.name
      //val newOverriderName = RefactoringUtil.suggestNewOverriderName(overriderName, baseName, newName)
      if (overriderName == baseName) {
        allRenames.put(elem, newName)
        elem match {
          case fun: ScFunction => fun.getGetterOrSetterFunction match {
            case Some(function2) => buff += function2
            case _ =>
          }
          case _ =>
        }
      }
    }
    if (!buff.isEmpty) {
      val dialog = new WarningDialog(function.getProject,
        "Function has getters or setters with same name. Rename them as well?")
      dialog.show()

      if (dialog.getExitCode == DialogWrapper.OK_EXIT_CODE) {
        val shortNewName = if (newName.endsWith("_=")) newName.substring(0, newName.length - 2) else newName
        for (elem <- buff) {
          allRenames.put(elem, if (elem.name.endsWith("_=")) shortNewName + "_=" else shortNewName)
        }
      }
    }
  }



  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = {
    element match {case x: ScPrimaryConstructor => return x.containingClass case _ =>}
    val function: ScFunction = element match {case x: ScFunction => x case _ => return element}
    if (function.isConstructor) return function.containingClass
    function.name match {
      case "apply" | "unapply" | "unapplySeq" => {
        return function.containingClass
      }
      case _ =>
    }

    val signs = function.superSignatures
    if (signs.length == 0 || signs.last.namedElement.isEmpty) return function
    val result = Messages.showDialog(element.getProject, ScalaBundle.message("method.has.supers", function.name), "Warning",
      Array(CommonBundle.getYesButtonText, CommonBundle.getNoButtonText, CommonBundle.getCancelButtonText), 0, Messages.getQuestionIcon
    )
    result match {
      case 0 => signs.last.namedElement.get
      case 1 => function
      case 2 => null
    }
  }

  private class WarningDialog(project: Project, text: String) extends DialogWrapper(project, true) {
    setTitle(IdeBundle.message("title.warning"))
    setButtonsAlignment(SwingConstants.CENTER)
    setOKButtonText(CommonBundle.getYesButtonText)
    init()

    def createCenterPanel: JComponent = null

    override def createNorthPanel: JComponent = {
      val panel = new JPanel(new BorderLayout)
      panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10))
      val icon = Messages.getWarningIcon
      if (icon != null) {
        val iconLabel = new JLabel(Messages.getQuestionIcon)
        panel.add(iconLabel, BorderLayout.WEST)
      }
      val labelsPanel = new JPanel(new GridLayout(0, 1, 0, 0))
      labelsPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 10))
      labelsPanel.add(new JLabel(text))
      panel.add(labelsPanel, BorderLayout.CENTER)
      panel
    }
  }

  def capitalize(text: String): String = Character.toUpperCase(text.charAt(0)) + text.substring(1)
}

