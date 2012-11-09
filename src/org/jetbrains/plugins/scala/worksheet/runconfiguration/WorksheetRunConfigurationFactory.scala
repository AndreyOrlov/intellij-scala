package org.jetbrains.plugins.scala
package worksheet.runconfiguration

import com.intellij.execution.configurations.{RunConfiguration, ConfigurationFactory, ConfigurationType}
import com.intellij.openapi.project.Project
import config.ScalaFacet

/**
 * @author Ksenia.Sautina
 * @since 10/16/12
 */
class WorksheetRunConfigurationFactory (val typez: ConfigurationType) extends ConfigurationFactory(typez) {
  def createTemplateConfiguration(project: Project): RunConfiguration = {
    new WorksheetRunConfiguration(project, this, "")
  }

  override def createConfiguration(name: String, template: RunConfiguration): RunConfiguration = {
    val configuration = (super.createConfiguration(name, template)).asInstanceOf[WorksheetRunConfiguration]
    ScalaFacet.findModulesIn(template.getProject).headOption.foreach {
      configuration.setModule _
    }
    configuration
  }
}
