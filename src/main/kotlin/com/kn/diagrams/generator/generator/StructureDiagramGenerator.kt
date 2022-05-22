package com.kn.diagrams.generator.generator

import com.kn.diagrams.generator.actions.ActionContext
import com.kn.diagrams.generator.actions.structure
import com.kn.diagrams.generator.config.StructureConfiguration
import com.kn.diagrams.generator.generator.code.CodeStructureAnalysis
import com.kn.diagrams.generator.generator.code.callAndStructureDiagramTemplate
import com.kn.diagrams.generator.settings.ConfigurationDefaults

fun createStructureDiagramUmlContent(actionContext: ActionContext): List<Pair<String, String>> {
    return CodeStructureAnalysis(actionContext.defaultConfig{ defaultConfiguration() }
            .plantUmlNamingPattern { node, i -> "${fileName}_structure.puml" }
            .structure())
            .buildDiagrams(callAndStructureDiagramTemplate)
}

private fun defaultConfiguration(): StructureConfiguration {
    val defaults = ConfigurationDefaults.structureDiagram()
    return StructureConfiguration("","",
            ConfigurationDefaults.classification(),
            defaults.graphRestriction,
            defaults.graphTraversal,
            defaults.details
    )
}

fun StructureConfiguration.visualizationConfig() = DiagramVisualizationConfiguration(
        null,
        projectClassification,
        details.showPackageLevels,
        details.showClassGenericTypes,
        details.showMethods,
        details.showMethodParameterTypes,
        details.showMethodParameterNames,
        details.showMethodReturnType,
        false,
        details.showDetailedClassStructure
)
