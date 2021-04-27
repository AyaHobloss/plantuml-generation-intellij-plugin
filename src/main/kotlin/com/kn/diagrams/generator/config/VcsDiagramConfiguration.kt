package com.kn.diagrams.generator.config

import com.intellij.psi.PsiClass
import com.kn.diagrams.generator.graph.*


class VcsConfiguration(rootClass: PsiClass,
                       var projectClassification: ProjectClassification,
                       var graphRestriction: GraphRestriction,
                       var graphTraversal: GraphTraversal,
                       var details: VcsDiagramDetails) : DiagramConfiguration(rootClass) {

    override fun restrictionFilter() = GraphRestrictionFilter(projectClassification, graphRestriction)

    override fun traversalFilter(rootNode: GraphNode) = GraphTraversalFilter(rootNode, projectClassification, graphTraversal)
}

enum class CommitFilter{ All, Matching, NotMatching }

class VcsDiagramDetails(
        @CommentWithValue("a cross-product is calculated and consumes your resources")
        var ignoreCommitsAboveFileCount: Int = 600,
        var squashCommitsContainingOneTicketReference: Boolean = true,
        // TODO branch
        // TODO place it under the diagram file? just use git repo name + branch
        var repositoryBranch: String = "master",
        var commitContainsPattern: String = "[QC-",
        @CommentWithEnumValues
        var commitFilter: CommitFilter = CommitFilter.All,
        var includedComponents: String = "",
        var showMaximumNumberOfEdges: Int = 30,
        var startDay: String? = "",
        var endDay: String? = "",

        @CommentWithValue("only applicable with no aggregation")
        var colorizeFilesWithSameComponent: Boolean = true,
        var coloredNodeFactor: Double = 1.0,
        var coloredEdgeFactor: Double = 15.0,
        var coloredEdgeWidthFactor: Double = 1.0,


        @CommentWithEnumValues
        var nodeAggregation: VcsNodeAggregation = VcsNodeAggregation.None,
        @CommentWithEnumValues
        var componentEdgeAggregationMethod: EdgeAggregation = EdgeAggregation.ClassRatioWithCommitSize,
        @CommentWithValue("weight * (1 / size ratio)^sizeNormalization, one commit should have a higher impact for smaller aggregates but a smaller impact on bigger aggregates")
        var sizeNormalization: Double = 0.0

)

enum class EdgeAggregation{ GraphConnections, CommitCount, TotalTouchedClasses, TouchedClassesOfCommit, ClassRatioWithCommitSize }

enum class VcsNodeAggregation{
    None, Component, Layer, ComponentAndLayer
}
