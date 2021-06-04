package com.kn.diagrams.generator.generator

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.progress.ProgressManager
import com.kn.diagrams.generator.builder.*
import com.kn.diagrams.generator.config.*
import com.kn.diagrams.generator.createIfNotExists
import com.kn.diagrams.generator.escapeHTML
import com.kn.diagrams.generator.graph.*
import com.kn.diagrams.generator.inReadAction
import com.kn.diagrams.generator.notReachable
import nl.cwts.networkanalysis.run.RunNetworkClustering
import java.io.File
import java.lang.reflect.Type
import java.util.*
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.streams.toList


fun ClusterConfiguration.visualizationConfig() = DiagramVisualizationConfiguration(
        null,
        projectClassification,
        details.packageLevels,
        false,
        false,
        false,
        false,
        false,
        false,
        false
)



fun GraphDefinition.nodesForClustering(filter: GraphTraversalFilter, details: ClusterDiagramDetails): List<GraphNode> {
    if(details.nodeSelection.clusterNameFromFile != ""){
        val wantedClusters = details.nodeSelection.clusterNameFromFile.split(";").toSet()
        return GsonBuilder().registerTypeAdapter(SimpleTypeToken<Map<String, List<String>>>().type, StringListOfStringsMapDeserializer())
            .setLenient().create()
            .fromJson<Map<String, List<String>>>(File(details.clusterDefinitionFile).readText(), SimpleTypeToken<Map<String, List<String>>>().type)
            .entries.filter { (cluster, _) -> cluster in wantedClusters }
            .flatMap { it.value }
            .mapNotNull { clusterEntry ->
                if (clusterEntry.contains(";")){
                    val cls = clusterEntry.substringBefore(";")
                    val clsNode = classes.values.firstOrNull { it.reference.name == cls }

                    clsNode?.methods?.values?.firstOrNull { clusterEntry == it.id.substringAfterLast(".") }
                } else {
                    classes.values.firstOrNull { it.reference.name == clusterEntry }
                }
            }.filter { filter.accept(it) }
    }else{
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
}

class ClusterDiagramGenerator {

    fun createUmlContent(config: ClusterConfiguration): List<Pair<String, String>> {
        val restrictionFilter = inReadAction { config.restrictionFilter() }
        val cache = analysisCache.getOrCompute(config.rootClass.project, restrictionFilter, config.projectClassification.searchMode)

        val filter = config.traversalFilter()
        val clusterRootNodes = cache.nodesForClustering(filter, config.details)
        val edges = cache.search(filter) {
            roots = clusterRootNodes
            forwardDepth = config.graphTraversal.forwardDepth
            backwardDepth = config.graphTraversal.backwardDepth
            edgeMode = config.details.edgeMode
        }.flatten().distinct()

        val visualizationConfiguration = config.visualizationConfig()
        val dot = DotDiagramBuilder()
        dot.direction = DiagramDirection.LeftToRight


        when(config.details.visualization){
            ClusterVisualization.Cluster, ClusterVisualization.ClusterWithoutDetails -> dot.aggregateToCluster(cache, edges, config, visualizationConfiguration)
            ClusterVisualization.Nodes -> {
                val clusters = if(config.details.clusteringAlgorithm == ClusterSource.TakeFromFile) {
                    loadPredefinedClusters(config.details.clusterDefinitionFile)
                }else if(config.details.clusteringAlgorithm == ClusterSource.Package) {
                    loadPackageClusters(edges, config, visualizationConfiguration)
                } else {
                    ClusterDefinition(clusterRootNodes.map { it.nameInCluster(config.details.nodeAggregation) to "cluster_0" }.toMap())
                }
                dot.visualizeGroupedByClusters(edges, config, visualizationConfiguration, clusters)
            }
            ClusterVisualization.NodesSimplified -> {
                val clusters = if(config.details.clusteringAlgorithm == ClusterSource.TakeFromFile) {
                    loadPredefinedClusters(config.details.clusterDefinitionFile)
                } else {
                    ClusterDefinition(clusterRootNodes.map { it.nameInCluster(config.details.nodeAggregation) to "cluster_0" }.toMap())
                }
                dot.visualizeGroupedByClustersSimplified(edges, config, visualizationConfiguration, clusters)
            }
        }


        return listOf("cluster" to dot.create().attacheMetaData(config))
    }
}

class SimpleTypeToken<T> : TypeToken<T>()

class ClusterDefinition(private val mapping: Map<String, String>) {

    fun cluster(ofClassName: String?): String? {
        if (ofClassName == null) return null

        return mapping[ofClassName]
    }

    fun serialize(): String {
            val asString = mapping.entries.groupBy { (_, cluster) -> cluster }
            .map { (cluster, nodes) -> """
                |   "$cluster":[
                ${nodes.joinToString(",\n") { "|        \"${ it.key }\"" }}
                |   ]
            """.trimMargin("|") }.joinToString(",\n")
        return """
            |{
            |$asString
            |}
        """.trimMargin("|")
    }
}

class StringListOfStringsMapDeserializer : JsonDeserializer<Map<String, List<String>>> {

    override fun deserialize(elem: JsonElement,
                             type: Type?,
                             jsonDeserializationContext: JsonDeserializationContext?): Map<String, List<String>> {
        return elem.asJsonObject.entrySet()
                .map { it.key to it.value.asJsonArray.map { it.asString } }
                .toMap()
    }

}


data class LeidenConfig(val resolution: Double, val starts: Int, val iterations: Int, val minNodes: Int, val randomness: Double)
data class LeidenConfigVariation(var resolution: List<Double>, var starts: List<Int>, var iterations: List<Int>, var minNodes: List<Int>, var randomness: List<Double>){
    constructor() : this(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
}

data class Cluster(val name: String, val dependenciesToOtherClusters: List<String>, val dataClasses: List<Any>, val serviceClasses: List<Any>){

    val dependenciesToOtherClustersCount = dependenciesToOtherClusters.size
    val serviceClassesCount = serviceClasses.size
    val dataClassesCount = dataClasses.size

    fun encapsulated() = dependenciesToOtherClustersCount == 0

}

fun loadPackageClusters(
    edges: List<SquashedGraphEdge>,
    config: ClusterConfiguration,
    visualConfig: DiagramVisualizationConfiguration
): ClusterDefinition{
    val aggregation = config.details.nodeAggregation

    val clusters = edges
        .flatMap { listOf(it.from()!! to it, it.to()!! to it) }
        .map { (node, edge) -> node.aggregate(aggregation) to edge }
        .groupBy { (node, _) -> node.containingClass().diagramPath(visualConfig) }
        .flatMap { (cluster, links) ->
            links.map { (node, _) ->
                node.nameInCluster(aggregation) to cluster
            }
        }.toMap()

    return ClusterDefinition(clusters)
}

fun loadLeidenClusters(edges: List<SquashedGraphEdge>, config: ClusterConfiguration): ClusterDefinition{
    val aggregation = config.details.nodeAggregation

    val nodes = edges.flatMap { it.nodes() }.map { it.nameInCluster(aggregation) }.distinct()

    File(".\\leiden_graph_${nodes.hashCode()}.txt")
        .createIfNotExists()
        .writeText(edges.createLeidenEdges(nodes, aggregation))

    if(config.details.leiden.optimizeClusterDistribution){
        val bestClusters = variate(config.details.leiden.leidenOptimization).parallelStream()
            .map {
                if(ProgressManager.getGlobalProgressIndicator()?.isCanceled == true) throw RuntimeException("aborted")
                it to edges.inClusters(clusterByLeiden(it, nodes), config.projectClassification, aggregation) }.toList()
            .sortedBy { score(it.second) }
            .take(20)

        bestClusters.forEachIndexed { i, clusters ->
            File("result_winner_$i").let {
                if(!it.exists()) it.createNewFile()
                it
            }.writeText(serializer.toJson(clusters.first) + "\n\n" + clusters
                .second.sortedByDescending { it.dependenciesToOtherClustersCount }.joinToString("\n") )
        }

        val bestConfig = bestClusters.first().first

        with(config.details.leiden){
            iterations = bestConfig.iterations
            minimumNodesPerCluster = bestConfig.minNodes
            randomStarts = bestConfig.starts
            randomness = bestConfig.randomness
            resolution = bestConfig.resolution
        }

        return clusterByLeiden(LeidenConfig(
            bestConfig.resolution,
            bestConfig.starts,
            bestConfig.iterations,
            bestConfig.minNodes,
            bestConfig.randomness
        ), nodes)
    } else {
        with(config.details.leiden){
            return clusterByLeiden(LeidenConfig(
                resolution,
                randomStarts,
                iterations,
                minimumNodesPerCluster,
                randomness
            ), nodes)
        }
    }
}

fun score(clusters: List<Cluster>): Double{
    val openClusters = clusters.filterNot { it.encapsulated() }
    val averageDependencies = openClusters.map { it.dependenciesToOtherClustersCount }.average()
    val averageDeviation = openClusters.map { it.dependenciesToOtherClustersCount }.standardDeviation()

    return averageDependencies * averageDeviation
}

fun List<Int>.standardDeviation(): Double {
    var sum = 0.0
    var standardDeviation = 0.0

    for (num in this) {
        sum += num
    }

    val mean = sum / size

    for (num in this) {
        standardDeviation += (num - mean).pow(2.0)
    }

    return sqrt(standardDeviation / size)
}

enum class ClusterAggregation { None, Class}
fun GraphNode.aggregate(aggregation: ClusterAggregation): Any {
    return when(aggregation){
        ClusterAggregation.Class -> containingClass()
        ClusterAggregation.None -> this
    }
}

fun List<SquashedGraphEdge>.inClusters(clusters: ClusterDefinition, classification: ProjectClassification, aggregation: ClusterAggregation): List<Cluster> {
    with(classification){
        return flatMap { listOf(it.from()!! to it, it.to()!! to it) }
            .map { (node, edge) -> node.aggregate(aggregation) to edge }
            .groupBy { (node, _) -> node.cluster(clusters, aggregation) }
            .map { (cluster, links) ->
                val distinctUsages = links.map { it.second }.map { it.from()!! to it.to()!! }.distinct()
                val ingoing = distinctUsages
                    .filter { (from, to) -> from.cluster(clusters, aggregation) != cluster && to.cluster(clusters, aggregation) == cluster }
                    .map { (from, _) -> from.cluster(clusters, aggregation) }
                    .distinct()
                val outgoing = distinctUsages
                    .filter { (from, to) -> from.cluster(clusters, aggregation) == cluster && to.cluster(clusters, aggregation) != cluster }
                    .map { (_, to) -> to.cluster(clusters, aggregation) }
                    .distinct()
                val dataClasses = links.map { it.first }.filter { it.containingClass().isDataStructure() || it.containingClass().isInterfaceStructure() }.distinct()
                val serviceClasses = links.map { it.first }.filterNot { it.containingClass().isDataStructure() }.distinct()

                Cluster(cluster, ingoing + outgoing, dataClasses, serviceClasses)
            }
    }
}

fun variate(variation: LeidenParametersVariation): List<LeidenConfig> {
    val configs = mutableListOf<LeidenConfig>()

    for (resolution in variation.resolution){
        for (starts in variation.randomStarts){
            for (minNodes in variation.minimumNodesPerCluster){
                for (randomness in variation.randomness){
                    for(iterations in variation.iterations) {
                        configs += LeidenConfig(
                            resolution,
                            starts,
                            iterations,
                            minNodes,
                            randomness
                        )
                    }
                }
            }
        }
    }

    return configs.toList()
}

private fun clusterByLeiden(
    config: LeidenConfig,
    nodes: List<String>
): ClusterDefinition {
    val outputFile = ".\\leiden_clusters_${config.hashCode()}_${nodes.hashCode()}.txt"

    val file = File(outputFile)
    if(!file.exists()){
        RunNetworkClustering.main(
            arrayOf(
                "--algorithm", "Leiden",
                "--resolution",  config.resolution.toString(), // Resolution higher resolution leeds to more communities
                "--random-starts", config.starts.toString(),
                "--iterations", config.iterations.toString(),
                "--quality-function", "CPM",
                "--min-cluster-size", config.minNodes.toString(),
                "--randomness", config.randomness.toString(), // higher randomness leeds to more exploration of partitions
                "--output-clustering", outputFile,
                "--sorted-edge-list",
                "--weighted-edges",
                ".\\leiden_graph_${nodes.hashCode()}.txt"
            )
        )
    }

    val clusters = file.readLines()
        .map { it.split("\t") }
        .map { (nodeIndex, cluster) -> nodes[nodeIndex.toInt()] to "cluster_$cluster" }
        .toMap()
    return ClusterDefinition(clusters)
}

fun List<SquashedGraphEdge>.createLeidenEdges(nodes: List<String>, aggregation: ClusterAggregation) = this.asSequence()
    .flatMap { sequenceOf(nodes.indexOf(it.from()!!.nameInCluster(aggregation)) to nodes.indexOf(it.to()!!.nameInCluster(aggregation)),
        nodes.indexOf(it.to()!!.nameInCluster(aggregation)) to nodes.indexOf(it.from()!!.nameInCluster(aggregation))) }
    .groupBy { it }
    .entries.map { (key, values) -> Triple(key.first, key.second, values.size) }
    .sortedWith(compareBy({ it.first }, { it.second }))
    .map { ""+it.first + "\t" + it.second + "\t" + it.third }
    .joinToString("\n")


fun loadPredefinedClusters(file: String) = ClusterDefinition(GsonBuilder()
    .registerTypeAdapter(SimpleTypeToken<Map<String, List<String>>>().type, StringListOfStringsMapDeserializer())
    .setLenient().create()
    .fromJson<Map<String, List<String>>>(File(file).readText(), SimpleTypeToken<Map<String, List<String>>>().type)
    .entries.flatMap { e -> e.value.map { it to e.key } }
    .toMap())


private fun DotDiagramBuilder.aggregateToCluster(graph: GraphDefinition, edges: List<SquashedGraphEdge>, config: ClusterConfiguration, visualConfig: DiagramVisualizationConfiguration) {
    ProgressManager.getGlobalProgressIndicator()?.text = "Clusters will be created"
    val clusters = when(config.details.clusteringAlgorithm) {
        ClusterSource.TakeFromFile -> loadPredefinedClusters(config.details.clusterDefinitionFile)
        ClusterSource.Leiden -> loadLeidenClusters(edges, config)
        ClusterSource.Package -> loadPackageClusters(edges, config, visualConfig)
    }

    if(config.details.clusterDefinitionOutputFile != ""){
        File(config.details.clusterDefinitionOutputFile)
            .createIfNotExists()
            .writeText(clusters.serialize())
        println("clusters output: "+File(config.details.clusterDefinitionOutputFile).absolutePath)
    }

    ProgressManager.getGlobalProgressIndicator()?.text = "Diagram is generated"

    visualizeInClusters(graph, edges, config, visualConfig, clusters)
}

class ClusterContext(val clusters: ClusterDefinition, val aggregation: ClusterAggregation){

    fun GraphNode.clusterName() = clusters.cluster(nameInCluster(aggregation))

}

fun DotDiagramBuilder.visualizeGroupedByClustersSimplified(
    edges: List<SquashedGraphEdge>,
    config: ClusterConfiguration,
    visualConfig: DiagramVisualizationConfiguration,
    clusters: ClusterDefinition
) {
    val aggregation = config.details.nodeAggregation

    val pathCluster = DotHierarchicalGroupCluster() { _, packageCluster, color, _ ->
        packageCluster.config.style = "filled"
        packageCluster.config.fillColor = "#" + color.toHex()

    }
    nodes.add(pathCluster)

    with(ClusterContext(clusters, aggregation)){

        val classUsages = edges.flatMap { edge ->
            edge.nodes()
                .filterIsInstance<AnalyzeClass>()
                .map { cls -> cls to edge.nodes()
                    .mapNotNull { it.clusterName() } }.distinct()
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
                            .groupBy { it.clusterName()!! }
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
                    nodes.add(table)
                }else{
                    // TODO check
                    pathCluster.addNode(table, Grouping(sameClusterName))
                }

            }

        edges.flatMap { it.nodes().filterIsInstance<AnalyzeMethod>() }
            .groupBy { it.containingClass to it.clusterName() }
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
                                cell(method.signature(visualConfig))
                            }
                        }
                }, Grouping(cluster ?: "missing cluster name"))
            }

        edges.groupBy { edge ->
            val from = edge.from()!!
            val to = edge.to()!!

            // TODO check warnings
            if(from.clusterName() == to.clusterName() && classUsages[from.containingClass()]?.contains(",") != true && classUsages[to.containingClass()]?.contains(",") != true){
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
                addLink(from, to){
                    tooltip = clusterEdges.allCalls()
                }
            }

    }




}

fun DotDiagramBuilder.visualizeGroupedByClusters(
    edges: List<SquashedGraphEdge>,
    config: ClusterConfiguration,
    visualConfig: DiagramVisualizationConfiguration,
    clusters: ClusterDefinition
) {
    val aggregation = config.details.nodeAggregation

    val pathCluster = DotHierarchicalGroupCluster { _, packageCluster, color, isLast ->
        packageCluster.config.style = "filled"
        packageCluster.config.fillColor = "#" + color.toHex()
    }
    nodes.add(pathCluster)

    fun addShapeForMethod(method: AnalyzeMethod) {
        pathCluster.addNode(DotShape(method.signature(visualConfig), method.diagramId()).with {
            tooltip = method.containingClass.name + "\n\n" + method.javaDoc
            fontColor = method.visibility.color()
            style = "filled"
            fillColor = "white"
        }, Grouping(method.containingClass.displayName))
    }

    edges.forEach { edge ->
        edge.nodes().forEach { node ->
            when (node) {
                is AnalyzeMethod -> if(aggregation == ClusterAggregation.None) {
                    addShapeForMethod(node)
                } else {
                    pathCluster.addNode(node.containingClass.createBoxShape(), Grouping(clusters.cluster(node.nameInCluster(aggregation)) ?: ""))
                }
                is AnalyzeClass -> pathCluster.addNode(node.createBoxOrTableShape(visualConfig), Grouping(clusters.cluster(node.nameInCluster(aggregation)) ?: ""))
            }
        }

        addDirectLink(edge, visualConfig)
    }
}


fun DotDiagramBuilder.visualizeInClusters(
    graph: GraphDefinition,
    edges: List<SquashedGraphEdge>,
    config: ClusterConfiguration,
    visualConfig: DiagramVisualizationConfiguration,
    clusters: ClusterDefinition
) {
    val aggregation = config.details.nodeAggregation
    with(visualConfig.projectClassification) {
        edges
            .flatMap { listOf(it.from()!! to it, it.to()!! to it) }
            .map { (node, edge) -> node.aggregate(aggregation) to edge }
            .groupBy { (node, _) -> node.cluster(clusters, aggregation) }
            .map { (cluster, links) ->
                val distinctUsages = links.map { it.second }.map { it.from()!! to it.to()!! }.distinct()
                val ingoing = distinctUsages.filter { (from, to) -> from.cluster(clusters, aggregation) != cluster && to.cluster(clusters, aggregation) == cluster }
                val outgoing = distinctUsages.filter { (from, to) -> from.cluster(clusters, aggregation) == cluster && to.cluster(clusters, aggregation) != cluster }
                val internal = distinctUsages.filter { (from, to) -> from.cluster(clusters, aggregation) == cluster && to.cluster(clusters, aggregation) == cluster }.size
                val dataClasses = links.map { it.first }.filter { it.containingClass().isDataStructure() || it.containingClass().isInterfaceStructure() }.distinct()
                val serviceClasses = links.map { it.first }.filterNot { it.containingClass().isDataStructure() }.distinct()

                addHTMLShape("$cluster-IN-$ingoing-OUT$outgoing-INT$internal-CLS$dataClasses", cluster) {
                    this.table.cellPadding = 5

                    withTable {
                        row {
                            if(config.details.visualization == ClusterVisualization.Cluster){
                                cell("Cluster")
                            }
                            cell(cluster)
                        }
                        if(config.details.visualization == ClusterVisualization.Cluster) {
                            horizontalSeparator()
                            row {
                                cell("Ingoing Calls")
                                cell(
                                    ingoing.size.toString() + "(${
                                        ingoing.map { (from, _) ->
                                            from.cluster(
                                                clusters,
                                                aggregation
                                            )
                                        }.distinct().size
                                    } clusters)"
                                )
                            }
                            row {
                                cell("Outgoing Calls")
                                cell(
                                    outgoing.size.toString() + "(${
                                        outgoing.map { (_, to) ->
                                            to.cluster(
                                                clusters,
                                                aggregation
                                            )
                                        }.distinct().size
                                    } clusters)"
                                )
                            }

                            row {
                                cell("Internal Calls")
                                cell(internal.toString())
                            }
                            row {
                                cell("Packages")
                                cell(dataClasses.map { it.containingClass().diagramPath(visualConfig) }
                                    .distinct().joinToString(", ")
                                    .chunked(120)
                                    .map { it + "<BR ALIGN=\"LEFT\"/>" }
                                    .joinToString(""))
                            }
                            row {
                                cell("Data Classes (${dataClasses.size})")
                                cell(dataClasses.clusterElements()
                                    .mapIndexed { i, cls -> if (i % 2 == 1) ", $cls,<BR ALIGN=\"LEFT\"/>" else cls }
                                    .joinToString("") + "<BR ALIGN=\"LEFT\"/>")
                            }
                            row {
                                cell("Service Classes (${serviceClasses.size})")
                                cell(serviceClasses.clusterElements()
                                    .mapIndexed { i, cls -> if (i % 2 == 1) ", $cls,<BR ALIGN=\"LEFT\"/>" else cls }
                                    .joinToString("") + "<BR ALIGN=\"LEFT\"/>")
                            }
                        }
                    }

                }
            }
    }
    val clustersToEdges = edges
        .filter { it.from()!!.cluster(clusters, aggregation) != it.to()!!.cluster(clusters, aggregation) }
        .groupBy { it.from()!!.cluster(clusters, aggregation) + "_" + it.to()!!.cluster(clusters, aggregation) }
    val directDependencies = clustersToEdges.entries.asSequence()
        .filter { !it.value.all { it.isInverted(graph, visualConfig) } }
        .map { it.key }
        .map { it.split("_") }.map { (from, to) -> from to to }
        .groupBy { (from, _) -> from }
        .mapValues { it.value.map { (_, to) -> to } }

    val eliminationMatrix = if(1==1) directDependencies.transientEliminationMatrix() else emptyMap()

    clustersToEdges.entries
        .filter { (dependency, _) -> eliminationMatrix.values.none { it.contains(dependency) } }
        .forEach { (dependency, calls) ->
            if (eliminationMatrix.isReplaced(dependency)) return@forEach

            val from = calls.first().from()!!.cluster(clusters, aggregation)
            val to = calls.first().to()!!.cluster(clusters, aggregation)

            val allCalls = calls + eliminationMatrix.replacedDependenciesFor(dependency).flatMap { clustersToEdges[it] ?: emptyList()}

            val invertedDependencies = calls.filter { edge ->
                edge.isInverted(graph, visualConfig)
            }.filter { it in calls }


            if (invertedDependencies.isNotEmpty()) {
                addLink(to, from) {
                    // IMPROVE: find the GraphDirectedEdge (maybe hidden) between both clusters and count the context
                    label = "inverted calls =    " + invertedDependencies.distinctBy { it.from() to it.to() }.size // counts parallel edges only once!!
                    if(config.details.showCallsInEdgeToolTips){
                        tooltip = invertedDependencies.allCalls(false)
                    }
                    color = "red"
                }
            }
            val withoutInverted = allCalls - invertedDependencies
            if (withoutInverted.isNotEmpty()) {
                addLink(from, to) {
                    // IMPROVE: find the GraphDirectedEdge (maybe hidden) between both clusters and count the context
                    label = "calls =    " + withoutInverted.distinctBy { it.from() to it.to() }.size // counts parallel edges only once!!
                    if(config.details.showCallsInEdgeToolTips) {
                        tooltip = withoutInverted.allCalls(false)
                    }
                }
            }
    }
}

fun Map<String, MutableList<String>>.isReplaced(dependency: String) = values.any { it.contains(dependency) }

fun Map<String, MutableList<String>>.replacedDependenciesFor(dependency: String): List<String> {
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

private fun SquashedGraphEdge.isInverted(
    graph: GraphDefinition,
    visualConfig: DiagramVisualizationConfiguration
): Boolean {
    val last = edges().last()
    if(last.from is AnalyzeClass || last.to is AnalyzeClass) return false

    val clsFrom = last.from.containingClass()
    val clsTo = graph.classes[last.to.containingClass().id()]!!

    val inherited = clsTo.superTypes.any { it == clsFrom } || clsTo.superTypes.flatMap { graph.classes[it.id()]?.superTypes ?: emptyList() }.any{ it == clsFrom }
    val otherPackage =
        clsFrom.diagramPath(visualConfig) != clsTo.containingClass().diagramPath(visualConfig)

    return inherited && otherPackage
}

fun Map<String, List<String>>.transientEliminationMatrix(): Map<String, MutableList<String>> {
    val dependencies = entries.flatMap { entry -> entry.value.map { entry.key to it } }

    val result = mutableMapOf<String, MutableList<String>>()

    val directOnlyDependencies = dependencies.filter { (from, to) -> get(from)!!.any { it == to } && !hasPath(from, to, indirect = true) }

    directOnlyDependencies.forEach { (from, to) ->
        dependencies.asSequence()
            .filter { (otherFrom, otherTo) -> result.none { it.value.contains("${otherFrom}_$otherTo") } }
            .filter { (otherFrom, _) -> hasPath(otherFrom, from, indirect = false) }
            .filter { (_, otherTo) -> otherTo == to }
            .forEach { (otherFrom, otherTo) ->
                val responsibleCluster = "${from}_$to"
                val replacedCluster = "${otherFrom}_$otherTo"

                result.getOrPut(responsibleCluster){ mutableListOf() }.add(replacedCluster)
            }
    }

    return result.toMap()
}

fun Map<String, List<String>>.hasPath(from: String, to: String, indirect: Boolean): Boolean{
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


fun List<Any>.clusterElements() = this
    .groupBy { it.containingClass() }.entries
    .map { (cls, values) -> cls.name + (values
        .filterIsInstance<AnalyzeMethod>()
        .takeIf { it.isNotEmpty() }
        ?.joinToString { it.name+"(${it.parameter.size})" }
        ?.let { "[$it]" }
        ?: "")
    }


fun Any.cluster(clusters: ClusterDefinition, aggregation: ClusterAggregation) = when(this){
    is GraphNode -> cluster(clusters, aggregation)
    is ClassReference -> cluster(clusters)
    else -> notReachable()
}

fun Any.containingClass() = when(this){
    is GraphNode -> containingClass()
    is ClassReference -> this
    else -> notReachable()
}

private fun GraphNode.cluster(clusters: ClusterDefinition, aggregation: ClusterAggregation = ClusterAggregation.None) = clusters.cluster(nameInCluster(aggregation)) ?: notReachable()

private fun Any.nameInCluster(aggregation: ClusterAggregation = ClusterAggregation.None) = when(this) {
    is AnalyzeClass -> reference.name
    is AnalyzeMethod -> if(aggregation == ClusterAggregation.Class) containingClass.name else id.replace("...", "").substringAfterLast(".")
    is ClassReference -> name
    else -> notReachable()
}

private fun ClassReference.cluster(clusters: ClusterDefinition) = clusters.cluster(this.name) ?: notReachable()


fun List<SquashedGraphEdge>.allCalls(hidden: Boolean = false) = this
    .map{ it.allCalls(hidden) }
    .distinct().sorted()
    .joinToString("&#013;")
    .run{ substring(0, min(16_300, length)) }

fun SquashedGraphEdge.allCalls(hidden: Boolean = false) = "${from().shortName()} -> ${to().shortName()}" + if (hidden) {
    " ### " + edges().flatMap { listOf(it.from, it.to) }.distinct().joinToString("=>") { it.shortName() }
} else ""

fun GraphNode?.shortName() = when (this) {
    null -> "nothing"
    is AnalyzeClass -> reference.name
    is AnalyzeMethod -> containingClass.name + "." + name
    else -> notReachable()
}
