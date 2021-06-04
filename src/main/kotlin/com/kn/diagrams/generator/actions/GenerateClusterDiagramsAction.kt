package com.kn.diagrams.generator.actions

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.kn.diagrams.generator.config.ClusterConfiguration
import com.kn.diagrams.generator.generator.ClusterDiagramGenerator
import com.kn.diagrams.generator.settings.ConfigurationDefaults
import org.jetbrains.annotations.Nullable


class GenerateClusterDiagramAction : AbstractDiagramAction<ClusterConfiguration>() {

    override fun createDiagramContent(configuration: ClusterConfiguration, project: Project): List<Pair<String, String>> {
        return ClusterDiagramGenerator().createUmlContent(configuration)
    }

    override fun defaultConfiguration(rootClass: PsiClass): ClusterConfiguration {
        val defaults = ConfigurationDefaults.clusterDiagram()
        // TODO cluster also independend?!
        return ClusterConfiguration(rootClass,
                ConfigurationDefaults.classification(),
                defaults.graphRestriction,
                defaults.graphTraversal,
                defaults.details
        )
    }

}
