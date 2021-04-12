package com.kn.diagrams.generator.generator

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcsUtil.VcsUtil
import com.kn.diagrams.generator.ProgressBar
import com.kn.diagrams.generator.builder.DotDiagramBuilder
import com.kn.diagrams.generator.builder.DotShape
import com.kn.diagrams.generator.builder.addLink
import com.kn.diagrams.generator.clamp
import com.kn.diagrams.generator.config.*
import com.kn.diagrams.generator.graph.ClassReference
import com.kn.diagrams.generator.throwExceptionIfCanceled
import git4idea.history.GitLogUtil
import org.jetbrains.annotations.NotNull
import java.awt.Color
import java.io.File
import java.util.*
import java.util.stream.Stream
import kotlin.math.*
import kotlin.streams.toList


class GitDiagramGenerator {

    fun createUmlContent(config: GitConfiguration): List<Pair<String, String>> {
        val project = config.rootClass.project

        val gitCommits = loadRawGit(project, config)

        ProgressBar.text = "Commits are processed and filtered"
        val squashedCommitsPerTicket = gitCommits.squashCommitWithSameTicket(config)
        val filteredCommits = squashedCommitsPerTicket.filterCommitsByConfiguration(config)

        ProgressBar.text = "Diagram is generated"
        val dot = DotDiagramBuilder()
        val undirectedWeightedEdges = filteredCommits.createFullyConnectedEdgesFromCommits(config)

        when(config.details.nodeAggregation){
            GitNodeAggregation.None -> dot.showChangesWithoutAggregation(undirectedWeightedEdges, config)
            GitNodeAggregation.Component -> dot.showChangesAggregatedToComponents(undirectedWeightedEdges, config)
        }

        return listOf("githeat" to dot.create().attacheMetaData(config))
    }

    private fun List<GitCommit>.filterCommitsByConfiguration(config: GitConfiguration): List<GitCommit> {
        val visualizationConfiguration = config.visualizationConfig()

        val filter = config.restrictionFilter()
        val includedComponents = config.details.includedComponents.split(";").filterNot { it == "" }.toSet()

        return mapNotNull { commit ->
            val changes = commit.files
                    .filter { filter.acceptClass(it) }
                    .filter { includedComponents.isEmpty() || it.diagramPath(visualizationConfiguration) in includedComponents }

            if (changes.size > 1 && commit.message.matchesPattern(config.details)) {
                GitCommit(commit.time, commit.message, changes)
            } else null
        }
    }

    private fun List<GitCommit>.createFullyConnectedEdgesFromCommits(config: GitConfiguration) = parallelStream()
            .flatMap { commit ->
                if (commit.files.size > config.details.ignoreCommitsAboveFileCount) {
                    Stream.empty()
                } else {
                    commit.allCombinations().stream()
                }
            }.toList().groupBy { (clsPair, _) -> clsPair }

    private fun String.matchesPattern(commitFilter: GitDiagramDetails) = when(commitFilter.commitFilter){
        CommitFilter.All -> true
        CommitFilter.Matching -> this.contains(commitFilter.commitPattern)
        CommitFilter.NotMatching -> !this.contains(commitFilter.commitPattern)
    }

    private fun Map<Set<String>, List<Map.Entry<Set<ClassReference>, List<Pair<Set<ClassReference>, GitCommit>>>>>.calculateWeights(config: GitConfiguration, visualizationConfiguration: DiagramVisualizationConfiguration): Map<Set<String>, Int>{
        return when(config.details.componentEdgeAggregationMethod){
            EdgeAggregation.ClassRatioOfCommit -> mapValues { entry ->
                entry.value
                        .groupBy { it.value.map { change -> (change.second.message + change.second.time) } }
                        .values.sumBy { changes ->
                            val classes = changes.flatMap { it.key }.distinct()
                            val changesLeft = classes.count { it.diagramPath(visualizationConfiguration) == entry.key.first() }.toDouble()
                            val changesRight = classes.count { it.diagramPath(visualizationConfiguration) == entry.key.last() }.toDouble()
                            val ratio = min(changesLeft, changesRight) / max(changesLeft, changesRight)

                            (100.0 * ratio).toInt()
                        }
            }
            EdgeAggregation.ClassRatioWithCommitSize -> mapValues { entry ->
                entry.value
                        .groupBy { it.value.map { change -> (change.second.message + change.second.time) } }
                        .values.sumBy { changes ->
                            val classes = changes.flatMap { it.key }.distinct()
                            val changesLeft = classes.count { it.diagramPath(visualizationConfiguration) == entry.key.first() }.toDouble()
                            val changesRight = classes.count { it.diagramPath(visualizationConfiguration) == entry.key.last() }.toDouble()
                            val ratio = min(changesLeft, changesRight) / max(changesLeft, changesRight)

                            (100.0 * ratio * classes.size).toInt()
                        }
            }
            EdgeAggregation.TouchedClassesOfCommit -> mapValues { entry ->
                entry.value
                        .groupBy { it.value.map { change -> (change.second.message + change.second.time) } }
                        .values.sumBy { changes ->
                            changes.flatMap { it.key }.distinct().size
                        }
            }
            EdgeAggregation.TotalTouchedClasses -> mapValues { entry ->
                entry.value
                        .flatMap { it.value.flatMap { change -> change.first } }
                        .distinct().count()
            }
            EdgeAggregation.CommitCount -> mapValues { entry ->
                entry.value
                        .flatMap { it.value.map { change -> change.second.message + change.second.time } }
                        .distinct().count()
            }
            EdgeAggregation.GraphConnections -> mapValues { entry -> entry.value.size }
        }
    }

    private fun DotDiagramBuilder.showChangesAggregatedToComponents(edges: Map<Set<ClassReference>, List<Pair<Set<ClassReference>, GitCommit>>>, config: GitConfiguration) {
        val visualizationConfiguration = config.visualizationConfig()

        val componentEdges = edges.groupByComponent(visualizationConfiguration).calculateWeights(config, visualizationConfiguration)
        val relevantComponentEdges = componentEdges.visibleInnerComponentChangesOrOverMinimumWeight(config.details.minimumWeight)
        val sumEdges = relevantComponentEdges.values.sum()
        // TODO total edges - based on aggregation method

        notes = weightDistribution(componentEdges.noInnerComponentChanges())

        relevantComponentEdges.keys.flatten().distinct().forEach { component ->
            nodes.add(DotShape(component, component).with {
                val edgeCount = relevantComponentEdges[setOf(component)] ?: 0
                val red = (255.0 * config.details.coloredNodeFactor * edgeCount / sumEdges).toInt().clamp(0, 255)

                tooltip = "inner changes = $edgeCount / ${(1.0 * edgeCount / sumEdges).percent()}%"
                fillColor = "#"+Color(255, 255-red, 255-red).toHex()
                style = "filled"
            })
        }

        relevantComponentEdges.forEach{ (componentEdge, edgeCount) ->
            if(componentEdge.size > 1){
                addLink(componentEdge.first(), componentEdge.last()) {
                    val redPercent = 1.0 * edgeCount / sumEdges
                    label = "$edgeCount / ${redPercent.percent()}%"
                    tooltip = label
                    color = "#"+Color((redPercent * config.details.coloredEdgeFactor * 255).toInt().clamp(0, 255), 0, 0).toHex()
                    penwidth = sqrt(ceil(redPercent * 100 * config.details.coloredEdgeWidthFactor)).toInt().clamp(1, 50)
                    arrowHead = "none"
                }
            }
        }
    }

    private fun Map<Set<String>, Int>.visibleInnerComponentChangesOrOverMinimumWeight(minimumWeight: Int) = this.filter { (components, weight) ->
        (components.size == 1 && anyEdgeFor(components.first(), minimumWeight)) || weight >= minimumWeight }

    private fun Map<Set<String>, Int>.anyEdgeFor(component: String, minimumWeight: Int) = any { it.key.contains(component) && it.value >= minimumWeight }

    private fun Map<Set<String>, Int>.noInnerComponentChanges() = filter { it.key.size != 1 }

    private fun Map<Set<ClassReference>, List<Pair<Set<ClassReference>, GitCommit>>>.groupByComponent(visualizationConfiguration: DiagramVisualizationConfiguration) = entries
            .groupBy { (edge, _) -> edge.map { it.diagramPath(visualizationConfiguration) }.toSet() }

    private fun DotDiagramBuilder.showChangesWithoutAggregation(allEdges: Map<Set<ClassReference>, List<Pair<Set<ClassReference>, GitCommit>>>, config: GitConfiguration) {
        val allSimplifiedEdges = allEdges.mapValues { it.value.size }

        val minimumWeight = config.details.minimumWeight
        val edges = allSimplifiedEdges.filter { (_, weight) -> weight >= minimumWeight }
        notes = weightDistribution(allSimplifiedEdges)

        val visualizationConfiguration = config.visualizationConfig()

        val nodes = edges.keys.flatten().distinct()

        val componentsWithColor = nodes
                .map { it.diagramPath(visualizationConfiguration) }.distinct()
                .map { it to it.staticColor() }
                .toMap()

        nodes.forEach { node ->
            this.nodes.add(DotShape(node.name, node.qualifiedName()).with {
                tooltip = node.diagramPath(visualizationConfiguration)

                if(config.details.colorizeFilesWithSameComponent){
                    fillColor = "#"+componentsWithColor[tooltip]!!.toHex()
                    style = "filled"
                }
            })
        }

        edges.forEach { (edge, weight) ->
            addLink(edge.first().qualifiedName(), edge.last().qualifiedName()) {
                label = weight.toString()
                tooltip = label
                arrowHead = "none"
            }
        }
    }

    private fun <T> weightDistribution(symmetricWeightedEdges: Map<Set<T>, Int>): String {
        val numbers = mutableMapOf<Int, Int>()

        symmetricWeightedEdges.values.sortedDescending().forEachIndexed { i, weight ->
            numbers[weight] = i
        }

        return "'weight -> number of displayed edges:\n" + numbers.entries.joinToString("\n") { (weight, i) ->
            "'$weight -> $i"
        }
    }

    private fun List<GitCommit>.squashCommitWithSameTicket(config: GitConfiguration): List<GitCommit> {
        if(!config.details.squashCommitsContainingOneTicketReference) return this

        return groupBy {
            val jiraTags = "\\[.{3,20}]".toRegex().findAll(it.message).map { it.value }.distinct().toList()

            jiraTags
        }.map { (jiraTags, commits) ->
            if (jiraTags.size == 1 && commits.size > 1) {
                val squashedChanges = commits.flatMap { it.files }.distinct()
                val lastTime = commits.map { it.time }.maxOfOrNull { it } ?: -1

                listOf(GitCommit(lastTime, jiraTags[0] + " manual squashed commits", squashedChanges))
            } else {
                commits
            }
        }.flatten()
    }

    private fun loadRawGit(project: @NotNull Project, config: GitConfiguration): List<GitCommit> {
        val resultFile = File("git${config.details.workspaceIdentifier}.csv")
        val changes = mutableListOf<GitCommit>()

        if(resultFile.exists()){
            ProgressBar.text = "GIT repository is loaded from cache file"
            changes.addAll(resultFile.readLines().map { GitCommit(it) })
        }else{
            ProgressBar.text = "GIT repository is loaded"
            val vcsRoot = VcsUtil.getVcsRootFor(project, config.rootClass.containingFile.virtualFile)
            GitLogUtil.readFullDetails(project, vcsRoot!!, { commit ->
                val changedFiles = commit.changes.parallelStream().filterSourceJavaFiles()

                if (changedFiles.isNotEmpty()) {
                    changes.add(GitCommit(
                            commit.commitTime,
                            commit.fullMessage.replace("\r", " ").replace(",", ";").replace("\n", " "),
                            changedFiles.map { ClassReference(it) }
                    ))
                }

                throwExceptionIfCanceled()
            })
            resultFile.writeText(changes.joinToString("\n") { it.toCsv() })
        }

        val timeRange = (config.details.startDay.toDate()?.time ?: 0L) .. (config.details.endDay.toDate()?.time ?: Long.MAX_VALUE)
        return changes.filter { commit -> commit.time in timeRange }.toList()
    }
}

fun Stream<Change>.filterSourceJavaFiles(): List<String> = this
        .map { it.afterRevision?.file?.path?.substringAfter("/src/") ?: ""}
        .filter { it != "" }
        .filter { it.startsWith("main/java") }
        .map { it.substringAfter("main/java/").replace("/", ".").replace(".java", "") }
        .toList()


fun GitConfiguration.visualizationConfig() = DiagramVisualizationConfiguration(
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

fun String?.toDate(): Date? {
    if(this == null || this == "") return null
    val (year, month, day) = this.split("-")
    val calendar = Calendar.getInstance()
    calendar.set(year.toInt(), month.toInt()-1, day.toInt())

    return calendar.time
}

fun String.staticColor(): Color{
    val r = (hashCode() * 5) % 255
    val g = (hashCode() * 43) % 255
    val b = (hashCode() * 73) % 255

    return Color(r.absoluteValue, g.absoluteValue, b.absoluteValue)
}

fun Double.percent() = round(this * 1000) / 10

class GitCommit(
        val time: Long,
        val message: String,
        val files: List<ClassReference>
) {
    constructor(line: List<String>): this(line[0].toLong(), line[1], line[2].split(";").map { ClassReference(it) })

    constructor(line: String): this(line.split(","))

    fun toCsv() = "$time,$message,"+files.joinToString(",") { it.qualifiedName() }

    fun allCombinations() = files
            .flatMap { first -> files
            .mapNotNull { second -> if(first != second) setOf(first, second) to this else null } }
}
