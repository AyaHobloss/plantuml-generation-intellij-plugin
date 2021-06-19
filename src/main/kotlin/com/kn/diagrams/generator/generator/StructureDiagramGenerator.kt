package com.kn.diagrams.generator.generator

import com.kn.diagrams.generator.config.StructureConfiguration
import com.kn.diagrams.generator.generator.code.CodeStructureAnalysis
import com.kn.diagrams.generator.generator.code.callAndStructureDiagramTemplate

fun createStructureDiagramUmlContent(config: StructureConfiguration): List<Pair<String, String>> {
    return CodeStructureAnalysis(config).buildDiagrams(callAndStructureDiagramTemplate)
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
