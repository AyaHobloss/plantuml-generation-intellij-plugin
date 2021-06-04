package com.kn.diagrams.generator.generator.code

import com.intellij.openapi.project.Project
import com.kn.diagrams.generator.builder.DiagramDirection
import com.kn.diagrams.generator.cast
import com.kn.diagrams.generator.config.*
import com.kn.diagrams.generator.generator.*
import com.kn.diagrams.generator.graph.*
import com.kn.diagrams.generator.inReadAction
import com.kn.diagrams.generator.notifications.notifyErrorMissingPublicMethod
import kotlin.streams.toList

class CodeStructureAnalysis {

    val project: Project
    val config: CodeDiagramConfig
    val cache: GraphDefinition
    val rootNodes: List<GraphNode>
    val fileNamingPattern: (GraphNode, Int) -> String

    val restrictionFilter: GraphRestrictionFilter
    val traversalFilter: TraversalFilter
    val visualizationConfig: DiagramVisualizationConfiguration
    val metaDataSection: String

    constructor(diagramConfig: CallConfiguration) {
        fileNamingPattern = { method, i -> "${i}_${ method.cast<AnalyzeMethod>()?.name }}_calls" }

        project =  diagramConfig.rootClass.project
        restrictionFilter = inReadAction { diagramConfig.restrictionFilter() }

        cache = analysisCache.getOrCompute(project, restrictionFilter, diagramConfig.projectClassification.searchMode)

        traversalFilter = inReadAction { diagramConfig.traversalFilter() }
        visualizationConfig = inReadAction { diagramConfig.visualizationConfig(cache) }

        with(diagramConfig){
            config = CodeDiagramConfig(
                    DiagramDirection.LeftToRight,
                    details.nodeAggregation,
                    details.nodeGrouping,
                    details.wrapMethodsWithItsClass,
                    graphTraversal.forwardDepth,
                    graphTraversal.backwardDepth,
                    EdgeMode.MethodsOnly
            )
        }
        metaDataSection = diagramConfig.metaDataSection()

        rootNodes = inReadAction {
            val requestedMethod = diagramConfig.rootMethod
            diagramConfig.rootClass.methods
                    .filter { requestedMethod == null || requestedMethod == it }
                    .filter { requestedMethod != null || !it.isPrivate() }
                    .mapNotNull { cache.methodFor(it) }
        }

        if (rootNodes.isEmpty()) {
            notifyErrorMissingPublicMethod(inReadAction { project }, diagramConfig.rootClass, diagramConfig.rootMethod)
        }
    }

    constructor(diagramConfig: StructureConfiguration) {
        fileNamingPattern = { method, _ -> "${method.cast<AnalyzeClass>()?.reference?.name }_structure" }

        project = inReadAction { diagramConfig.rootClass.project }
        restrictionFilter = inReadAction { diagramConfig.restrictionFilter() }

        cache = analysisCache.getOrCompute(project, restrictionFilter, diagramConfig.projectClassification.searchMode)

        traversalFilter = inReadAction { diagramConfig.traversalFilter() }
        visualizationConfig = inReadAction { diagramConfig.visualizationConfig() }

        with(diagramConfig){
            config = CodeDiagramConfig(
                    DiagramDirection.TopToBottom,
                    details.nodeAggregation,
                    details.nodeGrouping,
                    false,
                    graphTraversal.forwardDepth,
                    graphTraversal.backwardDepth,
                    EdgeMode.TypesOnly
            )
        }
        metaDataSection = diagramConfig.metaDataSection()

        rootNodes = listOfNotNull(inReadAction { cache.classFor(diagramConfig.rootClass) })
    }


    fun buildDiagrams(): List<Pair<String, String>> {
        return perRootNode {
            methodNodes.forEach { method ->
                method.grouping().addShape(method.signature(), method.diagramId()) {
                    penWidth = method.rootNodePenWidth()
                    tooltip = method.containingClass.name + "\n\n" + method.javaDoc
                    fontColor = method.visibility.color()
                    style = "filled"
                    fillColor = "white"
                }
            }

            classNodes.forEach { clazz ->
                clazz.grouping().addNode {
                    if (visualizationConfig.showDetailedClass) {
                        clazz.createHTMLShape(visualizationConfig)
                    } else {
                        clazz.createBoxShape()
                    }
                }
            }

            edges.parallelStream().flatMap { edge ->
                edge.createLinksPerContext { context, hasMoreEdges ->
                    when (context) {
                        is MethodClassUsage -> {
                            style = "dashed"
                            if (!hasMoreEdges) {
                                label = context.reference
                            }
                        }
                        is AnalyzeCall -> {
                            label = context.sequence.toString().takeIf { it != "-1" }.takeIf { visualizationConfig.showCallOrder }
                        }
                        is FieldWithTargetType -> {
                            label = context.field.name + "\n" + context.field.cardinality()
                        }
                        is InheritanceType -> {
                            val hasFullInheritance = edge.edges().all { InheritanceType.Implementation in it.context || InheritanceType.SubClass in it.context }
                            if(hasFullInheritance){
                                dir = "both"
                                arrowHead = "none"
                                arrowTail = "empty"
                            }
                        }
                    }
                }.stream()
            }.toList().let { dot.edges.addAll(it) }
        }
    }

    private fun perRootNode(diagramCreation: RootNodeBuildContext.() -> Unit): List<Pair<String, String>>{
        return rootNodes.sortedBy { it.diagramId() }.mapIndexed() { i, it ->
            val diagramContent = RootNodeBuildContext(this, it).buildDiagram(diagramCreation)
            val diagramFileName = fileNamingPattern(it, i)

            diagramFileName to diagramContent.replace("@startuml", "@startuml\n\n$metaDataSection\n\n")
        }
    }

}

class CodeDiagramConfig(
        val diagramDirection: DiagramDirection,
        val nodeAggregation: NodeAggregation,
        val nodeGrouping: NodeGrouping,
        val wrapMethodsWithItsClass: Boolean,

        var forwardDepth: Int? = null,
        var backwardDepth: Int? = null,
        var edgeMode: EdgeMode
)
