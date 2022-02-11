package com.kn.diagrams.generator.generator

import com.intellij.openapi.project.Project
import com.kn.diagrams.generator.builder.DiagramDirection
import com.kn.diagrams.generator.builder.DotDiagramBuilder
import com.kn.diagrams.generator.config.StructureConfiguration
import com.kn.diagrams.generator.config.attacheMetaData
import com.kn.diagrams.generator.graph.*
import com.kn.diagrams.generator.inReadAction
import com.kn.diagrams.generator.toSingleList


class StructureDiagramGenerator {

    fun createUmlContent(config: StructureConfiguration, project: Project): List<Pair<String, String>> {
        val restrictionFilter = inReadAction { config.restrictionFilter(project) }
        val cache = GraphCache(project, restrictionFilter, config.projectClassification.searchMode)

        val root = inReadAction { cache.classFor(config.rootClass.psiClassFromQualifiedName(project))!! }

        val edges = cache.search(config.traversalFilter(root)) {
            roots = root.toSingleList()
            forwardDepth = config.graphTraversal.forwardDepth
            backwardDepth = config.graphTraversal.backwardDepth
            edgeMode = EdgeMode.TypesOnly
            useStructureCalls = config.graphTraversal.useMethodCallsForStructureDiagram
        }.flatten()

        val visualizationConfiguration = inReadAction { config.visualizationConfig(root) }
        val dot = DotDiagramBuilder()
        dot.direction = DiagramDirection.TopToBottom

        when (config.details.aggregation) {
            Aggregation.ByClass -> dot.aggregateByClass(edges, visualizationConfiguration)
            Aggregation.GroupByClass -> dot.groupByClass(edges, visualizationConfiguration)
            Aggregation.None -> dot.noAggregation(edges, visualizationConfiguration)
        }

        val diagramExtension = config.diagramExtension(project)
        return listOf("structure" to diagramExtension(dot.create().attacheMetaData(config)))
    }
}

fun StructureConfiguration.visualizationConfig(root: AnalyzeClass) = DiagramVisualizationConfiguration(
    root,
    projectClassification,
    projectClassification.includedProjects,
    projectClassification.pathEndKeywords,
    details.showPackageLevels,
    details.showClassGenericTypes,
    details.showMethods,
    details.showMethodParameterTypes,
    details.showMethodParameterNames,
    details.showMethodReturnType,
    false,
    details.showDetailedClassStructure
)
