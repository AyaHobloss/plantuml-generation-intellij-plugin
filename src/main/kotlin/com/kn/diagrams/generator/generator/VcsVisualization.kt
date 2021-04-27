package com.kn.diagrams.generator.generator

import com.kn.diagrams.generator.ProgressBar
import com.kn.diagrams.generator.builder.DotDiagramBuilder
import com.kn.diagrams.generator.builder.DotShape
import com.kn.diagrams.generator.builder.addLink
import com.kn.diagrams.generator.clamp
import com.kn.diagrams.generator.config.*
import com.kn.diagrams.generator.graph.ClassReference
import com.kn.diagrams.generator.graph.isIncludedAndNotExcluded
import com.kn.diagrams.generator.throwExceptionIfCanceled
import java.awt.Color
import kotlin.math.*

fun VcsAnalysis.buildDiagram(builder: VcsVisualization.() -> Unit): List<Pair<String, String>> {
    ProgressBar.text = "Diagram is generated"
    return VcsVisualization(this).apply(builder).buildDiagram()
}

class VcsVisualization(val context: VcsAnalysis){
    val detailsConfig: VcsDiagramDetails
       get() = context.config.details
    val aggregation: ClassReference.() -> String = aggregation()
    var maxValidWeight: Int = 0

    private val dot = DotDiagramBuilder()
    private val visualizationConfiguration: DiagramVisualizationConfiguration = context.config.visualizationConfig()
    private val stats = Statistics(context, aggregation, detailsConfig.sizeNormalization)

    fun showChangesAggregated() {
        val groupByAggregation = context.graphEdges.groupByAggregation()
        val commitsPerComponent = groupByAggregation.commitCount()
        val weightsPerComponent = groupByAggregation.calculateWeights()

        // TODO chose weight to display nodes/edges or just take the first x edges - but how to chose nodes?
        maxValidWeight = pickWeightByNumberOfEdgesToShow(weightsPerComponent.noInnerComponentChanges())

        val relevantWeightsPerComponent = weightsPerComponent.visibleInnerComponentChangesOrOverMinimumWeight()
        val sumTotalWeight = weightsPerComponent.values.sum()
        val sumWeight = relevantWeightsPerComponent.values.sum()

        dot.notes = "'maximum "+relevantWeightsPerComponent.noInnerComponentChanges().size + " edges shown out of " + weightsPerComponent.noInnerComponentChanges().keys.size

        relevantWeightsPerComponent.keys.flatMap { it.sortedNodes() }.distinct().forEach { component ->
            dot.nodes.add(DotShape(component, component).with {
                val edgeCount = relevantWeightsPerComponent[UndirectedEdge(component, component)] ?: 0
                val red = (255.0 * detailsConfig.coloredNodeFactor * edgeCount / sumWeight).toInt().clamp(0, 255)

                tooltip = "inner changes = $edgeCount / ${(1.0 * edgeCount / sumWeight).percent()}% / ${(1.0 * edgeCount / sumTotalWeight).percent()}T%&#013;total files: " +
                        stats.filesByComponent?.getOrDefault(component, 0) + "; commits: "+commitsPerComponent[UndirectedEdge(component, component)]
                // TODO percent of changes over all classes - time range could be earlier - but size is related to later added files
                fillColor = "#" + Color(255, 255 - red, 255 - red).toHex()
                margin = stats.nodeSize(component)
                style = "filled"
            })
        }

        relevantWeightsPerComponent
                .filterNot { (edge, _) -> edge.isLoop() }
                .forEach { (edge, weight) ->
            dot.addLink(edge.from, edge.to) {
                val redPercent = 1.0 * weight / sumWeight
                label = "$weight / ${redPercent.percent()}% / ${(1.0 * weight / sumTotalWeight).percent()}T%"
                tooltip = label + "; commits: " + commitsPerComponent[edge]
                color = "#" + Color((redPercent * detailsConfig.coloredEdgeFactor * 255).toInt().clamp(0, 255), 0, 0).toHex()
                penwidth = sqrt(ceil(redPercent * 100 * detailsConfig.coloredEdgeWidthFactor)).toInt().clamp(1, 50)
                arrowHead = "none"
            }
        }
    }

    fun showChangesWithoutAggregation() {
        val allSimplifiedEdges = context.graphEdges.mapValues { it.value.size }

        maxValidWeight = pickWeightByNumberOfEdgesToShow(allSimplifiedEdges)
        val edges = allSimplifiedEdges.filter { (_, weight) -> weight >= maxValidWeight }
        dot.notes = "'"+edges.size + " edges shown out of " + allSimplifiedEdges.keys.size

        val nodes = edges.keys.flatMap { it.sortedNodes() }.distinct()

        val componentsWithColor = nodes
                .map { it.diagramPath(visualizationConfiguration) }.distinct()
                .map { it to it.staticColor() }
                .toMap()

        nodes.forEach { node ->
            dot.nodes.add(DotShape(node.name, node.diagramId()).with {
                tooltip = node.diagramPath(visualizationConfiguration)

                if(detailsConfig.colorizeFilesWithSameComponent){
                    fillColor = "#"+componentsWithColor[tooltip]!!.toHex()
                    style = "filled"
                }
            })
        }

        edges.forEach { (edge, weight) ->
            dot.addLink(edge.sortedNodes().first().diagramId(), edge.sortedNodes().last().diagramId()) {
                label = weight.toString()
                tooltip = label
                arrowHead = "none"
            }
        }
    }

    private fun aggregation(): ClassReference.() -> String {
        return when (detailsConfig.nodeAggregation) {
            VcsNodeAggregation.Component -> {{
                throwExceptionIfCanceled()
                diagramPath(visualizationConfiguration)
            }}
            VcsNodeAggregation.ComponentAndLayer -> {{
                throwExceptionIfCanceled()
                diagramPath(visualizationConfiguration) + " [" + layer(visualizationConfiguration) + "]"
            }}
            VcsNodeAggregation.Layer -> {{
                throwExceptionIfCanceled()
                layer(visualizationConfiguration)
            }}
            VcsNodeAggregation.None -> {{
                throwExceptionIfCanceled()
                name
            }}
        }
    }

    private fun Map<UndirectedEdge<String>, Int>.visibleInnerComponentChangesOrOverMinimumWeight() = this.filter { (edge, weight) ->
        weight >= maxValidWeight || (edge.isLoop() && anyEdgeFor(edge.from, maxValidWeight))
    }

    private fun Map<UndirectedEdge<String>, List<Map.Entry<UndirectedEdge<ClassReference>, List<VcsCommit>>>>.commitCount(): Map<UndirectedEdge<String>, Int> {
        return mapValues { entry ->
            entry.value
                    .flatMap { it.value.map { change -> change.message + change.time } }
                    .distinct().count()
        }
    }

    private fun Map<UndirectedEdge<String>, List<Map.Entry<UndirectedEdge<ClassReference>, List<VcsCommit>>>>.calculateWeights(): Map<UndirectedEdge<String>, Int> {
        // TODO normalize based on LOC of file/changes - hard to calculate since LOC will change over time - normalize value would be time related
        return when (detailsConfig.componentEdgeAggregationMethod) {
            EdgeAggregation.ClassRatioWithCommitSize -> mapValues { entry ->
                entry.value
                        .groupBy { it.value.map { change -> (change.message + change.time) } }
                        .values.sumBy { changes ->
                            val leftNode = entry.key.sortedNodes().first()
                            val rightNode = entry.key.sortedNodes().last()
                            val classes = changes.flatMap { it.key.sortedNodes() }.distinct()

                            if(entry.key.isLoop()){
                                (100.0 * classes.size * stats.sizeNormalizationFactor(leftNode)).toInt()
                            }else{
                                val changesLeft = classes.count { aggregation(it) == leftNode }.toDouble() * stats.sizeNormalizationFactor(leftNode)
                                val changesRight = classes.count { aggregation(it) == rightNode }.toDouble() * stats.sizeNormalizationFactor(rightNode)
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
                                    .sumByDouble { stats.sizeNormalizationFactor(it) }
                        }.roundToInt()
            }
            EdgeAggregation.TotalTouchedClasses -> mapValues { entry ->
                entry.value
                        .flatMap { it.key.sortedNodes() }.distinct()
                        .sumByDouble { stats.sizeNormalizationFactor(it) }
                        .roundToInt()
            }
            EdgeAggregation.CommitCount -> commitCount()
            EdgeAggregation.GraphConnections -> mapValues { entry -> entry.value.size }
        }

    }

    private fun Map<UndirectedEdge<ClassReference>, List<VcsCommit>>.groupByAggregation(): Map<UndirectedEdge<String>, List<Map.Entry<UndirectedEdge<ClassReference>, List<VcsCommit>>>> {
        return entries.groupBy { it.key.transform { aggregation(this) } }
    }

    private fun <T> pickWeightByNumberOfEdgesToShow(symmetricWeightedEdges: Map<UndirectedEdge<T>, Int>): Int {
        return symmetricWeightedEdges.values.asSequence().sortedDescending()
                .mapIndexed { edges, weight -> weight to edges }
                .groupBy {  (weight, _) -> weight }
                .mapValues { it.value.map { it.second }.maxOrNull() ?: 0 }
                .filter { (_, edges) -> edges < detailsConfig.showMaximumNumberOfEdges }
                .map { it.key }
                .minOrNull() ?: 0
    }

    fun buildDiagram() = listOf("vcs" to dot.create().attacheMetaData(context.config))
}




private fun Map<UndirectedEdge<String>, Int>.anyEdgeFor(component: String, minimumWeight: Int) = this
        .any { (edge, weight) -> edge.contains(component) && weight >= minimumWeight }

private fun Map<UndirectedEdge<String>, Int>.noInnerComponentChanges() = filterNot { it.key.isLoop() }


class Statistics(context: VcsAnalysis, val aggregation: ClassReference.() -> String, val normalizationExponent: Double) {

    val filesByComponent: Map<String, Int>?
    val totalFileCount: Int

    init {
        with(context){
            if(config.details.componentEdgeAggregationMethod != EdgeAggregation.GraphConnections){
                filesByComponent = totalFiles.groupBy(aggregation).mapValues { it.value.size }
                totalFileCount = filesByComponent.values.sum()
            } else {
                filesByComponent = null
                totalFileCount = 0
            }
        }
    }

    fun sizeNormalizationFactor(clazz: ClassReference, noneNormalizationValue: Double = 1.0) = sizeNormalizationFactor(aggregation(clazz), noneNormalizationValue)

    fun sizeNormalizationFactor(component: String, noneNormalizationValue: Double = 1.0): Double{
        if (filesByComponent == null) return noneNormalizationValue

        return (1 / sizeRatio(component)).pow(normalizationExponent)
    }

    private fun sizeRatio(component: String) =
            filesByComponent!!.getOrDefault(component, 0).toDouble() / totalFileCount

    fun nodeSize(component: String): Double {
        if (filesByComponent == null) return 0.0

        val componentCount = filesByComponent.keys.size.toDouble()
        return sizeRatio(component) * sqrt(componentCount)
    }
}


fun VcsConfiguration.visualizationConfig() = DiagramVisualizationConfiguration(
        null,
        projectClassification,
        1,
        false,
        false,
        false,
        false,
        false,
        false,
        false
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
