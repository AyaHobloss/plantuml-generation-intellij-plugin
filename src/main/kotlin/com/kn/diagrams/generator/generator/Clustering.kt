package com.kn.diagrams.generator.generator

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.kn.diagrams.generator.builder.DiagramDirection
import com.kn.diagrams.generator.builder.DotDiagramBuilder
import com.kn.diagrams.generator.builder.addHTMLShape
import com.kn.diagrams.generator.builder.addLink
import com.kn.diagrams.clustering.TemporaryGraph
import com.kn.diagrams.clustering.algorithm.GAConnectedComponents
import com.kn.diagrams.clustering.model.GAEdge
import com.kn.diagrams.clustering.model.GAEdgeType
import com.kn.diagrams.clustering.model.GAGraph
import com.kn.diagrams.clustering.model.GANode
import com.kn.diagrams.generator.config.*
import com.kn.diagrams.generator.graph.*
import com.kn.diagrams.generator.inReadAction
import com.kn.diagrams.generator.notReachable
import nl.cwts.networkanalysis.run.RunNetworkClustering
import org.neo4j.graphalgo.core.CypherMapWrapper
import org.neo4j.graphalgo.core.concurrency.Pools
import org.neo4j.graphalgo.core.utils.ProgressLogger
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker
import org.neo4j.graphalgo.louvain.Louvain
import org.neo4j.graphalgo.louvain.LouvainStreamConfigImpl
import java.io.File
import java.lang.reflect.Type
import java.util.*
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.streams.toList


fun ClusterConfiguration.visualizationConfig(cache: GraphCache) = DiagramVisualizationConfiguration(
    cache.classes[rootClass.reference().id()]!!,
    projectClassification,
    projectClassification.includedProjects,
    projectClassification.pathEndKeywords,
    0,
    false,
    false,
    false,
    false,
    false,
    false,
    false
)


class Cache {
    var existingGraph: GraphCache? = null
    var restrictionHash: Int = -1

    fun getOrCompute(project: Project, restrictionFilter: RestrictionFilter, searchMode: SearchMode): GraphCache{
        if(needsCompute(restrictionFilter)){
            existingGraph = GraphCache(project, restrictionFilter, searchMode)
            restrictionHash = hash(restrictionFilter)
        }

        return existingGraph!!
    }

    private fun needsCompute(restrictionFilter: RestrictionFilter) = existingGraph == null || restrictionHash != hash(restrictionFilter)

    private fun hash(restrictionFilter: RestrictionFilter) = serializer.toJson(restrictionFilter).hashCode()
}

val cache = Cache()

// TOTO: add a cache based on the restriction and classification hash
// TODO: add Leiden algorithmus https://github.com/CWTSLeiden/networkanalysis
class ClusterDiagramGenerator {

    fun createUmlContent(config: ClusterConfiguration): List<Pair<String, String>> {
        val restrictionFilter = inReadAction { config.restrictionFilter() }
        val cache = cache.getOrCompute(config.rootClass.project, restrictionFilter, config.projectClassification.searchMode)

        val root = inReadAction { cache.classFor(config.rootClass)!! }

        val filter = config.traversalFilter(root)
        val edges = cache.search(filter) {
            with(config.projectClassification){
                roots = cache.classes.values
                    .filter { filter.accept(it) }
                    .flatMap { it.methods.values.filter { !it.containingClass.isDataStructure() && !it.containingClass.isInterfaceStructure() && filter.accept(it) } } + cache.classes.values.filter { it.reference.isDataStructure() && it.reference.isInterfaceStructure() && filter.accept(it) }
            }
            forwardDepth = config.graphTraversal.forwardDepth
            backwardDepth = config.graphTraversal.backwardDepth
            edgeMode = config.details.edgeMode
        }.flatten().distinct()

        val visualizationConfiguration = inReadAction { config.visualizationConfig(cache) }
        val dot = DotDiagramBuilder()
        dot.direction = DiagramDirection.LeftToRight

        dot.aggregateToCluster(edges, config, visualizationConfiguration)

        return listOf("cluster" to dot.create().attacheMetaData(config))
    }
}

class SimpleTypeToken<T> : TypeToken<T>()

class ClusterDefinition(private val mapping: Map<String, String>) {

    fun cluster(ofClassName: String?): String? {
        if (ofClassName == null) return null

        return mapping[ofClassName]
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
data class LeidenConfigVariation(var resolution: ClosedRange<Double>, var starts: IntRange, var iterations: IntRange, var minNodes: IntRange, var randomness: ClosedRange<Double>){
    constructor() : this(0.0.rangeTo(0.0), 1..1, 1..1, 1..1, 0.09.rangeTo(0.09))
}

data class Cluster(val name: String, val dependenciesToOtherClusters: List<String>, val dataClasses: List<Any>, val serviceClasses: List<Any>){

    val dependenciesToOtherClustersCount = dependenciesToOtherClusters.size
    val serviceClassesCount = serviceClasses.size
    val dataClassesCount = dataClasses.size

    fun encapsulated() = dependenciesToOtherClustersCount == 0

}

const val graphFilePath = ".\\leiden_graph.txt"
fun loadLeidenClusters(edges: List<SquashedGraphEdge>, config: ClusterConfiguration): ClusterDefinition{
    val aggregation = config.details.nodeAggregation

    val nodes = edges.flatMap { it.nodes() }.map { it.nameInCluster(aggregation) }.distinct()

    File(graphFilePath)
        .let {
            println("graph location: ${it.absolutePath}")
            if(!it.exists()) it.createNewFile()
            it
        }
        .writeText(edges.createLeidenEdges(nodes, aggregation))

    if(1==1){
        variate {
            resolution = 0.3.rangeTo(0.00001)
            starts = 5..15
            iterations = 2..50
            minNodes = 2..22
            randomness = 0.01.rangeTo(0.2)
        }.parallelStream()
            .map {
                if(ProgressManager.getGlobalProgressIndicator()?.isCanceled == true) throw RuntimeException("aborted")
                it to edges.inClusters(clusterByLeiden(it, nodes), config.projectClassification, aggregation) }.toList()
            .sortedBy { score(it.second) }
            .take(20)
            .forEachIndexed { i, clusters ->
                File("result_winner_$i").let {
                    if(!it.exists()) it.createNewFile()
                    it
                }.writeText(serializer.toJson(clusters.first) + "\n\n" + clusters
                    .second.sortedByDescending { it.dependenciesToOtherClustersCount }.joinToString("\n") )
            }

    }

    return clusterByLeiden(LeidenConfig(
        config.details.leidenResolution, config.details.leidenStarts, config.details.leidenIterations, config.details.leidenMinimumNodesPerCluster, config.details.leidenRandomness
    ), nodes)
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

fun variate(action: LeidenConfigVariation.() -> Unit): List<LeidenConfig> {
    val configs = mutableListOf<LeidenConfig>()
    val variation = LeidenConfigVariation()
    action(variation)

    for (resolution in variation.resolution.inSteps(20)){
        for (starts in variation.starts.inIntSteps(4)){
            for (minNodes in variation.minNodes.inIntSteps(6)){
                for (randomness in variation.randomness.inSteps(4)){
                    for(iterations in variation.iterations.inIntSteps(10)) {
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

fun ClosedRange<Double>.inSteps(steps: Int): List<Double> {
    val width = endInclusive - start
    val stepSize = width / steps

    return 0.until(steps).map { start + (it * stepSize)  }
}

fun ClosedRange<Int>.inIntSteps(steps: Int): List<Int> {
    val width = endInclusive - start
    val stepSize = 1.0 * width / steps

    return 0.until(steps).map { round(start + (it * stepSize)).toInt()  }
}

private fun clusterByLeiden(
    config: LeidenConfig,
    nodes: List<String>
): ClusterDefinition {
    val outputFile = ".\\leiden_clusters_${config.hashCode()}.txt"

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
                graphFilePath
            )
        )
    }

    val clusters = file.readLines()
        .map { it.split("\t") }
        // TODO fix index out of bounds / none cluster + *.xml.* in salog
        .map { (nodeIndex, cluster) -> (nodes.getOrNull(nodeIndex.toInt()) ?: "incorrect index") to "cluster_$cluster" }
        .toMap()
    return ClusterDefinition(clusters)
}

fun List<SquashedGraphEdge>.createLeidenEdges(nodes: List<String>, aggregation: ClusterAggregation) = this.asSequence()
    .flatMap { sequenceOf(nodes.indexOf(it.from()!!.nameInCluster(aggregation)) to nodes.indexOf(it.to()!!.nameInCluster(aggregation)),
        nodes.indexOf(it.to()!!.nameInCluster(aggregation)) to nodes.indexOf(it.from()!!.nameInCluster(aggregation))) }
    .distinct()
    .sortedWith(compareBy({ it.first }, { it.second }))
    .map { ""+it.first + "\t" + it.second }
    .joinToString("\n")


fun loadConnectivityClusters(edges: List<SquashedGraphEdge>, levelOfConnectivity: Int): ClusterDefinition {
    val nodes = edges
        .flatMap { it.nodes() }
        .map { it.containingClass() }
        .distinct()
        .map { GANode(it.diagramId(), it.name, it.path, false) }
    val nodesById = nodes.groupBy { it.id }.mapValues { it.value.first()  }
    val gaEdges = edges
        .map { it.from()!!.containingClass().diagramId() to it.to()!!.containingClass().diagramId() }
        .distinct()
        .map { (fromId, toId) -> GAEdge(
            nodesById[fromId]!!,
            nodesById[toId]!!,
            GAEdgeType.IS_USED, false, true, false) }

    val graph = GAGraph(nodes, gaEdges)
    val connectedComponents = GAConnectedComponents(graph, levelOfConnectivity)

    return ClusterDefinition(connectedComponents
        .runAlgorithm()
        .mapIndexed { i, content ->
            content.map { it.name to "cluster_$i"  }
        }.flatten().toMap()
    )
}

fun loadPredefinedClusters(file: String) = ClusterDefinition(GsonBuilder()
    .registerTypeAdapter(SimpleTypeToken<Map<String, List<String>>>().type, StringListOfStringsMapDeserializer())
    .setLenient().create()
    .fromJson<Map<String, List<String>>>(File(file).readText(), SimpleTypeToken<Map<String, List<String>>>().type)
    .entries.flatMap { e -> e.value.map { it to e.key } }
    .toMap())


private fun DotDiagramBuilder.aggregateToCluster(edges: List<SquashedGraphEdge>, config: ClusterConfiguration, visualConfig: DiagramVisualizationConfiguration) {
    val aggregation = config.details.nodeAggregation

    ProgressManager.getGlobalProgressIndicator()?.text = "Clusters will be created"
    val clusters = when(config.details.source) {
        ClusterSource.File -> loadPredefinedClusters(config.details.clusterDefinitionFile)
        ClusterSource.Connectivity -> loadConnectivityClusters(edges, config.details.levelOfConnectivity)
        ClusterSource.Louvian -> loadLouvianClusters(edges, config.details)
        ClusterSource.Leiden -> loadLeidenClusters(edges, config)
    }

    ProgressManager.getGlobalProgressIndicator()?.text = "Diagram is generated"
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
                            cell("Cluster")
                            cell(cluster)
                        }
                        horizontalSeparator()
                        row {
                            cell("Ingoing Calls")
                            cell(ingoing.size.toString() + "(${ingoing.map { (from, _) -> from.cluster(clusters, aggregation) }.distinct().size } clusters)")
                        }
                        row {
                            cell("Outgoing Calls")
                            cell(outgoing.size.toString() + "(${outgoing.map { (_, to) -> to.cluster(clusters, aggregation) }.distinct().size } clusters)")
                        }

                        row {
                            cell("Internal Calls")
                            cell(internal.toString())
                        }
                        row {
                            cell("Packages")
                            cell(dataClasses.map { it.containingClass().javaPackage() }
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

    edges
        .filter { it.from()!!.cluster(clusters, aggregation) != it.to()!!.cluster(clusters, aggregation) }
        .groupBy { it.from()!!.cluster(clusters, aggregation) + "_" + it.to()!!.cluster(clusters, aggregation) }
        .values.forEach { calls ->
            val from = calls.first().from()!!.cluster(clusters, aggregation)
            val to = calls.first().to()!!.cluster(clusters, aggregation)
            if(calls.size > config.details.showEdgesAboveCallCount && calls.size < config.details.showEdgesBelowCallCount){
                addLink(from, to) {
                    // IMPROVE: find the GraphDirectedEdge (maybe hidden) between both clusters and count the context
                    label = "calls =    " + calls.size // counts parallel edges only once!!
                    if(1==2){
                        tooltip = calls.allCalls(true)
                    }
                }
            }
            // TODO add a 1:1 edge to indicate hidden edges
        }
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

fun gdlGraphWithoutAggregation(edges: List<SquashedGraphEdge>): String{
    val nodes = edges.flatMap { it.nodes() }.distinct()
    return """
        g1[
        ${
        nodes
            .joinToString("\n") { """(${it.diagramId()}:${it.shortName()})""" }
    }
        ${
        edges.joinToString("\n") { """(${it.from()!!.diagramId()})-->(${it.to()!!.diagramId()})""" }
    }
        ]
    """.trimIndent()
}

fun gdlGraphWithClassAggregation(edges: List<SquashedGraphEdge>): String{
    val nodes = edges.flatMap { it.nodes().map { it.containingClass() } }.distinct()
    return """
        g1[
        ${
        nodes
            .joinToString("\n") { """(${it.diagramId()}:${it.name})""" }
    }
        ${
        edges.map { it.from()!!.containingClass() to it.to()!!.containingClass() }
            .distinct()
            .joinToString("\n") { (from, to) -> """(${from.diagramId()})-->(${to.diagramId()})""" }
    }
        ]
    """.trimIndent()
}


fun loadLouvianClusters(edges: List<SquashedGraphEdge>, config: ClusterDiagramDetails): ClusterDefinition {
    val nodes = edges.flatMap { it.nodes() }.distinct()
    val graph = TemporaryGraph.Builder.fromGdl(gdlGraphWithClassAggregation(edges))

    val algorithm = Louvain(
        graph,
        LouvainStreamConfigImpl(
            Optional.of("g1"), Optional.empty(), "", CypherMapWrapper.empty()
            .withNumber("maxIterations", config.louvianMaxIterations)
            .withNumber("maxLevels", config.louvianMaxLevels)),
        Pools.DEFAULT,
        ProgressLogger.NULL_LOGGER,
        AllocationTracker.EMPTY
    )
    val result = algorithm.compute()

    // test
    val clusters = nodes
        .map { it.containingClass() }.distinct()
        .mapIndexed { i, node -> node.name to "cluster_"+result.getCommunity(i.toLong()) }.toMap()

    return ClusterDefinition(clusters)

}

private fun ClassReference.javaPackage() = path
    .replace("com.kn.salog.", "")
    .replace("com.kn.qtair.", "")
    .replace("com.kn.rmsa.", "")
    .substringBefore(".service")
    .substringBefore(".remote")
    .substringBefore(".entity")
    .substringBefore(".domain")
    .substringBefore(".dataaccess")
    .substringBefore(".ws")
    .substringBefore(".xml")
    .substringBefore(".impl")
    .substringBefore(".assembler")

//private fun ClassReference?.cluster() = this
//        ?.javaPackage()
//        ?.replace(".","")
//        ?: "none" //clusterDefinition.filter { (matching,_) -> matching.matches(name) }.firstOrNull()?.value ?: "none"
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
    is AnalyzeMethod -> if(aggregation == ClusterAggregation.Class) containingClass.name else id.substringAfterLast(".")
    is ClassReference -> name
    else -> notReachable()
}

private fun ClassReference.cluster(clusters: ClusterDefinition) = clusters.cluster(this.name) ?: notReachable()


fun List<SquashedGraphEdge>.allCalls(hidden: Boolean = false) = joinToString("<br/>") { it.allCalls(hidden) }

fun SquashedGraphEdge.allCalls(hidden: Boolean = false) = if (hidden) {
    edges().flatMap { listOf(it.from, it.to) }.distinct().joinToString("=>") { it.shortName() }
} else {
    "${from().shortName()} -> ${to().shortName()}"
}

fun GraphNode?.shortName() = when (this) {
    null -> "nothing"
    is AnalyzeClass -> reference.name
    is AnalyzeMethod -> containingClass.name + "." + name
    else -> notReachable()
}
