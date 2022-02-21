package com.kn.diagrams.generator.generator

import com.kn.diagrams.generator.actions.ActionContext
import com.kn.diagrams.generator.actions.CallActionContext
import com.kn.diagrams.generator.actions.call
import com.kn.diagrams.generator.cast
import com.kn.diagrams.generator.config.CallConfiguration
import com.kn.diagrams.generator.generator.code.CodeStructureAnalysis
import com.kn.diagrams.generator.generator.code.callAndStructureDiagramTemplate
import com.kn.diagrams.generator.graph.AnalyzeMethod
import com.kn.diagrams.generator.settings.ConfigurationDefaults

fun createCallDiagramUmlContent(actionContext: ActionContext): List<Pair<String, String>> {
    return CodeStructureAnalysis(actionContext
            .defaultConfig{ defaultConfiguration() }
            .plantUmlNamingPattern { node, i -> "${fileName}_${i}_${ node.cast<AnalyzeMethod>()?.name }_calls.puml" }
            .call())
            .buildDiagrams(callAndStructureDiagramTemplate)
}

private fun defaultConfiguration(): CallConfiguration {
    val defaults = ConfigurationDefaults.callDiagram()
    return CallConfiguration("", null,
            ConfigurationDefaults.classification(),
            defaults.graphRestriction,
            defaults.graphTraversal,
            defaults.details
    )
}

fun CallConfiguration.visualizationConfig() = DiagramVisualizationConfiguration(
    null, // TODO root node?
    projectClassification,
    details.showPackageLevels,
    false,
    false,
    details.showMethodParametersTypes,
    details.showMethodParametersNames,
    details.showMethodReturnType,
    details.showCallOrder,
    details.showDetailedClassStructure
)


