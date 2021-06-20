package com.kn.diagrams.generator.actions

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.kn.diagrams.generator.config.FlowConfiguration
import com.kn.diagrams.generator.generator.FlowDiagramGenerator
import com.kn.diagrams.generator.settings.ConfigurationDefaults

class GenerateFlowDiagramsAction : AbstractDiagramAction<FlowConfiguration>() {

    override fun createDiagramContent(configuration: FlowConfiguration, project: Project): List<Pair<String, String>> {
        return FlowDiagramGenerator().createUmlContent(configuration, project)
    }

    override fun defaultConfiguration(rootClass: PsiClass): FlowConfiguration {
        val defaults = ConfigurationDefaults.flowDiagram()
        return FlowConfiguration(rootClass.qualifiedName!!, null,
                ConfigurationDefaults.classification(),
                defaults.graphRestriction,
                defaults.graphTraversal
        )
    }

}
