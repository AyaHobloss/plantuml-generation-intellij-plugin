package com.kn.diagrams.generator.actions

import com.intellij.psi.PsiClass
import com.kn.diagrams.generator.config.ClusterConfiguration
import com.kn.diagrams.generator.generator.ClusterDiagramGenerator
import com.kn.diagrams.generator.settings.ConfigurationDefaults


class GenerateClusterDiagramAction : AbstractDiagramAction<ClusterConfiguration>() {

    override fun createDiagramContent(configuration: ClusterConfiguration): List<Pair<String, String>> {
        return ClusterDiagramGenerator().createUmlContent(configuration)
    }

    override fun defaultConfiguration(rootClass: PsiClass): ClusterConfiguration {
        val defaults = ConfigurationDefaults.clusterDiagram()
        return ClusterConfiguration(rootClass,
                ConfigurationDefaults.classification(),
                defaults.graphRestriction,
                defaults.graphTraversal,
                defaults.details
        )
    }

}
