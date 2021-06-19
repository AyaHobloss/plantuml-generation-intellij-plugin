package com.kn.diagrams.generator.generator.code

import com.kn.diagrams.generator.builder.*
import com.kn.diagrams.generator.cast
import com.kn.diagrams.generator.config.NodeAggregation
import com.kn.diagrams.generator.config.NodeGrouping
import com.kn.diagrams.generator.generator.*
import com.kn.diagrams.generator.generator.vcs.staticColor
import com.kn.diagrams.generator.graph.*
import com.kn.diagrams.generator.toSingleList
import java.util.stream.Collectors
import java.util.stream.Stream

class RootNodeBuildContext(val context: CodeStructureAnalysis,
                           val root: GraphNode){

    val config get() = context.config
    val cache get() = context.cache
    val visualizationConfig get() = context.visualizationConfig

    val dot = DotDiagramBuilder().apply { direction = config.diagramDirection }

    var methodNodes: Set<AnalyzeMethod>
    var classNodes: Set<AnalyzeClass>
    var edges = mutableListOf<SquashedGraphEdge>()

    private val groupHierarchyCluster = DotHierarchicalGroupCluster() { _, packageCluster, color, isLast ->
        packageCluster.config.style = "filled"
        if(isLast && config.wrapMethodsWithItsClass){
            packageCluster.config.fillColor = "white"
        }else{
            packageCluster.config.fillColor = "#" + color.toHex()
        }
    }.apply { dot.nodes.add(this) }

    init {
        edges.addAll(searchEdges())

        val types = edges.parallelStream()
                .flatMap { Stream.of(it.from(), it.to()) }
                .map { it.aggregateIfNeeded() }
                .collect(Collectors.groupingByConcurrent { it?.javaClass })

        when(val rootNode = root.aggregateIfNeeded()){
            is AnalyzeClass -> types.getOrPut(AnalyzeClass::class.java.cast(), { mutableListOf() })?.add(rootNode)
            is AnalyzeMethod -> types.getOrPut(AnalyzeMethod::class.java.cast(), { mutableListOf() })?.add(rootNode)
        }

        classNodes = types[AnalyzeClass::class.java.cast()]?.toSet().cast<Set<AnalyzeClass>>() ?: emptySet()
        methodNodes = types[AnalyzeMethod::class.java.cast()]?.toSet().cast<Set<AnalyzeMethod>>() ?: emptySet()
    }

    fun buildDiagram(actions: RootNodeBuildContext.() -> Unit): String {
        actions(this)

        return dot.create()
    }

    fun AnalyzeMethod.rootNodePenWidth() = if(this == root) 4 else null
    fun AnalyzeMethod.signature() = signature(visualizationConfig)

    fun AnalyzeMethod.visibilityOrStructureBasedColor(): String {
        return when(config.methodColorCoding){
            StructureColorCoding.Component -> containingClass.diagramPath(visualizationConfig).staticColor().toHex("#")
            StructureColorCoding.Layer -> containingClass.layer(visualizationConfig).color
            StructureColorCoding.None -> "white"
        }
    }
    fun AnalyzeClass.structureBasedColor(): String {
        return when(config.classColorCoding){
            StructureColorCoding.Component -> reference.diagramPath(visualizationConfig).staticColor().toHex("#")
            StructureColorCoding.Layer -> reference.layer(visualizationConfig).color
            StructureColorCoding.None -> "white"
        }
    }

    private fun GraphNode?.aggregateIfNeeded(): GraphNode? {
        return if(config.nodeAggregation == NodeAggregation.Class && this is AnalyzeMethod) {
            cache.classFor(containingClass)
        }else {
            this
        }
    }

    fun AnalyzeMethod.grouping(): DotCluster {
        return containingClass.grouping(config.wrapMethodsWithItsClass)
    }

    fun ClassReference.grouping(classWrapper: Boolean = false): DotCluster {
        if(config.nodeGrouping == NodeGrouping.None) return groupHierarchyCluster

        var group = group(config.nodeGrouping, visualizationConfig)

        if(classWrapper){
            group = Grouping(name, group.path.takeIf { it != "" }?.let { "$it.${group.name}" } ?: group.name)
        }

        // TODO inline in pathCluster
        return groupHierarchyCluster.groupCluster(group)
    }

    fun AnalyzeClass.grouping() = reference.grouping()

    fun searchEdges(): List<SquashedGraphEdge> {
        return cache.search(context.traversalFilter) {
            roots = root.toSingleList()
            forwardDepth = config.forwardDepth
            backwardDepth = config.backwardDepth
            edgeMode = config.edgeMode
        }.flatten()
    }

    fun SquashedGraphEdge.createLinksPerContext(styling: DotLinkConfig.(EdgeContext, Boolean) -> Unit): List<DotEdge>{
        val hasMoreEdges = edges().size > 1
        val relevantEdge = if (direction == Direction.Forward) edges().last() else edges().first()

        return relevantEdge.context
                .mapNotNull { context ->
                    val (from, to) = fromAndTo(context, this)

                    if (from == to && edges().flatMap { it.context }.any { it is InheritanceType }) {
                        null
                    }else{
                        DotLink(from.aggregateIfNeeded()!!.diagramId(), to.aggregateIfNeeded()!!.diagramId())
                                .with{ styling(this, context, hasMoreEdges) }
                    }
                }
    }
}
