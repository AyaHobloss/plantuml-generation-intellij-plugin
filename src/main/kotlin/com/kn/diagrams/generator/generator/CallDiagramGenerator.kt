package com.kn.diagrams.generator.generator

import com.kn.diagrams.generator.config.*
import com.kn.diagrams.generator.generator.code.CodeStructureAnalysis
import com.kn.diagrams.generator.generator.code.callAndStructureDiagramTemplate
import com.kn.diagrams.generator.graph.*

fun createCallDiagramUmlContent(config: CallConfiguration): List<Pair<String, String>> {
    return CodeStructureAnalysis(config).buildDiagrams(callAndStructureDiagramTemplate)
}

fun CallConfiguration.visualizationConfig(cache: GraphDefinition) = DiagramVisualizationConfiguration(
    // TODO NPE when class is explicitly excluded
    rootMethod?.let { cache.methodFor(it) } ?: cache.classes[rootClass.reference().id()]!!,
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


