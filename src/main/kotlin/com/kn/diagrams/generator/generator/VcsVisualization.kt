package com.kn.diagrams.generator.generator

import com.kn.diagrams.generator.ProgressBar
import com.kn.diagrams.generator.builder.DotDiagramBuilder
import com.kn.diagrams.generator.clamp
import com.kn.diagrams.generator.config.*
import com.kn.diagrams.generator.graph.ClassReference
import com.kn.diagrams.generator.graph.isIncludedAndNotExcluded
import com.kn.diagrams.generator.throwExceptionIfCanceled
import java.awt.Color
import kotlin.math.*

fun VcsAnalysis.buildDiagram(actions: VcsVisualization.() -> Unit): List<Pair<String, String>> {
    ProgressBar.text = "Diagram is generated"

    return VcsVisualization(this)
            .apply(actions)
            .buildDiagram()
}

class VcsVisualization(val context: VcsAnalysis){
    val detailsConfig: VcsDiagramDetails
       get() = context.config.details
    val aggregation: ClassReference.() -> Aggregate = aggregation()

    val dot = DotDiagramBuilder()
    val visualizationConfiguration: DiagramVisualizationConfiguration = context.config.visualizationConfig()
    val stats = AggregateDetails(this)
    val aggregatedGraph = AggregatedVcsGraph(this)
    val visibleGraph = VisibleVcsGraph(this)

    fun Aggregate.commitCount() = UndirectedEdge(this, this).commitCount()

    fun UndirectedEdge<Aggregate>.commitCount() = aggregatedGraph.commitsPerEdges.getOrDefault(this, 0)

    fun Aggregate.nodeSize(): Double {
        if (stats.filesByAggregate.isEmpty()) return 0.0

        val componentCount = stats.filesByAggregate.keys.size.toDouble()
        return stats.sizeRatio(this) * sqrt(componentCount)
    }

    fun UndirectedEdge<Aggregate>.weightBasedPenWidth(): Int? {
        if(detailsConfig.coloredEdgeWidthFactor <= 0 || detailsConfig.nodeAggregation == VcsNodeAggregation.None) return null

        val redPercent = 1.0 * visibleGraph.edges[this]!! / visibleGraph.sumWeight
        return sqrt(ceil(redPercent * 100 * detailsConfig.coloredEdgeWidthFactor)).toInt().clamp(1, 50)
    }

    fun UndirectedEdge<Aggregate>.weightBasedColor(): String? {
        if(detailsConfig.coloredEdgeFactor <= 0
                || detailsConfig.nodeAggregation == VcsNodeAggregation.None
                || detailsConfig.edgeColorCoding == EdgeColorCoding.None) return null

        val redPercent = 1.0 * visibleGraph.edges[this]!! / visibleGraph.sumWeight

        return Color((redPercent * detailsConfig.coloredEdgeFactor * 255).toInt().clamp(0, 255), 0, 0).toHex("#") // TODO missing in stats
    }

    fun Aggregate.fileCount() = stats.filesByAggregate.getOrDefault(this, 0)

    private fun Aggregate.weight() = UndirectedEdge(this, this).weight()
    private fun UndirectedEdge<Aggregate>.weight() = visibleGraph.edges.getOrDefault(this, 0)

    fun Aggregate.relativeWeight() = UndirectedEdge(this, this).relativeWeight()
    fun UndirectedEdge<Aggregate>.relativeWeight() = 1.0 * visibleGraph.edges.getOrDefault(this, 0) / visibleGraph.sumWeight

    fun Aggregate.relativeTotalWeight() = UndirectedEdge(this, this).relativeTotalWeight()
    fun UndirectedEdge<Aggregate>.relativeTotalWeight() = 1.0 * visibleGraph.edges.getOrDefault(this, 0) / aggregatedGraph.totalWeight

    fun Aggregate.weightOrStructureBasedColor(): String? {
        return when(detailsConfig.nodeColorCoding){
            NodeColorCoding.Layer -> stats.layer(this)?.staticColor()?.toHex("#")
            NodeColorCoding.Component -> stats.component(this)?.staticColor()?.toHex("#")
            NodeColorCoding.WeightDistribution -> {
                val red = (255.0 * detailsConfig.coloredNodeFactor * weight() / visibleGraph.sumWeight).toInt().clamp(0, 255)
                Color(255, 255 - red, 255 - red).toHex()
            }
            NodeColorCoding.None -> null
        }
    }

    private fun aggregation(): ClassReference.() -> Aggregate {
        return when (detailsConfig.nodeAggregation) {
            VcsNodeAggregation.Component -> {{
                throwExceptionIfCanceled()
                Aggregate(diagramPath(visualizationConfiguration))
            }}
            VcsNodeAggregation.ComponentAndLayer -> {{
                throwExceptionIfCanceled()
                Aggregate(diagramPath(visualizationConfiguration) + " [" + layer(visualizationConfiguration) + "]")
            }}
            VcsNodeAggregation.Layer -> {{
                throwExceptionIfCanceled()
                Aggregate(layer(visualizationConfiguration))
            }}
            VcsNodeAggregation.None -> {{
                throwExceptionIfCanceled()
                Aggregate(diagramId(), name)
            }}
        }
    }




    fun buildDiagram() = listOf("vcs" to dot.create().attacheMetaData(context.config))
}

// needed for Classes with same name under different package
data class Aggregate(val key: String, val display: String = key)


private fun Map<UndirectedEdge<Aggregate>, Int>.anyEdgeFor(aggregate: Aggregate, minimumWeight: Int) = this
        .any { (edge, weight) -> edge.contains(aggregate) && weight >= minimumWeight }

private fun Map<UndirectedEdge<Aggregate>, Int>.noInnerAggregateChanges() = filterNot { it.key.isLoop() }

class AggregatedVcsGraph(val context: VcsVisualization){
    val weightsPerEdges: Map<UndirectedEdge<Aggregate>, Int>
    val commitsPerEdges: Map<UndirectedEdge<Aggregate>, Int>
    val totalWeight: Int
    var totalEdgesCount: Int

    init {
        with(context){
            val groupByAggregation = this.context.graphEdges.groupByAggregation()
            commitsPerEdges = groupByAggregation.commitCount()

            // TODO percent of changes over all classes - time range could be earlier - but size is related to later added files
            weightsPerEdges = groupByAggregation.calculateWeights()
            totalEdgesCount = weightsPerEdges.noInnerAggregateChanges().keys.size

            totalWeight = weightsPerEdges.values.sum()
        }
    }

    private fun Map<UndirectedEdge<ClassReference>, List<VcsCommit>>.groupByAggregation(): Map<UndirectedEdge<Aggregate>, List<Map.Entry<UndirectedEdge<ClassReference>, List<VcsCommit>>>> {
        return entries.groupBy { it.key.transform { context.aggregation(this) } }
    }

    private fun Map<UndirectedEdge<Aggregate>, List<Map.Entry<UndirectedEdge<ClassReference>, List<VcsCommit>>>>.calculateWeights(): Map<UndirectedEdge<Aggregate>, Int> {
        // TODO normalize based on LOC of file/changes - hard to calculate since LOC will change over time - normalize value would be time related
        return when (context.detailsConfig.componentEdgeAggregationMethod) {
            EdgeAggregation.ClassRatioWithCommitSize -> mapValues { entry ->
                entry.value
                        .groupBy { it.value.map { change -> (change.message + change.time) } }
                        .values.sumBy { changes ->
                            val leftNode = entry.key.sortedNodes().first()
                            val rightNode = entry.key.sortedNodes().last()
                            val classes = changes.flatMap { it.key.sortedNodes() }.distinct()

                            if(entry.key.isLoop()){
                                (100.0 * classes.size * context.stats.sizeNormalizationFactor(leftNode)).toInt()
                            }else{
                                val changesLeft = classes.count { context.aggregation(it) == leftNode }.toDouble() * context.stats.sizeNormalizationFactor(leftNode)
                                val changesRight = classes.count { context.aggregation(it) == rightNode }.toDouble() * context.stats.sizeNormalizationFactor(rightNode)
                                val ratio = min(changesLeft, changesRight) / max(changesLeft, changesRight)

                                (100.0 * ratio * classes.size).toInt()
                            }
                        }
            }
            EdgeAggregation.TouchedClassesOfCommit -> mapValues { entry ->
                entry.value
                        .groupBy { it.value.map { change -> (change.message + change.time) } }
                        .values.sumByDouble { changes ->
                            changes.flatMap { it.key.sortedNodes() }.distinct()
                                    .sumByDouble { context.stats.sizeNormalizationFactor(it) }
                        }.roundToInt()
            }
            EdgeAggregation.TotalTouchedClasses -> mapValues { entry ->
                entry.value
                        .flatMap { it.key.sortedNodes() }.distinct()
                        .sumByDouble { context.stats.sizeNormalizationFactor(it) }
                        .roundToInt()
            }
            EdgeAggregation.CommitCount -> commitCount()
            EdgeAggregation.GraphConnections -> mapValues { entry -> entry.value.size }
        }
    }

    private fun Map<UndirectedEdge<Aggregate>, List<Map.Entry<UndirectedEdge<ClassReference>, List<VcsCommit>>>>.commitCount(): Map<UndirectedEdge<Aggregate>, Int> {
        return mapValues { entry ->
            entry.value
                    .flatMap { it.value.map { change -> change.message + change.time } }
                    .distinct().count()
        }
    }
}


class VisibleVcsGraph(val context: VcsVisualization){
    private val maxValidWeight: Int
    val sumWeight: Int

    val edges: Map<UndirectedEdge<Aggregate>, Int>
    val nodes: Map<Aggregate, Int>

    init {
        with(context){
            // TODO chose weight to display nodes/edges or just take the first x edges - but how to chose nodes?
            maxValidWeight = pickWeightByNumberOfEdgesToShow(aggregatedGraph.weightsPerEdges.noInnerAggregateChanges())

            val relevantWeightsPerComponent = aggregatedGraph.weightsPerEdges.visibleInnerComponentChangesOrOverMinimumWeight()
            sumWeight = relevantWeightsPerComponent.values.sum()

            edges = relevantWeightsPerComponent.noInnerAggregateChanges()
            nodes = relevantWeightsPerComponent.keys.flatMap { sequenceOf(it.from, it.to) }.distinct()
                    .map { it to edges.getOrDefault(UndirectedEdge(it, it), 0) }
                    .toMap()
        }
    }

    private fun Map<UndirectedEdge<Aggregate>, Int>.visibleInnerComponentChangesOrOverMinimumWeight() = this.filter { (edge, weight) ->
        weight >= maxValidWeight || (edge.isLoop() && anyEdgeFor(edge.from, maxValidWeight))
    }


    private fun pickWeightByNumberOfEdgesToShow(symmetricWeightedEdges: Map<UndirectedEdge<Aggregate>, Int>): Int {
        return symmetricWeightedEdges.values.asSequence().sortedDescending()
                .mapIndexed { edges, weight -> weight to edges }
                .groupBy {  (weight, _) -> weight }
                .mapValues { it.value.map { it.second }.maxOrNull() ?: 0 }
                .filter { (_, edges) -> edges < context.detailsConfig.showMaximumNumberOfEdges }
                .map { it.key }
                .minOrNull() ?: 0
    }

}

class AggregateDetails(visualizationContext: VcsVisualization) {

    val normalizationExponent: Double = visualizationContext.detailsConfig.sizeNormalization
    val aggregation = visualizationContext.aggregation

    val filesByAggregate: Map<Aggregate, Int>
    val layersByAggregate: Map<Aggregate, List<String>>
    val componentsByAggregate: Map<Aggregate, List<String>>

    private val totalFileCount: Int

    init {
        with(visualizationContext){
            val aggregated = context.totalFiles.groupBy(aggregation)

            filesByAggregate = aggregated.mapValues { it.value.size }
            layersByAggregate = aggregated.mapValues { it.value.map { cls -> cls.layer(visualizationConfiguration) }.distinct() }
            componentsByAggregate = aggregated.mapValues { it.value.map { cls -> cls.diagramPath(visualizationConfiguration) }.distinct() }

            totalFileCount = filesByAggregate.values.sum()
        }
    }

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

    fun sizeNormalizationFactor(aggregate: Aggregate): Double{
        if (filesByAggregate.isEmpty()) return 1.0

        return (1 / sizeRatio(aggregate)).pow(normalizationExponent)
    }

    fun sizeRatio(aggregate: Aggregate) = filesByAggregate.getOrDefault(aggregate, 0).toDouble() / totalFileCount

}


fun VcsConfiguration.visualizationConfig() = DiagramVisualizationConfiguration(
        rootNode = null,
        projectClassification,
        showPackageLevels = 1,
        showClassGenericTypes = false,
        showClassMethods = false,
        showMethodParametersTypes = false,
        showMethodParametersNames = false,
        showMethodReturnType = false,
        showCallOrder = false,
        showDetailedClass = false
)

fun ClassReference.layer(config: DiagramVisualizationConfiguration): String {
    // TODO simple way of configuring this?
    with(config.projectClassification){
        val customLayer = customLayers.entries
                // TODO unify filtering
                .firstOrNull { (_, pattern) -> isIncludedAndNotExcluded("", pattern.name) { name }
                        && isIncludedAndNotExcluded("", pattern.path) { path } }
                ?.key
        val layer = customLayer ?: sequenceOf<(ClassReference) -> String?>(
                { cls -> "Test".takeIf { cls.isTest() }  },
                { cls -> "Interface Structure".takeIf { cls.isInterfaceStructure() }  },
                { cls -> "Data Structure".takeIf { cls.isDataStructure() }  },
                { cls -> "Client".takeIf { cls.isClient() }  },
                { cls -> "Data Access".takeIf { cls.isDataAccess() }  },
                { cls -> "Entry Point".takeIf { cls.isEntryPoint() }  },
                { cls -> "Mapping".takeIf { cls.isMapping() }  },
        ).mapNotNull { it(this@layer) }.firstOrNull()

        if(layer == null) println(path)

        return layer ?: "no layer"
    }

}


fun String.staticColor(): Color{
    val r = (hashCode() * 5) % 255
    val g = (hashCode() * 43) % 255
    val b = (hashCode() * 73) % 255

    return Color(r.absoluteValue, g.absoluteValue, b.absoluteValue)
}

fun Double.percent() = round(this * 1000) / 10
