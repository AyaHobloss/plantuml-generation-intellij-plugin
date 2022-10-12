package com.kn.diagrams.generator.generator.code


import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.castSafelyTo
import com.kn.diagrams.generator.actions.ActionContext
import com.kn.diagrams.generator.builder.DiagramDirection
import com.kn.diagrams.generator.builder.DotDiagramBuilder
import com.kn.diagrams.generator.builder.DotHTMLShape
import com.kn.diagrams.generator.builder.addLink
import com.kn.diagrams.generator.config.*
import com.kn.diagrams.generator.escapeHTML
import com.kn.diagrams.generator.generator.*
import com.kn.diagrams.generator.graph.*
import com.kn.diagrams.generator.notReachable
import java.util.*
import kotlin.math.min

fun ActionContext.createGeneticsClusterContext(init: GeneticsClusterDiagramContext.() -> Unit):

        GeneticsClusterDiagramContext = when(config<GeneticsConfiguration>().details.visualization ) {
    GeneticsClusterVisualization.NodesSimplified, GeneticsClusterVisualization.Nodes -> NodeBuildingContextGenetics(this, init)
    GeneticsClusterVisualization.Cluster, GeneticsClusterVisualization.ClusterWithoutDetails -> GeneticsClusterBuildingContext(this, init)

}

class NodeBuildingContextGenetics(actionContext: ActionContext, init: GeneticsClusterDiagramContext.() -> Unit): GeneticsClusterDiagramContext(actionContext, init){

    val nodes: List<Any> = baseEdgesGenetics
        .flatMap { it.nodes() }
        .map { it.aggregateGenetics() }
        .distinct()
    val edges get() = baseEdgesGenetics
    val stats = baseEdgesGenetics.groupBy { setOfNotNull(it.from()?.diagramId(), it.to()?.diagramId()) }

    val pathCluster = DotHierarchicalGroupCluster { _, packageCluster, color, _ ->
        packageCluster.config.style = "filled"
        packageCluster.config.fillColor = "#" + color.toHex()
    }.apply { dot.nodes.add(this) }


    fun Any.grouping() = pathCluster.groupCluster(Grouping(geneticsCluster()))
}

class GeneticsClusterBuildingContext(actionContext: ActionContext, init: GeneticsClusterDiagramContext.() -> Unit): GeneticsClusterDiagramContext(actionContext, init){

    val nodes = baseEdgesGenetics.flatMap { listOf(it.from()!! to it, it.to()!! to it) }
        .map { (node, edge) -> node.aggregateGenetics() to edge }
        .groupBy { (node, _) -> node.geneticsCluster() }
        .entries.map { GeneticsClusterNode(this, it) }
    val edges: List<GeneticsClusterEdge>

    init {
        val clustersToEdges: Map<String, List<SquashedGraphEdge>> = baseEdgesGenetics
            .filter { it.from()!!.geneticsCluster() != it.to()!!.geneticsCluster() }
            .groupBy { it.from()!!.geneticsCluster() + "_" + it.to()!!.geneticsCluster() }

        // TODO by config?!
        val eliminationMatrix = if(configGenetics.details.removedTransientDependencies) {
            // TODO mark inverted arrows separate? - config??
            val directDependencies = clustersToEdges.entries.asSequence()
                .filter { !it.value.all { it.isInvertedGenetics(geneticsCache, geneticsVisualConfig) } }
                .map { it.key }
                .map { it.split("_") }.map { (from, to) -> from to to }
                .groupBy { (from, _) -> from }
                .mapValues { it.value.map { (_, to) -> to } }

            directDependencies.transientEliminationMatrixGenetics()
        } else emptyMap()

        val allEdges = mutableListOf<GeneticsClusterEdge>()
        clustersToEdges.entries
            .filterNot { (dependency, _) -> eliminationMatrix.isReplacedGenetics(dependency) }
            .forEach { (dependency, calls) ->
                val from = calls.first().from()!!.geneticsCluster()
                val to = calls.first().to()!!.geneticsCluster()

                val allCalls = calls + eliminationMatrix.replacedDependenciesForGenetics(dependency).flatMap { clustersToEdges[it] ?: emptyList()}

                val invertedDependencies = if(configGenetics.details.showInvertedDependenciesExplicitly) {
                    calls.filter { edge ->
                        edge.isInvertedGenetics(geneticsCache, geneticsVisualConfig)
                    }.filter { it in calls }
                } else emptyList()


                if (invertedDependencies.isNotEmpty()) {
                    allEdges += GeneticsClusterEdge(to, from, invertedDependencies.distinctBy { it.from() to it.to() }, true)
                }

                val withoutInverted = allCalls - invertedDependencies
                if (withoutInverted.isNotEmpty()) {
                    allEdges += GeneticsClusterEdge(from, to, withoutInverted.distinctBy { it.from() to it.to() }, false)
                }
            }

        allEdges.toList().also { this.edges = it }
    }

    fun forEachNodeGenetics(actions: GeneticsClusterNode.() -> Unit){
        nodes.forEach { actions(it) }
    }
    fun forEachEdgeGenetics(actions: GeneticsClusterEdge.() -> Unit){
        edges.forEach { actions(it) }
    }
}
class GeneticsClusterEdge(val from: String, val to: String, val calls: List<SquashedGraphEdge>, val inverted: Boolean)
class GeneticsClusterNode(context: GeneticsClusterBuildingContext, entry: Map.Entry<String, List<Pair<Any, SquashedGraphEdge>>>){
    val cluster = entry.key
    val links = entry.value
    val showDetailed = context.configGenetics.details.visualization == GeneticsClusterVisualization.Cluster

    val serviceClasses: List<Any>
    val dataClasses: List<Any>
    val internal: Int
    val outgoing: List<Pair<GraphNode, GraphNode>>
    val outgoingClusters: List<String>
    val ingoing: List<Pair<GraphNode, GraphNode>>
    val ingoingClusters: List<String>
    val packageNames: List<String>

    init {
        with(context){
            with(context.geneticsVisualConfig.projectClassification) {
                val distinctUsages = links.map { it.second }.map { it.from()!! to it.to()!! }.distinct()
                ingoing = distinctUsages.filter { (from, to) -> from.geneticsCluster() != cluster && to.geneticsCluster() == cluster }
                ingoingClusters = ingoing.map { (from, _) -> from.geneticsCluster() }.distinct()

                outgoing = distinctUsages.filter { (from, to) -> from.geneticsCluster() == cluster && to.geneticsCluster() != cluster }
                outgoingClusters = outgoing.map { (_, to) -> to.geneticsCluster() }.distinct()

                internal = distinctUsages.filter { (from, to) -> from.geneticsCluster() == cluster && to.geneticsCluster() == cluster }.size
                dataClasses = links.map { it.first }.filter { it.containingClass().isDataStructure() || it.containingClass().isInterfaceStructure() }.distinct()
                serviceClasses = links.map { it.first }.filterNot { it.containingClass().isDataStructure() }.distinct()

                packageNames = dataClasses
                    .map { it.containingClass().diagramPath(context.geneticsVisualConfig) }
                    .distinct()
            }
        }
    }

}



open class GeneticsClusterDiagramContext(actionContext: ActionContext, init: GeneticsClusterDiagramContext.() -> Unit){
    val project: Project = actionContext.project

    val configGenetics = actionContext.config<GeneticsConfiguration>()

    val dot = DotDiagramBuilder().apply { direction = DiagramDirection.LeftToRight }


    val geneticsVisualConfig = configGenetics.visualizationGeneticsConfig()


    val geneticsCache=analysisCache.getOrCompute(project, configGenetics.restrictionFilter(), configGenetics.projectClassification.searchMode)

    val geneticsRootNodes = geneticsCache.nodesForGeneticsClustering(configGenetics.traversalFilter(), configGenetics.details)

    val baseEdgesGenetics: MutableList<SquashedGraphEdge> = mutableListOf()
    val geneticsClusterDefinition = GeneticsClusterDefinition()


    init {
        init(this)
    }

    fun searchEdgesBySelectedNodesGenetics(): GeneticsClusterDiagramContext {
        baseEdgesGenetics += geneticsCache.search(configGenetics.traversalFilter()) {
            roots = geneticsRootNodes
            forwardDepth = configGenetics.graphTraversal.forwardDepth
            backwardDepth = configGenetics.graphTraversal.backwardDepth
            edgeMode = configGenetics.details.edgeMode
        }.flatten().distinct()

        // TODO
//        aggrgatedNodes += baseEdges.flatMap { it.nodes() }.map { it.nameInCluster(config.details.nodeAggregation) }.distinct()
//        aggrgatedEdges += baseEdges.asSequence()
//                .flatMap { sequenceOf(nodes.indexOf(it.from()!!.nameInCluster(aggregation)) to nodes.indexOf(it.to()!!.nameInCluster(aggregation)),
//                        nodes.indexOf(it.to()!!.nameInCluster(aggregation)) to nodes.indexOf(it.from()!!.nameInCluster(aggregation))) }
        return this
    }

    fun List<GraphNode>.clusterToGenetics(clusterName: String) = GeneticsClusterDefinition(associate { it.nameInClusterGenetics() to clusterName })

    // TODO SimplifiedNodes have a lot of missing edges
    // TODO cluster definition is empty when optimization is used?!
    private fun GraphNode.geneticsCluster() = geneticsClusterDefinition.geneticsCluster(nameInClusterGenetics()) ?: notReachable()
    private fun ClassReference.geneticsCluster() = geneticsClusterDefinition.geneticsCluster(this.name) ?: notReachable()
    fun Any.geneticsCluster() = when(this){
        is GraphNode -> geneticsCluster()
        is ClassReference -> geneticsCluster()
        else -> notReachable()
    }


     fun geneticsClustering(geneticsClustering: GeneticsClusterDiagramContext.(ClusterSourceGenetics) -> GeneticsClusterDefinition): GeneticsClusterDiagramContext {
         geneticsClusterDefinition += geneticsClustering(configGenetics.details.clusteringAlgorithm)
         return this
     }




    fun Geneticsbuild() = listOf("GeneticDiagram.puml" to dot.create().attacheMetaData(configGenetics))

    fun buildNodeBasedDiagramGenetics(actions: NodeBuildingContextGenetics.() -> Unit): GeneticsClusterDiagramContext {
        ProgressManager.getGlobalProgressIndicator()?.text = "Diagram is generated"
        this.castSafelyTo<NodeBuildingContextGenetics>()?.let(actions)
        return this
    }

    fun buildClusterAggregatedDiagramGenetics(actions: GeneticsClusterBuildingContext.() -> Unit): GeneticsClusterDiagramContext {
        ProgressManager.getGlobalProgressIndicator()?.text = "Diagram is generated"
        this.castSafelyTo<GeneticsClusterBuildingContext>()?.let(actions)
        return this
    }

   fun Any.nameInClusterGenetics() = when(this) {
        is AnalyzeClass -> reference.name
        is AnalyzeMethod -> if(configGenetics.details.nodeAggregation == GeneticsClusterAggregation.Class) containingClass.name else id.replace("...", "").substringAfterLast(".")
        is ClassReference -> name
        else -> notReachable()
    }

    fun loadPackageClustersGenetics(): GeneticsClusterDefinition {
        val clusters = baseEdgesGenetics
            .flatMap { listOf(it.from()!! to it, it.to()!! to it) }
            .map { (node, edge) -> node.aggregateGenetics() to edge }
            .groupBy { (node, _) -> node.containingClass().diagramPath(geneticsVisualConfig) }
            .flatMap { (cluster, links) ->
                links.map { (node, _) ->
                    node.nameInClusterGenetics() to cluster
                }
            }.toMap()

        return GeneticsClusterDefinition(clusters)
    }

    fun loadLayerClustersGenetics(): GeneticsClusterDefinition {
        val clusters = baseEdgesGenetics
            .flatMap { listOf(it.from()!! to it, it.to()!! to it) }
            .map { (node, edge) -> node.aggregateGenetics() to edge }
            .groupBy { (node, _) -> node.containingClass().layer(configGenetics.projectClassification).name }
            .flatMap { (cluster, links) ->
                links.map { (node, _) ->
                    node.nameInClusterGenetics() to cluster
                }
            }.toMap()

        return GeneticsClusterDefinition(clusters)
    }

    fun GraphNode.aggregateGenetics(): Any {
        return when(configGenetics.details.nodeAggregation){
            GeneticsClusterAggregation.Class -> containingClass()
            GeneticsClusterAggregation.None -> this
        }
    }
}

fun NodeBuildingContextGenetics.visualizeGroupedByClustersSimplifiedGenetics() {
    // TODO check visualization in diagram to generify
    val classUsages = edges.flatMap { edge ->
        edge.nodes()
            .filterIsInstance<AnalyzeClass>()
            .map { cls -> cls to edge.nodes().map { it.geneticsCluster() } }.distinct()
    }.groupBy { it.first }
        .mapValues { it.value.flatMap { it.second }.distinct().sorted().joinToString(", ") }

    classUsages.entries.groupBy { it.value }
        .mapValues { it.value.map { it.key } }
        .forEach { (sameClusterName, classes) ->

            val table = DotHTMLShape(sameClusterName, "\"$sameClusterName\"")
                .with {
                    this.config.fillColor = "#FFFFFF"
                    this.config.style = "filled"
                }
                .withTable {
                    border = 0
                    val groups = classes
                        .groupBy { it.geneticsCluster()!! }
                        .mapValues {
                            it.value
                                .map { it.shortNameGenetics() }.sorted()
                                .mapIndexed { i, str -> if (i % 2 == 0 && i > 0) "<BR/>$str" else str }
                                .joinToString(", ")
                        }
                        .entries

                    row {
                        if(sameClusterName.contains(",")){
                            if(groups.size > 1) {
                                cell("common usage for: $sameClusterName", colspan = 2)
                            } else{
                                val firstGroup = groups.first().key
                                cell("common usage for: ${sameClusterName.replace(firstGroup, "<B>$firstGroup</B>")}")
                            }
                        }else{
                            cell("data structures")
                        }
                    }

                    groups.sortedBy { it.key }.forEach { (cluster, classes) ->
                        row {
                            if(groups.size > 1) {
                                cell(cluster)
                            }
                            cell(classes)
                        }
                    }
                }

            if(sameClusterName.contains(",")){
                dot.nodes.add(table)
            }else{
                // TODO check
                pathCluster.addNode(table, Grouping(sameClusterName))
            }

        }

    edges.flatMap { it.nodes().filterIsInstance<AnalyzeMethod>() }
        .groupBy { it.containingClass to it.geneticsCluster() }
        .forEach { (group, methods) ->
            val (cls, cluster) = group
            pathCluster.addNode(DotHTMLShape(cls.displayName, cls.diagramId())
                .with {
                    this.config.fillColor = "#FFFFFF"
                    this.config.style = "filled"
                }
                .withTable {
                    cellPadding = 6
                    row {
                        cell("<B>${cls.displayName.escapeHTML()}</B>")
                    }
                    methods.distinct().sortedBy { it.name }.forEach { method ->
                        row {
                            cell(method.signature(geneticsVisualConfig))
                        }
                    }
                }, Grouping(cluster ?: "missing cluster name"))
        }

    edges.groupBy { edge ->
        val from = edge.from()!!
        val to = edge.to()!!

        // TODO check warnings
        if(from.geneticsCluster() == to.geneticsCluster() && classUsages[from.containingClass()]?.contains(",") != true && classUsages[to.containingClass()]?.contains(",") != true){
            null
        }else{

            val fromId = if(from is AnalyzeClass) "\""+classUsages[from]!!+"\"" else from.containingClass().diagramId()
            val toId = if(to is AnalyzeClass) "\""+classUsages[to]!!+"\"" else to.containingClass().diagramId()

            fromId to toId
        }

    }
        .filter { it.key != null }
        .filter { it.key!!.first != it.key!!.second }
        .forEach { (key, clusterEdges) ->
            val (from, to) = key!!
            dot.addLink(from, to){
                tooltip = clusterEdges.allCallsGenetics()
            }
        }

}






fun GraphDefinition.nodesForGeneticsClustering(filter: GraphTraversalFilter, details: GeneticsDiagramDetails): List<GraphNode> {



    with(filter.global){
        val validClasses = classes.values
            .filter { it.reference.included(details.nodeSelectionG.className, details.nodeSelectionG.classPackage) }
            .filter { filter.accept(it) }

        val classNodes = validClasses
            .filter { (it.reference.isDataStructure() || it.reference.isInterfaceStructure()) }

        val methodNodes = validClasses.flatMap { cls ->
            cls.methods.values
                .filter { it.name.included(details.nodeSelectionG.methodName) }
                .filter { !it.containingClass.isDataStructure() && !it.containingClass.isInterfaceStructure() && filter.accept(it) }
        }

        return methodNodes + classNodes
    }
}

class GeneticsClusterDefinition(existingMapping: Map<String, String> = emptyMap()) {

    private val mappingGenetics: MutableMap<String, String> = existingMapping.toMutableMap()

    operator fun plusAssign(other: GeneticsClusterDefinition){
        mappingGenetics += other.mappingGenetics
    }

    fun geneticsCluster(ofClassName: String?): String? {
        if (ofClassName == null) return null

        return mappingGenetics[ofClassName]
    }

}


fun GeneticsClusterNode.toClusterDotNodeGenetics(): DotHTMLShape {
    return DotHTMLShape("$cluster-IN-$ingoing-OUT$outgoing-INT$internal-CLS$dataClasses", cluster).with {
        this.table.cellPadding = 5

        withTable {
            row {
                if(showDetailed) { cell("Cluster") }
                cell(cluster)
            }
            if(showDetailed) {
                horizontalSeparator()
                row {
                    cell("Ingoing Calls")
                    cell( ingoing.size.toString() + "(${ ingoingClusters.size } clusters)")
                }
                row {
                    cell("Outgoing Calls")
                    cell(outgoing.size.toString() + "(${ outgoingClusters.size } clusters)")
                }
                row {
                    cell("Internal Calls")
                    cell(internal.toString())
                }
                row {
                    cell("Packages")
                    cell(packageNames.joinToString(", ").breakAfterGenetics(chars = 120))
                }
                row {
                    cell("Data Classes (${dataClasses.size})")
                    cell(dataClasses.clusterElementsGenetics().breakEveryGenetics(elements = 2))
                }
                row {
                    cell("Service Classes (${serviceClasses.size})")
                    cell(serviceClasses.clusterElementsGenetics().breakEveryGenetics(elements = 2))
                }
            }
        }
    }
}
fun Map<String, MutableList<String>>.isReplacedGenetics(dependency: String) = values.any { it.contains(dependency) }

fun Map<String, MutableList<String>>.replacedDependenciesForGenetics(dependency: String): List<String> {
    val result = mutableListOf<String>()
    val processed = mutableSetOf(dependency)
    val search = Stack<String>()
    search.push(dependency)

    while (search.isNotEmpty()){
        val current = search.pop()
        processed.add(current)

        val newElements = get(current) ?: mutableListOf()
        result.addAll(newElements)
        search.addAll(newElements)
    }

    return result.toList()
}




private fun SquashedGraphEdge.isInvertedGenetics(
    graph: GraphDefinition,
    geneticsVisualConfig: DiagramVisualizationConfiguration
): Boolean {
    val last = edges().last()
    if(last.from is AnalyzeClass || last.to is AnalyzeClass) return false

    val clsFrom = last.from.containingClass()
    val clsTo = graph.classes[last.to.containingClass().id()]!!

    val inherited = clsTo.superTypes.any { it == clsFrom } || clsTo.superTypes.flatMap { graph.classes[it.id()]?.superTypes ?: emptyList() }.any{ it == clsFrom }
    val otherPackage =
        clsFrom.diagramPath(geneticsVisualConfig) != clsTo.containingClass().diagramPath(geneticsVisualConfig)

    return inherited && otherPackage
}

fun Map<String, List<String>>.transientEliminationMatrixGenetics(): Map<String, MutableList<String>> {
    val dependencies = entries.flatMap { entry -> entry.value.map { entry.key to it } }

    val result = mutableMapOf<String, MutableList<String>>()

    val directOnlyDependencies = dependencies.filter { (from, to) -> get(from)!!.any { it == to } && !hasPathGenetics(from, to, indirect = true) }

    directOnlyDependencies.forEach { (from, to) ->
        dependencies.asSequence()
            .filter { (otherFrom, otherTo) -> result.none { it.value.contains("${otherFrom}_$otherTo") } }
            .filter { (otherFrom, _) -> hasPathGenetics(otherFrom, from, indirect = false) }
            .filter { (_, otherTo) -> otherTo == to }
            .forEach { (otherFrom, otherTo) ->
                val responsibleCluster = "${from}_$to"
                val replacedCluster = "${otherFrom}_$otherTo"

                result.getOrPut(responsibleCluster){ mutableListOf() }.add(replacedCluster)
            }
    }

    return result.toMap()
}

fun Map<String, List<String>>.hasPathGenetics(from: String, to: String, indirect: Boolean): Boolean{
    val processed = mutableSetOf(from)
    val stack = Stack<String>()

    get(from)?.filter { !indirect || it != to }?.forEach { stack.push(it) }

    while (stack.isNotEmpty()){
        val current = stack.pop()
        processed.add(current)

        if(current == to) return true

        get(current)?.filterNot { it in processed }?.forEach { stack.push(it) }
    }

    return false
}


fun List<Any>.clusterElementsGenetics() = this
    .groupBy { it.containingClass() }.entries
    .map { (cls, values) -> cls.name + (values
        .filterIsInstance<AnalyzeMethod>()
        .takeIf { it.isNotEmpty() }
        ?.joinToString { it.name+"(${it.parameter.size})" }
        ?.let { "[$it]" }
        ?: "")
    }

fun String.breakAfterGenetics(chars: Int) = chunked(chars)
    .joinToString("") { "$it<BR ALIGN=\"LEFT\"/>" }

fun List<String>.breakEveryGenetics(elements: Int) = mapIndexed { i, cls -> if (i % elements == 1) ", $cls,<BR ALIGN=\"LEFT\"/>" else cls }
    .joinToString("") + "<BR ALIGN=\"LEFT\"/>"

fun List<SquashedGraphEdge>.allCallsGenetics(hidden: Boolean = false) = this
    .map{ it.allCallsGenetics(hidden) }
    .distinct().sorted()
    .joinToString("&#013;")
    .run{ substring(0, min(16_300, length)) }

fun SquashedGraphEdge.allCallsGenetics(hidden: Boolean = false) = "${from().shortNameGenetics()} -> ${to().shortNameGenetics()}" + if (hidden) {
    " ### " + edges().flatMap { listOf(it.from, it.to) }.distinct().joinToString("=>") { it.shortNameGenetics() }
} else ""

fun GraphNode?.shortNameGenetics() = when (this) {
    null -> "nothing"
    is AnalyzeClass -> reference.name
    is AnalyzeMethod -> containingClass.name + "." + name
    else -> notReachable()
}

