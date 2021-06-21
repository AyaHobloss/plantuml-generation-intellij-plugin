package com.kn.diagrams.generator.generator

import com.intellij.openapi.project.Project
import com.kn.diagrams.generator.config.*
import com.kn.diagrams.generator.generator.code.CodeStructureAnalysis
import com.kn.diagrams.generator.generator.code.callAndStructureDiagramTemplate
import com.kn.diagrams.generator.graph.*

fun createCallDiagramUmlContent(config: CallConfiguration, project: Project): List<Pair<String, String>> {
    return CodeStructureAnalysis(config, project).buildDiagrams(callAndStructureDiagramTemplate)
}

fun CallConfiguration.visualizationConfig(cache: GraphDefinition) = DiagramVisualizationConfiguration(
    // TODO NPE when class is explicitly excluded
    cache.fromConfigReference(rootMethod ?: rootClass),
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


