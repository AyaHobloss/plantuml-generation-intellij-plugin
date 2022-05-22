package com.kn.diagrams.generator.generator.code

import com.kn.diagrams.generator.config.LeidenParametersVariation
import com.kn.diagrams.generator.config.serializer
import com.kn.diagrams.generator.createIfNotExists
import com.kn.diagrams.generator.generator.containingClass
import com.kn.diagrams.generator.throwExceptionIfCanceled
import nl.cwts.networkanalysis.run.RunNetworkClustering
import java.io.File
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.streams.toList


data class LeidenConfig(val resolution: Double, val starts: Int, val iterations: Int, val minNodes: Int, val randomness: Double)

// TODO add again!
data class LeidenConfigVariation(var resolution: List<Double>, var starts: List<Int>, var iterations: List<Int>, var minNodes: List<Int>, var randomness: List<Double>){
    constructor() : this(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
}

fun ClusterDiagramContext.loadLeidenClusters(): ClusterDefinition {
    val nodes = baseEdges.flatMap { it.nodes() }.map { it.nameInCluster() }.distinct()

    File(".\\leiden_graph_${nodes.hashCode()}.txt")
            .createIfNotExists()
            .writeText(createLeidenEdges(nodes))

    if(config.details.leiden.optimizeClusterDistribution){
        val bestClusters = variate(config.details.leiden.leidenOptimization).parallelStream()
                .map {
                    throwExceptionIfCanceled()
                    it to edgesToClusters() }.toList()
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


fun ClusterDiagramContext.createLeidenEdges(nodes: List<String>) = baseEdges.asSequence()
        .flatMap { sequenceOf(nodes.indexOf(it.from()!!.nameInCluster()) to nodes.indexOf(it.to()!!.nameInCluster()),
                nodes.indexOf(it.to()!!.nameInCluster()) to nodes.indexOf(it.from()!!.nameInCluster())) }
        .groupBy { it }
        .entries.map { (key, values) -> Triple(key.first, key.second, values.size) }
        .sortedWith(compareBy({ it.first }, { it.second }))
        .map { ""+it.first + "\t" + it.second + "\t" + it.third }
        .joinToString("\n")

// TODO unify with datastructure ClusterNode; add comment in config that services/dataclasses get balanced - configure the weight?
fun ClusterDiagramContext.edgesToClusters(): List<LeidenCluster> {
    with(visualConfig.projectClassification){
        return baseEdges.flatMap { listOf(it.from()!! to it, it.to()!! to it) }
                .map { (node, edge) -> node.aggregate() to edge }
                .groupBy { (node, _) -> node.cluster() }
                .map { (cluster, links) ->
                    val distinctUsages = links.map { it.second }.map { it.from()!! to it.to()!! }.distinct()
                    val ingoing = distinctUsages
                            .filter { (from, to) -> from.cluster() != cluster && to.cluster() == cluster }
                            .map { (from, _) -> from.cluster() }
                            .distinct()
                    val outgoing = distinctUsages
                            .filter { (from, to) -> from.cluster() == cluster && to.cluster() != cluster }
                            .map { (_, to) -> to.cluster() }
                            .distinct()
                    val dataClasses = links.map { it.first }.filter { it.containingClass().isDataStructure() || it.containingClass().isInterfaceStructure() }.distinct()
                    val serviceClasses = links.map { it.first }.filterNot { it.containingClass().isDataStructure() }.distinct()

                    LeidenCluster(cluster, ingoing + outgoing, dataClasses, serviceClasses)
                }
    }
}

data class LeidenCluster(val name: String, val dependenciesToOtherClusters: List<String>, val dataClasses: List<Any>, val serviceClasses: List<Any>){

    val dependenciesToOtherClustersCount = dependenciesToOtherClusters.size

    fun encapsulated() = dependenciesToOtherClustersCount == 0

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

fun clusterByLeiden(
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


fun score(clusters: List<LeidenCluster>): Double{
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
