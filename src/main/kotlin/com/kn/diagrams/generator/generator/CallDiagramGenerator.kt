package com.kn.diagrams.generator.generator

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.kn.diagrams.generator.builder.*
import com.kn.diagrams.generator.cast
import com.kn.diagrams.generator.config.*
import com.kn.diagrams.generator.generator.code.CodeStructureAnalysis
import com.kn.diagrams.generator.graph.*
import com.kn.diagrams.generator.inReadAction
import com.kn.diagrams.generator.notifications.notifyErrorMissingPublicMethod
import com.kn.diagrams.generator.toSingleList
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.math.absoluteValue
import kotlin.streams.toList

fun createCallDiagramUmlContent(config: CallConfiguration): List<Pair<String, String>> {
    return CodeStructureAnalysis(config).buildDiagrams()
}

fun CallConfiguration.visualizationConfig(cache: GraphDefinition) = DiagramVisualizationConfiguration(
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


