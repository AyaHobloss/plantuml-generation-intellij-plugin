package com.kn.diagrams.generator.actions

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.kn.diagrams.generator.config.ClusterConfiguration
import com.kn.diagrams.generator.generator.createClusterDiagramUmlContent
import com.kn.diagrams.generator.settings.ConfigurationDefaults


class GenerateClusterDiagramAction : AbstractDiagramAction<ClusterConfiguration>() {

    override fun createDiagramContent(configuration: ClusterConfiguration, project: Project): List<Pair<String, String>> {
        return createClusterDiagramUmlContent(configuration, project)
    }

    override fun defaultConfiguration(rootClass: PsiClass): ClusterConfiguration {
        val defaults = ConfigurationDefaults.clusterDiagram()
        return ClusterConfiguration(
                ConfigurationDefaults.classification(),
                defaults.graphRestriction,
                defaults.graphTraversal,
                defaults.details
        )
    }

}
