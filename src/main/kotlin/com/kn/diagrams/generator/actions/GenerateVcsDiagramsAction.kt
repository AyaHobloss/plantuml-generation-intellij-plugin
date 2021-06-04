package com.kn.diagrams.generator.actions

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.kn.diagrams.generator.config.VcsConfiguration
import com.kn.diagrams.generator.config.VcsDiagramDetails
import com.kn.diagrams.generator.generator.createVcsContent
import com.kn.diagrams.generator.settings.ConfigurationDefaults
import org.jetbrains.annotations.Nullable


class GenerateVcsDiagramsAction : AbstractDiagramAction<VcsConfiguration>() {

    override fun createDiagramContent(configuration: VcsConfiguration, project: Project) = createVcsContent(configuration, project)

    override fun defaultConfiguration(rootClass: PsiClass): VcsConfiguration {
        val defaults = ConfigurationDefaults.clusterDiagram()
        return VcsConfiguration(
                ConfigurationDefaults.classification(),
                defaults.graphRestriction,
                VcsDiagramDetails()
        )
    }

    override fun writeDiagramToFile(directory: PsiDirectory, diagramFileName: String, diagramContent: String) {
        if(directory.findFile(diagramFileName) != null){
            val noneExistingFileName = IntRange(0, 100)
                    .map { diagramFileName + it }
                    .firstOrNull { directory.findFile(diagramFileName) == null }

            super.writeDiagramToFile(directory, noneExistingFileName ?: diagramFileName, diagramContent)
        }

        super.writeDiagramToFile(directory, diagramFileName, diagramContent)
    }

}
