package com.kn.diagrams.generator.generator.vcs

import com.kn.diagrams.generator.ProgressBar
import com.kn.diagrams.generator.builder.DotDiagramBuilder
import com.kn.diagrams.generator.clamp
import com.kn.diagrams.generator.config.*
import com.kn.diagrams.generator.default
import com.kn.diagrams.generator.generator.*
import com.kn.diagrams.generator.generator.code.layer
import com.kn.diagrams.generator.graph.ClassReference
import com.kn.diagrams.generator.graph.ProjectClassification
import com.kn.diagrams.generator.graph.included
import com.kn.diagrams.generator.throwExceptionIfCanceled
import java.awt.Color
import kotlin.math.*

fun VcsAnalysis.buildDiagram(actions: VcsVisualization.() -> Unit): List<Pair<String, String>> {
    ProgressBar.text = "Diagram is generated"

    return VcsVisualization(this)
            .apply(actions)
            .buildDiagram()
}

class VcsVisualization(val context: VcsAnalysis) {
    val detailsConfig: VcsDiagramDetails
        get() = context.config.details
    val aggregation: ClassReference.() -> Aggregate = aggregation()

    val dot = DotDiagramBuilder()
    val visualizationConfiguration: DiagramVisualizationConfiguration = context.config.visualizationConfig()
    val stats = AggregateDetails(this)
    val aggregatedGraph = AggregatedVcsGraph(this)
    val visibleGraph = VisibleVcsGraph(this)

    fun Aggregate.commitCount() = UndirectedEdge(this, this).commitCount()
    fun UndirectedEdge<Aggregate>.commitCount() = aggregatedGraph.commitsPerEdges.getOrDefault(this, null) ?: 0

    fun Aggregate.nodeSize(): Double? {
        return when(detailsConfig.nodeSize){
            NodeSizing.None -> null
            NodeSizing.FileCount -> detailsConfig.nodeSizeFactor * stats.sizeRatio(this) * sqrt(stats.numberOfAggregates().toDouble())
            NodeSizing.WeightDistribution -> detailsConfig.nodeSizeFactor * relativeWeight() * sqrt(stats.numberOfAggregates().toDouble())
        }
    }

    fun Aggregate.fontSize(): Double? {
        return when(detailsConfig.nodeSize){
            NodeSizing.None -> null
            NodeSizing.FileCount -> detailsConfig.nodeSizeFactor * 14.0 * (1 + nodeSize().default(0.0) )
            NodeSizing.WeightDistribution -> detailsConfig.nodeSizeFactor * 14.0 * (1+ nodeSize().default(0.0))
        }
    }

    fun UndirectedEdge<Aggregate>.weightBasedPenWidth(): Int? {
        if (detailsConfig.coloredEdgeWidthFactor <= 0 || detailsConfig.nodeAggregation == VcsNodeAggregation.None) return null

        val redPercent = 1.0 * visibleGraph.edges[this]!! / visibleGraph.sumWeight
        return sqrt(ceil(redPercent * 100 * detailsConfig.coloredEdgeWidthFactor)).toInt().clamp(1, 50)
    }

    fun UndirectedEdge<Aggregate>.weightBasedColor(): String? {
        if (detailsConfig.coloredEdgeFactor <= 0
                || detailsConfig.nodeAggregation == VcsNodeAggregation.None
                || detailsConfig.edgeColorCoding == EdgeColorCoding.None) return null

        val redPercent = 1.0 * visibleGraph.edges[this]!! / visibleGraph.sumWeight

        return Color((redPercent * detailsConfig.coloredEdgeFactor * 255).toInt().clamp(0, 255), 0, 0).toHex("#")
    }

    fun Aggregate.fileCount() = stats.totalFileCount(this)

    fun Aggregate.weight() = aggregatedGraph.weightsPerEdges.getOrDefault(UndirectedEdge(this, this), 0)

    fun Aggregate.relativeWeight() = 1.0 * visibleGraph.nodes.getOrDefault(this, 0) / visibleGraph.sumWeight
    fun UndirectedEdge<Aggregate>.relativeWeight() = 1.0 * visibleGraph.edges.getOrDefault(this, 0) / visibleGraph.sumWeight

    fun Aggregate.relativeTotalWeight() = 1.0 * visibleGraph.nodes.getOrDefault(this, 0) / aggregatedGraph.totalWeight
    fun UndirectedEdge<Aggregate>.relativeTotalWeight() = 1.0 * visibleGraph.edges.getOrDefault(this, 0) / aggregatedGraph.totalWeight

    fun Aggregate.component() = stats.component(this)
    fun Aggregate.layer() = stats.layer(this)

    fun Aggregate.weightOrStructureBasedColor(): String? {
        return when (detailsConfig.nodeColorCoding) {
            NodeColorCoding.Layer -> layer()?.staticColor()?.toHex("#")
            NodeColorCoding.Component -> component()?.staticColor()?.toHex("#")
            NodeColorCoding.WeightDistribution -> {
                val red = (255.0 * detailsConfig.coloredNodeFactor * weight() / visibleGraph.sumWeight).toInt().clamp(0, 255)
                Color(255, 255 - red, 255 - red).toHex("#")
            }
            NodeColorCoding.None -> null
        }
    }

    private fun aggregation(): ClassReference.() -> Aggregate {
        return when (detailsConfig.nodeAggregation) {
            VcsNodeAggregation.Component -> {
                {
                    throwExceptionIfCanceled()
                    Aggregate(diagramPath(visualizationConfiguration))
                }
            }
            VcsNodeAggregation.ComponentAndLayer -> {
                {
                    throwExceptionIfCanceled()
                    Aggregate(diagramPath(visualizationConfiguration) + " [" + layer(visualizationConfiguration).name + "]")
                }
            }
            VcsNodeAggregation.Layer -> {
                {
                    throwExceptionIfCanceled()
                    Aggregate(layer(visualizationConfiguration).name)
                }
            }
            VcsNodeAggregation.None -> {
                {
                    throwExceptionIfCanceled()
                    Aggregate(diagramId(), name)
                }
            }
        }
    }


    fun buildDiagram() = listOf("VcsDiagram.puml" to dot.create().attacheMetaData(context.config))
}

fun VcsConfiguration.visualizationConfig() = DiagramVisualizationConfiguration(
        rootNode = null,
        projectClassification,
        showPackageLevels = details.showPackageLevels,
        showClassGenericTypes = false,
        showClassMethods = false,
        showMethodParametersTypes = false,
        showMethodParametersNames = false,
        showMethodReturnType = false,
        showCallOrder = false,
        showDetailedClass = false
)

data class Layer(val name: String, val color: String)


fun String.staticColor(): Color {
    val r = (hashCode() * 5) % 255
    val g = (hashCode() * 43) % 255
    val b = (hashCode() * 73) % 255

    return Color(r.absoluteValue, g.absoluteValue, b.absoluteValue)
}

fun Double.percent() = round(this * 1000) / 10
