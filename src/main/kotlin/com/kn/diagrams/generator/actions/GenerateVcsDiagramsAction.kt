package com.kn.diagrams.generator.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiDirectory
import com.kn.diagrams.generator.config.VcsConfiguration
import com.kn.diagrams.generator.config.VcsDiagramDetails
import com.kn.diagrams.generator.generator.createVcsContent
import com.kn.diagrams.generator.settings.ConfigurationDefaults


class GenerateVcsDiagramsAction : AbstractDiagramAction<VcsConfiguration>() {

    override fun createDiagramContent(event: AnActionEvent) = generateWith(event
            .directoryBasedContext()
            .defaultConfig { defaultConfiguration() }
    )

    override fun generateWith(actionContext: ActionContext): List<Pair<String, String>> {
        return createVcsContent(actionContext)
    }

    private fun defaultConfiguration(): VcsConfiguration {
        val defaults = ConfigurationDefaults.clusterDiagram()
        return VcsConfiguration("",
                ConfigurationDefaults.classification(),
                defaults.graphRestriction,
                VcsDiagramDetails()
        )
    }

    override fun writeDiagramToFile(directory: PsiDirectory, diagramFileName: String, diagramContent: String) {
        super.writeDiagramToFile(directory, directory.findNonExistingFile(diagramFileName), diagramContent)
    }

}
