package com.kn.diagrams.generator.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiDirectory
import com.kn.diagrams.generator.config.GeneticsConfiguration
import com.kn.diagrams.generator.generator.createGeneticDiagramUmlContent
import com.kn.diagrams.generator.settings.ConfigurationDefaults

class GenerateGeneticsAction : AbstractDiagramAction<GeneticsConfiguration>() {
    override fun createDiagramContent(event: AnActionEvent): List<Pair<String, String>> {
        return generateWith(event
            .directoryBasedContext()
            .defaultConfig { defaultConfiguration() }
        )
    }

    override fun generateWith(actionContext: ActionContext): List<Pair<String, String>>  {
        return createGeneticDiagramUmlContent(actionContext)
    }

    private fun defaultConfiguration(): GeneticsConfiguration {
        val defaults = ConfigurationDefaults.geneticDiagram()

        return GeneticsConfiguration( "",
            ConfigurationDefaults.classification(),
            defaults.graphRestriction,
            defaults.graphTraversal,
            defaults.details
        )
    }

    override fun writeDiagramToFile(directory: PsiDirectory, diagramFileName: String, diagramContent: String) {
        super.writeDiagramToFile(directory, directory.findNonExistingFile(diagramFileName), diagramContent)
    }

}
