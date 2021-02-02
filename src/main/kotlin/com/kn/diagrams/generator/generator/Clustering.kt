package com.kn.diagrams.generator.generator

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.progress.ProgressManager
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
import com.kn.diagrams.generator.config.ClusterConfiguration
import com.kn.diagrams.generator.config.ClusterDiagramDetails
import com.kn.diagrams.generator.config.ClusterSource
import com.kn.diagrams.generator.config.attacheMetaData
import com.kn.diagrams.generator.graph.*
import com.kn.diagrams.generator.notReachable
import org.neo4j.graphalgo.core.CypherMapWrapper
import org.neo4j.graphalgo.core.concurrency.Pools
import org.neo4j.graphalgo.core.utils.ProgressLogger
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker
import org.neo4j.graphalgo.louvain.Louvain
import org.neo4j.graphalgo.louvain.LouvainStreamConfigImpl
import java.io.File
import java.lang.reflect.Type
import java.util.*


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


// TOTO: add a cache based on the restriction and classification hash
// TODO: add Leiden algorithmus https://github.com/CWTSLeiden/networkanalysis
class ClusterDiagramGenerator {

    fun createUmlContent(config: ClusterConfiguration): List<Pair<String, String>> {
        val cache = GraphCache(config.rootClass.project, config.restrictionFilter(), config.projectClassification.searchMode)

        val root = cache.classFor(config.rootClass)!!

        val filter = config.traversalFilter(root)
        val edges = cache.search(filter) {
            roots = cache.classes.values
                .filter { filter.accept(it) }
                .flatMap { it.methods.values.filter { filter.accept(it) } } + cache.classes.values.filter { filter.accept(it) }
            forwardDepth = config.graphTraversal.forwardDepth
            backwardDepth = config.graphTraversal.backwardDepth
            edgeMode = config.details.edgeMode
        }.flatten()

        val visualizationConfiguration = config.visualizationConfig(cache)
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
    ProgressManager.getGlobalProgressIndicator()?.text = "Clusters will be created"
    val clusters = when(config.details.source) {
        ClusterSource.File -> loadPredefinedClusters(config.details.clusterDefinitionFile)
        ClusterSource.Connectivity -> loadConnectivityClusters(edges, config.details.levelOfConnectivity)
        ClusterSource.Louvian -> loadLouvianClusters(edges, config.details)
    }

    ProgressManager.getGlobalProgressIndicator()?.text = "Diagram is generated"
    with(visualConfig.projectClassification) {
        edges
            .flatMap { listOf(it.from()!! to it, it.to()!! to it) }
            .map { (node, edge) -> node.containingClass() to edge }
            .groupBy { (cls, _) -> cls.cluster(clusters) }
            .map { (cluster, links) ->
                val distinctUsages = links.map { it.second }.map { it.from()!! to it.to()!! }.distinct()
                val ingoing = distinctUsages.filter { (from, to) -> from.cluster(clusters) != cluster && to.cluster(clusters) == cluster }
                val outgoing = distinctUsages.filter { (from, to) -> from.cluster(clusters) == cluster && to.cluster(clusters) != cluster }
                val internal = distinctUsages.filter { (from, to) -> from.cluster(clusters) == cluster && to.cluster(clusters) == cluster }.size
                val dataClasses = links.map { it.first }.filter { it.isDataStructure() || it.isInterfaceStructure() }.distinct()
                val serviceClasses = links.map { it.first }.filterNot { it.isDataStructure() }.distinct()

//                if(cluster != "none")
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
                            cell(ingoing.size.toString() + "(${ingoing.map { (from, _) -> from.cluster(clusters) }.distinct().size } clusters)")
                        }
                        row {
                            cell("Outgoing Calls")
                            cell(outgoing.size.toString() + "(${outgoing.map { (_, to) -> to.cluster(clusters) }.distinct().size } clusters)")
                        }

                        row {
                            cell("Internal Calls")
                            cell(internal.toString())
                        }
                        row {
                            cell("Packages")
                            cell(dataClasses.map { it.javaPackage() }
                                .distinct().joinToString(", ")
                                .chunked(120)
                                .map { it + "<BR ALIGN=\"LEFT\"/>" }
                                .joinToString(""))
                        }
                        row {
                            cell("Data Classes (${dataClasses.size})")
                            cell(dataClasses
                                .mapIndexed { i, cls -> if (i % 2 == 1) ", " + cls.name + ",<BR ALIGN=\"LEFT\"/>" else cls.name }
                                .joinToString("") + "<BR ALIGN=\"LEFT\"/>")
                        }
                        row {
                            cell("Service Classes (${serviceClasses.size})")
                            cell(serviceClasses
                                .mapIndexed { i, cls -> if (i % 2 == 1) ", " + cls.name + ",<BR ALIGN=\"LEFT\"/>" else cls.name }
                                .joinToString("") + "<BR ALIGN=\"LEFT\"/>")
                        }
                    }

                }
            }
    }

    edges
        .filter { it.from()!!.cluster(clusters) != it.to()!!.cluster(clusters) }
        .groupBy { it.from()!!.cluster(clusters) + "_" + it.to()!!.cluster(clusters) }
        .values.forEach { calls ->
            val from = calls.first().from()!!.cluster(clusters)
            val to = calls.first().to()!!.cluster(clusters)
            if(calls.size > config.details.showEdgesAboveCallCount){
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
fun GraphNode.cluster(clusters: ClusterDefinition) = containingClass().cluster(clusters)

private fun ClassReference?.cluster(clusters: ClusterDefinition) = clusters.cluster(this?.name) ?: "none"


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
