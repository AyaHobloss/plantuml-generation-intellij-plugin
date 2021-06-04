package com.kn.diagrams.generator.generator

import com.kn.diagrams.generator.builder.DiagramDirection
import com.kn.diagrams.generator.builder.DotDiagramBuilder
import com.kn.diagrams.generator.config.CallConfiguration
import com.kn.diagrams.generator.config.StructureConfiguration
import com.kn.diagrams.generator.config.attacheMetaData
import com.kn.diagrams.generator.generator.code.CodeStructureAnalysis
import com.kn.diagrams.generator.graph.*
import com.kn.diagrams.generator.inReadAction
import com.kn.diagrams.generator.toSingleList

fun createStructureDiagramUmlContent(config: StructureConfiguration): List<Pair<String, String>> {
    return CodeStructureAnalysis(config).buildDiagrams()
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
