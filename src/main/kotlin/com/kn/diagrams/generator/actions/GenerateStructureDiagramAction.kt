package com.kn.diagrams.generator.actions

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.kn.diagrams.generator.config.StructureConfiguration
import com.kn.diagrams.generator.generator.createStructureDiagramUmlContent
import com.kn.diagrams.generator.settings.ConfigurationDefaults


class GenerateStructureDiagramAction : AbstractDiagramAction<StructureConfiguration>() {

    override fun createDiagramContent(configuration: StructureConfiguration, project: Project): List<Pair<String, String>> {
        return createStructureDiagramUmlContent(configuration)
    }

    override fun defaultConfiguration(rootClass: PsiClass): StructureConfiguration {
        val defaults = ConfigurationDefaults.structureDiagram()
        return StructureConfiguration(rootClass,
                ConfigurationDefaults.classification(),
                defaults.graphRestriction,
                defaults.graphTraversal,
                defaults.details
        )
    }

}
