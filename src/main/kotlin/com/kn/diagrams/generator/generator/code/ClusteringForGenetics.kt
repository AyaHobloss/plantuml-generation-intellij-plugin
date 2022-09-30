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

fun ActionContext.createGeneticsClusterContext(init: GeneticsClusterDiagramContext.() -> Unit):

        GeneticsClusterDiagramContext = when(config<GeneticsConfiguration>().details.visualization ) {
    GeneticsClusterVisualization.NodesSimplified, GeneticsClusterVisualization.Nodes -> NodeBuildingContextGenetics(this, init)
    GeneticsClusterVisualization.Cluster, GeneticsClusterVisualization.ClusterWithoutDetails -> GeneticsClusterBuildingContext(this, init)

}

class NodeBuildingContextGenetics(actionContext: ActionContext, init: GeneticsClusterDiagramContext.() -> Unit): GeneticsClusterDiagramContext(actionContext, init){

    val nodes: List<Any> = baseEdges
        .flatMap { it.nodes() }
        .map { it.aggregate() }
        .distinct()
    val edges get() = baseEdges
    val stats = baseEdges.groupBy { setOfNotNull(it.from()?.diagramId(), it.to()?.diagramId()) }

    val pathCluster = DotHierarchicalGroupCluster { _, packageCluster, color, _ ->
        packageCluster.config.style = "filled"
        packageCluster.config.fillColor = "#" + color.toHex()
    }.apply { dot.nodes.add(this) }


    fun Any.grouping() = pathCluster.groupCluster(Grouping(cluster()))
}

class GeneticsClusterBuildingContext(actionContext: ActionContext, init: GeneticsClusterDiagramContext.() -> Unit): GeneticsClusterDiagramContext(actionContext, init){

    val nodes = baseEdges.flatMap { listOf(it.from()!! to it, it.to()!! to it) }
        .map { (node, edge) -> node.aggregate() to edge }
        .groupBy { (node, _) -> node.cluster() }
        .entries.map { GeneticsClusterNode(this, it) }
    val edges: List<ClusterEdge>

    init {
        val clustersToEdges = baseEdges
            .filter { it.from()!!.cluster() != it.to()!!.cluster() }
            .groupBy { it.from()!!.cluster() + "_" + it.to()!!.cluster() }

        // TODO by config?!
        val eliminationMatrix = if(configGenetics.details.removedTransientDependencies) {
            // TODO mark inverted arrows separate? - config??
            val directDependencies = clustersToEdges.entries.asSequence()
                .filter { !it.value.all { it.isInverted(geneticsCache, geneticsVisualConfig) } }
                .map { it.key }
                .map { it.split("_") }.map { (from, to) -> from to to }
                .groupBy { (from, _) -> from }
                .mapValues { it.value.map { (_, to) -> to } }

            directDependencies.transientEliminationMatrix()
        } else emptyMap()

        val allEdges = mutableListOf<ClusterEdge>()
        clustersToEdges.entries
            .filterNot { (dependency, _) -> eliminationMatrix.isReplaced(dependency) }
            .forEach { (dependency, calls) ->
                val from = calls.first().from()!!.cluster()
                val to = calls.first().to()!!.cluster()

                val allCalls = calls + eliminationMatrix.replacedDependenciesFor(dependency).flatMap { clustersToEdges[it] ?: emptyList()}

                val invertedDependencies = if(configGenetics.details.showInvertedDependenciesExplicitly) {
                    calls.filter { edge ->
                        edge.isInverted(geneticsCache, geneticsVisualConfig)
                    }.filter { it in calls }
                } else emptyList()


                if (invertedDependencies.isNotEmpty()) {
                    allEdges += ClusterEdge(to, from, invertedDependencies.distinctBy { it.from() to it.to() }, true)
                }

                val withoutInverted = allCalls - invertedDependencies
                if (withoutInverted.isNotEmpty()) {
                    allEdges += ClusterEdge(from, to, withoutInverted.distinctBy { it.from() to it.to() }, false)
                }
            }

        edges = allEdges.toList()
    }

    fun forEachNode(actions: GeneticsClusterNode.() -> Unit){
        nodes.forEach { actions(it) }
    }
    fun forEachEdge(actions: ClusterEdge.() -> Unit){
        edges.forEach { actions(it) }
    }
}

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
                ingoing = distinctUsages.filter { (from, to) -> from.cluster() != cluster && to.cluster() == cluster }
                ingoingClusters = ingoing.map { (from, _) -> from.cluster() }.distinct()

                outgoing = distinctUsages.filter { (from, to) -> from.cluster() == cluster && to.cluster() != cluster }
                outgoingClusters = outgoing.map { (_, to) -> to.cluster() }.distinct()

                internal = distinctUsages.filter { (from, to) -> from.cluster() == cluster && to.cluster() == cluster }.size
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

    val baseEdges: MutableList<SquashedGraphEdge> = mutableListOf()
    val clusterDefinition = ClusterDefinition()

    init {
        init(this)
    }

    fun searchEdgesBySelectedNodes(): GeneticsClusterDiagramContext {
        baseEdges += geneticsCache.search(configGenetics.traversalFilter()) {
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

    fun List<GraphNode>.clusterTo(clusterName: String) = ClusterDefinition(associate { it.nameInCluster() to clusterName })

    // TODO SimplifiedNodes have a lot of missing edges
    // TODO cluster definition is empty when optimization is used?!
    private fun GraphNode.cluster() = clusterDefinition.cluster(nameInCluster()) ?: notReachable()
    private fun ClassReference.cluster() = clusterDefinition.cluster(this.name) ?: notReachable()
    fun Any.cluster() = when(this){
        is GraphNode -> cluster()
        is ClassReference -> cluster()
        else -> notReachable()
    }


     fun geneticsClustering(geneticsClustering: GeneticsClusterDiagramContext.(ClusterSourceGenetics) -> ClusterDefinition): GeneticsClusterDiagramContext {
         clusterDefinition += geneticsClustering(configGenetics.details.clusteringAlgorithm)
         return this
     }




    fun Geneticsbuild() = listOf("GeneticDiagram.puml" to dot.create().attacheMetaData(configGenetics))

    fun buildNodeBasedDiagram(actions: NodeBuildingContextGenetics.() -> Unit): GeneticsClusterDiagramContext {
        ProgressManager.getGlobalProgressIndicator()?.text = "Diagram is generated"
        this.castSafelyTo<NodeBuildingContextGenetics>()?.let(actions)
        return this
    }

    fun buildClusterAggregatedDiagram(actions: GeneticsClusterBuildingContext.() -> Unit): GeneticsClusterDiagramContext {
        ProgressManager.getGlobalProgressIndicator()?.text = "Diagram is generated"
        this.castSafelyTo<GeneticsClusterBuildingContext>()?.let(actions)
        return this
    }

   fun Any.nameInCluster() = when(this) {
        is AnalyzeClass -> reference.name
        is AnalyzeMethod -> if(configGenetics.details.nodeAggregation == GeneticsClusterAggregation.Class) containingClass.name else id.replace("...", "").substringAfterLast(".")
        is ClassReference -> name
        else -> notReachable()
    }

    fun loadPackageClusters(): ClusterDefinition {
        val clusters = baseEdges
            .flatMap { listOf(it.from()!! to it, it.to()!! to it) }
            .map { (node, edge) -> node.aggregate() to edge }
            .groupBy { (node, _) -> node.containingClass().diagramPath(geneticsVisualConfig) }
            .flatMap { (cluster, links) ->
                links.map { (node, _) ->
                    node.nameInCluster() to cluster
                }
            }.toMap()

        return ClusterDefinition(clusters)
    }

    fun loadLayerClusters(): ClusterDefinition {
        val clusters = baseEdges
            .flatMap { listOf(it.from()!! to it, it.to()!! to it) }
            .map { (node, edge) -> node.aggregate() to edge }
            .groupBy { (node, _) -> node.containingClass().layer(configGenetics.projectClassification).name }
            .flatMap { (cluster, links) ->
                links.map { (node, _) ->
                    node.nameInCluster() to cluster
                }
            }.toMap()

        return ClusterDefinition(clusters)
    }

    fun GraphNode.aggregate(): Any {
        return when(configGenetics.details.nodeAggregation){
            GeneticsClusterAggregation.Class -> containingClass()
            GeneticsClusterAggregation.None -> this
        }
    }
}

fun NodeBuildingContextGenetics.visualizeGroupedByClustersSimplified() {
    // TODO check visualization in diagram to generify
    val classUsages = edges.flatMap { edge ->
        edge.nodes()
            .filterIsInstance<AnalyzeClass>()
            .map { cls -> cls to edge.nodes().map { it.cluster() } }.distinct()
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
                        .groupBy { it.cluster()!! }
                        .mapValues {
                            it.value
                                .map { it.shortName() }.sorted()
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
        .groupBy { it.containingClass to it.cluster() }
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
        if(from.cluster() == to.cluster() && classUsages[from.containingClass()]?.contains(",") != true && classUsages[to.containingClass()]?.contains(",") != true){
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
                tooltip = clusterEdges.allCalls()
            }
        }

}

fun GeneticsConfiguration.visualizationGeneticsConfig() = DiagramVisualizationConfiguration(
    rootNode = null,
    projectClassification,
    details.packageLevels,
    showClassGenericTypes = false,
    showClassMethods = false,
    showMethodParametersTypes = false,
    showMethodParametersNames = false,
    showMethodReturnType = false,
    showCallOrder = false,
    showDetailedClass = false
)




fun GraphDefinition.nodesForGeneticsClustering(filter: GraphTraversalFilter, details: GeneticsDiagramDetails): List<GraphNode> {



    with(filter.global){
        val validClasses = classes.values
            .filter { it.reference.included(details.nodeSelection.className, details.nodeSelection.classPackage) }
            .filter { filter.accept(it) }

        val classNodes = validClasses
            .filter { (it.reference.isDataStructure() || it.reference.isInterfaceStructure()) }

        val methodNodes = validClasses.flatMap { cls ->
            cls.methods.values
                .filter { it.name.included(details.nodeSelection.methodName) }
                .filter { !it.containingClass.isDataStructure() && !it.containingClass.isInterfaceStructure() && filter.accept(it) }
        }

        return methodNodes + classNodes
    }
}




fun GeneticsClusterNode.toClusterDotNode(): DotHTMLShape {
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
                    cell(packageNames.joinToString(", ").breakAfter(chars = 120))
                }
                row {
                    cell("Data Classes (${dataClasses.size})")
                    cell(dataClasses.clusterElements().breakEvery(elements = 2))
                }
                row {
                    cell("Service Classes (${serviceClasses.size})")
                    cell(serviceClasses.clusterElements().breakEvery(elements = 2))
                }
            }
        }
    }
}




private fun SquashedGraphEdge.isInverted(
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
