package com.kn.diagrams.generator.generator

import com.kn.diagrams.generator.config.EdgeAggregation
import com.kn.diagrams.generator.config.VcsNodeAggregation
import com.kn.diagrams.generator.generator.code.layer
import com.kn.diagrams.generator.generator.vcs.UndirectedEdge
import com.kn.diagrams.generator.generator.vcs.VcsCommit
import com.kn.diagrams.generator.generator.vcs.VcsVisualization
import com.kn.diagrams.generator.graph.ClassReference
import com.kn.diagrams.generator.graph.included
import java.net.URL
import java.util.stream.Stream
import kotlin.math.*
import kotlin.streams.toList


class AggregatedVcsGraph(val context: VcsVisualization) {
    val groupByAggregation: Map<UndirectedEdge<Aggregate>, List<UndirectedClassEdgeWithCommitInfo>>
    val weightsPerEdges: Map<UndirectedEdge<Aggregate>, Long>
    val commitsPerEdges: Map<UndirectedEdge<Aggregate>, Long>
    val totalWeight: Long
    var totalEdgesCount: Long
    var totalNodesCount: Long

    init {
        with(context) {
            groupByAggregation = context.filteredCommits
                    .createFullyConnectedEdges()
                    .matchingNodeSelection()
                    .groupByAggregation()
            commitsPerEdges = groupByAggregation.commitCount()

            weightsPerEdges = groupByAggregation.calculateWeights()
            totalEdgesCount = weightsPerEdges.noInnerAggregateChanges().keys.size.toLong()

            totalWeight = weightsPerEdges.values.sum()
            totalNodesCount = weightsPerEdges.keys.flatMap { sequenceOf(it.from, it.to) }.distinct().size.toLong()
        }
    }

    private fun Map<UndirectedEdge<ClassReference>, List<VcsCommit>>.matchingNodeSelection(): Map<UndirectedEdge<ClassReference>, List<VcsCommit>>{
        if (context.detailsConfig.nodeSelection.let { it.className == "" && it.classPackage == "" }) return this

        return filter { (edge, _) -> edge.from.matchingSelection() || edge.to.matchingSelection() }
    }

    private fun ClassReference.matchingSelection() = included(context.detailsConfig.nodeSelection.className, context.detailsConfig.nodeSelection.classPackage)


    private fun List<VcsCommit>.createFullyConnectedEdges() = parallelStream()
            .flatMap { commit ->
                if (commit.files.size > context.detailsConfig.ignoreCommitsAboveFileCount) {
                    Stream.empty()
                } else {
                    commit.allCombinations().stream()
                }
            }.toList()
            .groupBy { (clsPair, _) -> clsPair }
            .mapValues { (_, values) -> values.map { it.second }.distinct() }

    private fun Map<UndirectedEdge<ClassReference>, List<VcsCommit>>.groupByAggregation(): Map<UndirectedEdge<Aggregate>, List<UndirectedClassEdgeWithCommitInfo>> {
        return entries
                .groupBy { it.key.transform { context.aggregation(this) } }
                .mapValues { (_, values) -> values.map { UndirectedClassEdgeWithCommitInfo(it.key, it.value) } }
    }

    // IMPROVEMENT: use LOC instead of file count
    // IMPROVEMENT: use relation based on commit time - e.g. project is growing over time and earlier commits are weighted based on total file count
    private fun Map<UndirectedEdge<Aggregate>, List<UndirectedClassEdgeWithCommitInfo>>.calculateWeights(): Map<UndirectedEdge<Aggregate>, Long> {
        return when (context.detailsConfig.componentEdgeAggregationMethod) {
            EdgeAggregation.ClassRatioWithCommitSize -> mapValues { entry ->
                entry.value
                        .groupBy { it.commits.map { change -> (change.message + change.time) } }
                        .values.sumOf { changes ->
                            val leftNode = entry.key.sortedNodes().first()
                            val rightNode = entry.key.sortedNodes().last()
                            val classes = changes.flatMap { it.edge.sortedNodes() }.distinct()

                            if (entry.key.isLoop()) {
                                (100.0 * classes.size * context.stats.sizeNormalizationFactor(leftNode)).toLong()
                            } else {
                                val changesLeft = classes.count { context.aggregation(it) == leftNode }.toDouble() * context.stats.sizeNormalizationFactor(leftNode)
                                val changesRight = classes.count { context.aggregation(it) == rightNode }.toDouble() * context.stats.sizeNormalizationFactor(rightNode)
                                val ratio = min(changesLeft, changesRight) / max(changesLeft, changesRight)

                                (100.0 * ratio * classes.size).toLong()
                            }
                        }
            }
            EdgeAggregation.TouchedClassesOfCommit -> mapValues { entry ->
                entry.value
                        .groupBy { it.commits.map { change -> (change.message + change.time) } }
                        .values.sumByDouble { changes ->
                            changes.flatMap { it.edge.sortedNodes() }.distinct()
                                    .sumByDouble { context.stats.sizeNormalizationFactor(it) }
                        }.roundToLong()
            }
            EdgeAggregation.TotalTouchedClasses -> mapValues { entry ->
                entry.value
                        .flatMap { it.edge.sortedNodes() }.distinct()
                        .sumByDouble { context.stats.sizeNormalizationFactor(it) }
                        .roundToLong()
            }
            EdgeAggregation.CommitCount -> mapValues { entry ->
                commitsPerEdges[entry.key] ?: 0L
            }
            EdgeAggregation.GraphConnections -> mapValues { entry -> entry.value.sumOf { it.commits.size.toLong() } }
        }
    }

    private fun Map<UndirectedEdge<Aggregate>, List<UndirectedClassEdgeWithCommitInfo>>.commitCount(): Map<UndirectedEdge<Aggregate>, Long> {
        return mapValues { entry ->
            entry.value.flatMap { it.commits }
                    .distinctBy { it.message + it.time }
                    .count().toLong()
        }
    }
}

class UndirectedClassEdgeWithCommitInfo(val edge: UndirectedEdge<ClassReference>, val commits: List<VcsCommit>)

class VisibleVcsGraph(val context: VcsVisualization) {
    private val maxValidWeight: Long
    val sumWeight: Long

    val edges: Map<UndirectedEdge<Aggregate>, Long>
    val nodes: Map<Aggregate, Long>

    init {
        with(context) {
            maxValidWeight = pickWeightByNumberOfEdgesToShow(aggregatedGraph.weightsPerEdges.noInnerAggregateChanges())

            val relevantWeightsPerComponent = aggregatedGraph.weightsPerEdges.visibleInnerComponentChangesOrOverMinimumWeight()
            sumWeight = relevantWeightsPerComponent.values.sum()

            edges = aggregatedGraph.weightsPerEdges.noInnerAggregateChanges().takeHighestWeightedEdges()

            val nodesWithWeightFromEdges = edges.nodesOnly().map { it to it.weight() }.toMap()
            val isolatedNodesWithWeight = aggregatedGraph.weightsPerEdges.nodesOnly()
                    .filterNot { it in nodesWithWeightFromEdges.keys }
                    .map { it to it.weight() }
                    .sortedByDescending { (_, weight) -> weight }
                    .take(detailsConfig.showMaximumNumberOfIsolatedNodes)

            nodes = (nodesWithWeightFromEdges + isolatedNodesWithWeight).toMap()

        }
    }

    private fun Map<UndirectedEdge<Aggregate>, Long>.nodesOnly() = keys.flatMap { sequenceOf(it.from, it.to) }.toSet()

    private fun Map<UndirectedEdge<Aggregate>, Long>.takeHighestWeightedEdges() = entries
            .sortedByDescending { (_, weight) -> weight }
            .take(context.detailsConfig.showMaximumNumberOfEdges)
            .map { it.key to it.value }.toMap()

    private fun Map<UndirectedEdge<Aggregate>, Long>.visibleInnerComponentChangesOrOverMinimumWeight() = this.filter { (edge, weight) ->
        weight >= maxValidWeight || (edge.isLoop() && anyEdgeFor(edge.from, maxValidWeight))
    }

    private fun pickWeightByNumberOfEdgesToShow(symmetricWeightedEdges: Map<UndirectedEdge<Aggregate>, Long>): Long {
        return symmetricWeightedEdges.values.asSequence().sortedDescending()
                .mapIndexed { edges, weight -> weight to edges }
                .groupBy { (weight, _) -> weight }
                .mapValues { it.value.map { it.second }.maxOrNull() ?: 0 }
                .filter { (_, edges) -> edges < context.detailsConfig.showMaximumNumberOfEdges }
                .map { it.key }
                .minOrNull() ?: 0
    }

}

class AggregateDetails(visualizationContext: VcsVisualization) {

    private val normalizationExponent: Double = visualizationContext.detailsConfig.sizeNormalization
    private val aggregation = visualizationContext.aggregation

    private val filesByAggregate: Map<Aggregate, Int>
    private val layersByAggregate: Map<Aggregate, List<String>>
    private val componentsByAggregate: Map<Aggregate, List<String>>

    private val totalFileCount: Int

    init {
        with(visualizationContext) {
            val aggregated = context.totalFiles.groupBy(aggregation)

            filesByAggregate = aggregated.mapValues { it.value.size }
            layersByAggregate = aggregated.mapValues { it.value.map { cls -> cls.layer(visualizationConfiguration).name }.distinct() }
            componentsByAggregate = aggregated.mapValues { it.value.map { cls -> cls.diagramPath(visualizationConfiguration) }.distinct() }

            totalFileCount = filesByAggregate.values.sum()
        }
    }

    fun numberOfAggregates() = filesByAggregate.keys.size

    fun totalFileCount(aggregate: Aggregate) = filesByAggregate.getOrDefault(aggregate, 0)

    fun layer(aggregate: Aggregate): String? {
        return layersByAggregate.getOrDefault(aggregate, emptyList())
                .takeIf { it.size == 1 }
                ?.first()
    }

    fun component(aggregate: Aggregate): String? {
        return componentsByAggregate.getOrDefault(aggregate, emptyList())
                .takeIf { it.size == 1 }
                ?.first()
    }

    fun sizeNormalizationFactor(clazz: ClassReference) = sizeNormalizationFactor(aggregation(clazz))

    fun sizeNormalizationFactor(aggregate: Aggregate): Double {
        if (filesByAggregate.isEmpty()) return 1.0

        return (1 / sizeRatio(aggregate)).pow(normalizationExponent)
    }

    fun sizeRatio(aggregate: Aggregate) = filesByAggregate.getOrDefault(aggregate, 0).toDouble() / totalFileCount

}


// needed for Classes with same name under different package
data class Aggregate(val key: String, val display: String = key)

private fun Map<UndirectedEdge<Aggregate>, Long>.anyEdgeFor(aggregate: Aggregate, minimumWeight: Long) = this
        .any { (edge, weight) -> edge.contains(aggregate) && weight >= minimumWeight }

private fun Map<UndirectedEdge<Aggregate>, Long>.noInnerAggregateChanges() = filterNot { it.key.isLoop() }

