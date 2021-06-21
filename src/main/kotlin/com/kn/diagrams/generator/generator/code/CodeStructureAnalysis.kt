package com.kn.diagrams.generator.generator.code

import com.intellij.openapi.project.Project
import com.kn.diagrams.generator.builder.DiagramDirection
import com.kn.diagrams.generator.cast
import com.kn.diagrams.generator.config.*
import com.kn.diagrams.generator.generator.DiagramVisualizationConfiguration
import com.kn.diagrams.generator.generator.diagramId
import com.kn.diagrams.generator.generator.toHex
import com.kn.diagrams.generator.generator.vcs.Layer
import com.kn.diagrams.generator.generator.vcs.staticColor
import com.kn.diagrams.generator.generator.visualizationConfig
import com.kn.diagrams.generator.graph.*
import com.kn.diagrams.generator.inReadAction
import com.kn.diagrams.generator.notifications.notifyErrorMissingPublicMethod

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

    constructor(diagramConfig: CallConfiguration, _project: Project) {
        fileNamingPattern = { method, i -> "${i}_${ method.cast<AnalyzeMethod>()?.name }_calls" }

        project = _project
        restrictionFilter = diagramConfig.restrictionFilter()

        cache = analysisCache.getOrCompute(project, restrictionFilter, diagramConfig.projectClassification.searchMode)

        traversalFilter = diagramConfig.traversalFilter()
        visualizationConfig = diagramConfig.visualizationConfig(cache)

        with(diagramConfig){
            config = CodeDiagramConfig(
                    DiagramDirection.LeftToRight,
                    details.nodeAggregation,
                    details.nodeGrouping,
                    details.wrapMethodsWithItsClass,
                    graphTraversal.forwardDepth,
                    graphTraversal.backwardDepth,
                    details.edgeMode,
                    details.methodColorCoding,
                    details.classColorCoding
            )
        }
        metaDataSection = diagramConfig.metaDataSection()

        rootNodes = when(val node = visualizationConfig.rootNode){
            is AnalyzeMethod -> listOf(node)
            is AnalyzeClass -> node.methods.values.filter { it.visibility == MethodVisibility.PUBLIC }
            else -> emptyList()
        }

        if (rootNodes.isEmpty()) {
            notifyErrorMissingPublicMethod(inReadAction { project }, diagramConfig.rootClass, diagramConfig.rootMethod)
        }
    }

    constructor(diagramConfig: StructureConfiguration, _project: Project) {
        fileNamingPattern = { _, _ -> "structure" }

        project = _project
        restrictionFilter = diagramConfig.restrictionFilter()

        cache = analysisCache.getOrCompute(project, restrictionFilter, diagramConfig.projectClassification.searchMode)

        traversalFilter = diagramConfig.traversalFilter()
        visualizationConfig = diagramConfig.visualizationConfig()

        with(diagramConfig){
            config = CodeDiagramConfig(
                    DiagramDirection.TopToBottom,
                    details.nodeAggregation,
                    details.nodeGrouping,
                    false,
                    graphTraversal.forwardDepth,
                    graphTraversal.backwardDepth,
                    EdgeMode.TypesOnly,
                    details.methodColorCoding,
                    details.classColorCoding

            )
        }
        metaDataSection = diagramConfig.metaDataSection()

        rootNodes = listOfNotNull(inReadAction { cache.classFor(diagramConfig.rootClass.psiClassFromQualifiedName(project)) })

        if (rootNodes.isEmpty()) {
            notifyErrorMissingPublicMethod(inReadAction { project }, diagramConfig.rootClass, null)
        }
    }


    fun buildDiagrams(builder: RootNodeBuildContext.() -> Unit): List<Pair<String, String>> {
        return perRootNode(builder)
    }

    private fun perRootNode(diagramCreation: RootNodeBuildContext.() -> Unit): List<Pair<String, String>>{
        return rootNodes.sortedBy { it.diagramId() }.mapIndexed() { i, it ->
            val diagramContent = RootNodeBuildContext(this, it).buildDiagram(diagramCreation)
            val diagramFileName = fileNamingPattern(it, i)

            diagramFileName to diagramContent.replace("@startuml", "@startuml\n\n$metaDataSection\n\n")
        }
    }

}

enum class StructureColorCoding {
    None, Component, Layer
}

class CodeDiagramConfig(
        val diagramDirection: DiagramDirection,
        val nodeAggregation: NodeAggregation,
        val nodeGrouping: NodeGrouping,
        val wrapMethodsWithItsClass: Boolean,

        var forwardDepth: Int? = null,
        var backwardDepth: Int? = null,
        var edgeMode: EdgeMode,

        var methodColorCoding: StructureColorCoding = StructureColorCoding.None,
        var classColorCoding: StructureColorCoding = StructureColorCoding.None
)

fun ClassReference.layer(visualizationConfiguration: DiagramVisualizationConfiguration) = layer(visualizationConfiguration.projectClassification)
fun ClassReference.layer(classification: ProjectClassification): Layer {

    with(classification) {
        val customLayer = customLayers.entries
                .firstOrNull { (_, pattern) -> included(pattern.name, pattern.path) }
                ?.let { Layer(it.key, it.value.color ?: it.key.staticColor().toHex("#")) }
        val layer = customLayer ?: sequenceOf<(ClassReference) -> Layer?>(
                { cls -> defaultLayer(cls.isTest(), "Test", "#3498db") },
                { cls -> defaultLayer(cls.isInterfaceStructure(), "Interface Structure", "#12CBC4") },
                { cls -> defaultLayer(cls.isDataStructure(), "Data Structure", "#d1ccc0") },
                { cls -> defaultLayer(cls.isClient(), "Client", "#badc58") },
                { cls -> defaultLayer(cls.isDataAccess(), "Data Access", "#ffda79") },
                { cls -> defaultLayer(cls.isEntryPoint(), "Entry Point", "#6ab04c") },
                { cls -> defaultLayer(cls.isMapping(), "Mapping", "#9c88ff") },
        ).mapNotNull { it(this@layer) }.firstOrNull()

        return layer ?: Layer("No Layer", "#F79F1F" )
    }

}


fun defaultLayer(condition: Boolean, name: String, color: String): Layer? {
    return if(condition) Layer(name, color) else null
}
