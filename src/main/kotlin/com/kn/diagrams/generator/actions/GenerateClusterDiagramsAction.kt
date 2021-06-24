package com.kn.diagrams.generator.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.kn.diagrams.generator.config.ClusterConfiguration
import com.kn.diagrams.generator.generator.createClusterDiagramUmlContent
import com.kn.diagrams.generator.settings.ConfigurationDefaults


class GenerateClusterDiagramAction : AbstractDiagramAction<ClusterConfiguration>() {

    override fun createDiagramContent(event: AnActionEvent): List<Pair<String, String>> {
        return generateWith(event
                .directoryBasedContext()
                .defaultConfig { defaultConfiguration() }
        )
    }

    override fun generateWith(actionContext: ActionContext): List<Pair<String, String>>  {
        return createClusterDiagramUmlContent(actionContext)
    }

    private fun defaultConfiguration(): ClusterConfiguration {
        val defaults = ConfigurationDefaults.clusterDiagram()
        return ClusterConfiguration(
                ConfigurationDefaults.classification(),
                defaults.graphRestriction,
                defaults.graphTraversal,
                defaults.details
        )
    }

}
