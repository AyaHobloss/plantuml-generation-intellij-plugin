package com.kn.diagrams.generator.generator

import com.intellij.openapi.project.Project
import com.kn.diagrams.generator.actions.ActionContext
import com.kn.diagrams.generator.builder.DotShape
import com.kn.diagrams.generator.builder.addLink
import com.kn.diagrams.generator.config.VcsConfiguration
import com.kn.diagrams.generator.generator.vcs.VcsAnalysis
import com.kn.diagrams.generator.generator.vcs.buildDiagram
import com.kn.diagrams.generator.generator.vcs.percent

fun createVcsContent(actionContext: ActionContext,
                     commits: VcsAnalysis.() -> Unit = VcsAnalysis::loadAndCacheRawCommits): List<Pair<String, String>> {
    return VcsAnalysis(actionContext.config(), actionContext.project, commits) {
        val fullTimeRangeFilteredCommits = rawCommits
                .squashWithSameTicket()
                .filterByConfiguration()

        filteredCommits += fullTimeRangeFilteredCommits.filterByTimeRange()
        totalFiles += fullTimeRangeFilteredCommits.extractUniqueFiles()

    }.buildDiagram {
        dot.notes = "'${visibleGraph.edges.size} edges shown out of ${aggregatedGraph.totalEdgesCount}\n" +
                    "'${visibleGraph.nodes.size} nodes shown out of ${aggregatedGraph.totalNodesCount}"

        visibleGraph.nodes.forEach { (aggregate, weight) ->
            dot.nodes.add(DotShape(aggregate.display, aggregate.key).with {
                tooltip = "weight = $weight " +
                        "/ ${ aggregate.relativeWeight().percent() }% of visible nodes " +
                        "/ ${ aggregate.relativeTotalWeight().percent() }% of total nodes &#013;" +
                        "total files: ${ aggregate.fileCount() } " +
                        "/ commits: ${ aggregate.commitCount() } &#013;" +
                        "component: ${ aggregate.component() ?: "-" } &#013;" +
                        "layer: ${ aggregate.layer() ?: "-"}"

                // TODO remove
                aggregate.codeCoverage()?.let {
                    tooltip += "&#013;code test coverage: $it"
                }

                fillColor = aggregate.weightOrStructureBasedColor()
                fontSize = aggregate.fontSize()
                margin = aggregate.nodeSize()
                style = "filled"
            })
        }

        visibleGraph.edges.forEach { (edge, weight) ->
            dot.addLink(edge.from.key, edge.to.key) {
                label = "$weight / ${edge.relativeWeight().percent()}% / ${edge.relativeTotalWeight().percent()}%oT"
                tooltip = "$label / commits: ${ edge.commitCount() }"
                color = edge.weightBasedColor()
                penwidth = edge.weightBasedPenWidth()
                arrowHead = "none"
            }
        }

    }
}

