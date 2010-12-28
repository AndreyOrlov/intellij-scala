package org.jetbrains.plugins.scala.actions

import java.lang.String
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.openapi.project.{Project}
import com.intellij.openapi.project.DumbAware
import com.intellij.ide.actions.{CreateFileFromTemplateDialog, CreateTemplateInPackageAction}
import org.jetbrains.plugins.scala.icons.Icons
import com.intellij.CommonBundle
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.psi._
import org.jetbrains.plugins.scala.util.ScalaUtils
import org.jetbrains.annotations.NonNls
import com.intellij.openapi.module.Module
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.fileTypes.StdFileTypes
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaBundle}
import com.intellij.ide.actions.CreateFileFromTemplateDialog.Builder
import com.intellij.pom.java.LanguageLevel
import com.intellij.openapi.roots.{LanguageLevelProjectExtension, ProjectRootManager, ProjectFileIndex}
import com.intellij.ide.{IdeBundle, IdeView}
import com.intellij.ide.fileTemplates.JavaTemplateUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.09.2009
 */

class NewScalaTypeDefinitionAction extends CreateTemplateInPackageAction[ScTypeDefinition](
  ScalaBundle.message("newclass.menu.action.text"), ScalaBundle.message("newclass.menu.action.description"), Icons.CLASS, true) with DumbAware {
  protected def buildDialog(project: Project, directory: PsiDirectory,
                            builder: CreateFileFromTemplateDialog.Builder): Unit = {
    builder.addKind("Class", Icons.CLASS, "ScalaClass.scala");
    builder.addKind("Object", Icons.OBJECT, "ScalaObject.scala");
    builder.addKind("Trait", Icons.TRAIT, "ScalaTrait.scala");
    builder.setTitle("Create New Scala Class")
    return builder;
  }

  def getActionName(directory: PsiDirectory, newName: String, templateName: String): String = {
    ScalaBundle.message("newclass.menu.action.text")
  }

  def getNavigationElement(createdElement: ScTypeDefinition): PsiElement = createdElement.extendsBlock

  def doCreate(directory: PsiDirectory, newName: String, templateName: String): ScTypeDefinition = {
    val file: PsiFile = createClassFromTemplate(directory, newName, templateName);
    if (file.isInstanceOf[ScalaFile]) {
      val scalaFile = file.asInstanceOf[ScalaFile]
      val classes = scalaFile.getClasses
      if (classes.length == 1 && classes(0).isInstanceOf[ScTypeDefinition]) {
        val definition = classes(0).asInstanceOf[ScTypeDefinition]
        return definition
      }
    }
    return null
  }

  override def isAvailable(dataContext: DataContext): Boolean = {
    return super.isAvailable(dataContext) && isUnderSourceRoots(dataContext)
  }

  private def isUnderSourceRoots(dataContext: DataContext): Boolean = {
    val module: Module = dataContext.getData(LangDataKeys.MODULE.getName).asInstanceOf[Module]
    if (!ScalaUtils.isSuitableModule(module)) {
      return false
    }
    val view = dataContext.getData(LangDataKeys.IDE_VIEW.getName).asInstanceOf[IdeView]
    val project = dataContext.getData(PlatformDataKeys.PROJECT.getName).asInstanceOf[Project]
    if (view != null && project != null) {
      val projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex
      val dirs = view.getDirectories
      for (dir <- dirs) {
        val aPackage = JavaDirectoryService.getInstance.getPackage(dir)
        if (projectFileIndex.isInSourceContent(dir.getVirtualFile) && aPackage != null) {
          return true
        }
      }
    }
    return false
  }

  private def createClassFromTemplate(directory: PsiDirectory, className: String, templateName: String,
                                                   parameters: String*): PsiFile = {
    return ScalaTemplatesFactory.createFromTemplate(directory, className, className + SCALA_EXTENSIOIN, templateName, parameters: _*)
  }

  private val SCALA_EXTENSIOIN = ".scala";

  override def doCheckCreate(dir: PsiDirectory, className: String, templateName: String): Unit = {
    if (!ScalaNamesUtil.isIdentifier(className)) {
      throw new IncorrectOperationException(PsiBundle.message("0.is.not.an.identifier", className))
    }
    val fileName: String = className + "." + ScalaFileType.DEFAULT_EXTENSION
    dir.checkCreateFile(fileName)
    val helper: PsiNameHelper = JavaPsiFacade.getInstance(dir.getProject).getNameHelper
    var aPackage: PsiPackage = JavaDirectoryService.getInstance.getPackage(dir)
    val qualifiedName: String = if (aPackage == null) null else aPackage.getQualifiedName
    if (!StringUtil.isEmpty(qualifiedName) && !helper.isQualifiedName(qualifiedName)) {
      throw new IncorrectOperationException("Cannot create class in invalid package: '" + qualifiedName + "'")
    }
  }

  def checkPackageExists(directory: PsiDirectory) = {
    JavaDirectoryService.getInstance.getPackage(directory) != null
  }
}