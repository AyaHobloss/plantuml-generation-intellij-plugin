package com.kn.diagrams.generator.actions

import com.intellij.psi.PsiClass
import com.kn.diagrams.generator.config.ClusterConfiguration
import com.kn.diagrams.generator.config.GitConfiguration
import com.kn.diagrams.generator.config.GitDiagramDetails
import com.kn.diagrams.generator.generator.ClusterDiagramGenerator
import com.kn.diagrams.generator.generator.GitDiagramGenerator
import com.kn.diagrams.generator.settings.ConfigurationDefaults


class GenerateGitChangeHeatMapDiagramsAction : AbstractDiagramAction<GitConfiguration>() {

    override fun createDiagramContent(configuration: GitConfiguration): List<Pair<String, String>> {
        return GitDiagramGenerator().createUmlContent(configuration)
    }

    override fun defaultConfiguration(rootClass: PsiClass): GitConfiguration {
        val defaults = ConfigurationDefaults.clusterDiagram()
        return GitConfiguration(rootClass,
                ConfigurationDefaults.classification(),
                defaults.graphRestriction,
                defaults.graphTraversal,
                GitDiagramDetails()
        )
    }

}
