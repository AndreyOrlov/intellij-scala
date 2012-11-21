package org.jetbrains.plugins.scala
package config.scalaProjectTemplate

import com.intellij.platform.{ProjectTemplate, ProjectTemplatesFactory}
import com.intellij.ide.util.projectWizard.WizardContext
import javax.swing.Icon
import tools.scalap.scalax.rules.scalasig.NoSymbol

/**
 * User: Dmitry Naydanov
 * Date: 11/7/12
 */
class ScalaProjectTemplatesFactory extends ProjectTemplatesFactory {
  def getGroups: Array[String] = Array("Scala")

  def createTemplates(group: String, context: WizardContext): Array[ProjectTemplate] = Array[ProjectTemplate](new ScalaProjectTemplate)

  override def getGroupIcon(group: String): Icon = if (group == "Scala") org.jetbrains.plugins.scala.icons.Icons.BIG_ICON else null
}
