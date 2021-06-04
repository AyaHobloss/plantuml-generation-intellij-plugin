package com.kn.diagrams.generator.actions

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.kn.diagrams.generator.config.CallConfiguration
import com.kn.diagrams.generator.generator.createCallDiagramUmlContent
import com.kn.diagrams.generator.settings.ConfigurationDefaults

class GenerateCallDiagramsAction : AbstractDiagramAction<CallConfiguration>() {


    override fun createDiagramContent(configuration: CallConfiguration, project: Project): List<Pair<String, String>> {
        return createCallDiagramUmlContent(configuration)
    }

    override fun defaultConfiguration(rootClass: PsiClass): CallConfiguration {
        val defaults = ConfigurationDefaults.callDiagram()
        return CallConfiguration(rootClass, null,
                ConfigurationDefaults.classification(),
                defaults.graphRestriction,
                defaults.graphTraversal,
                defaults.details
        )
    }

}
