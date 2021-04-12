package com.kn.diagrams.generator.config

import com.intellij.psi.PsiClass
import com.kn.diagrams.generator.graph.*
import java.util.*


class GitConfiguration(rootClass: PsiClass,
                       var projectClassification: ProjectClassification,
                       var graphRestriction: GraphRestriction,
                       var graphTraversal: GraphTraversal,
                       var details: GitDiagramDetails) : DiagramConfiguration(rootClass) {

    override fun restrictionFilter() = GraphRestrictionFilter(projectClassification, graphRestriction)

    override fun traversalFilter(rootNode: GraphNode) = GraphTraversalFilter(rootNode, projectClassification, graphTraversal)
}

enum class CommitFilter{ All, Matching, NotMatching }

class GitDiagramDetails(
        @CommentWithValue("a cross-product is calculated and consumes your resources")
        var ignoreCommitsAboveFileCount: Int = 600,
        var squashCommitsContainingOneTicketReference: Boolean = true,
        // TODO branch
        // TODO place it under the diagram file? just use git repo name + branch
        var workspaceIdentifier: String = "",

        var commitPattern: String = "[QC-",
        @CommentWithEnumValues
        var commitFilter: CommitFilter = CommitFilter.All,
        var includedComponents: String = "",
        var minimumWeight: Int = 30,
        @CommentWithValue("use the display name of the node to overwrite the global minimum weight")
        var nodeBasedMinimumWeight: Map<String, Int> = mapOf(),
        var startDay: String? = "",
        var endDay: String? = "",

        var colorizeFilesWithSameComponent: Boolean = true,
        var coloredNodeFactor: Double = 1.0,
        var coloredEdgeFactor: Double = 15.0,
        var coloredEdgeWidthFactor: Double = 1.0,


        @CommentWithEnumValues
        var nodeAggregation: GitNodeAggregation = GitNodeAggregation.None,
        @CommentWithEnumValues
        var componentEdgeAggregationMethod: EdgeAggregation = EdgeAggregation.ClassRatioOfCommit
)

enum class EdgeAggregation{ GraphConnections, CommitCount, TotalTouchedClasses, TouchedClassesOfCommit, ClassRatioOfCommit, ClassRatioWithCommitSize }

enum class GitNodeAggregation{
    None, Component
}
