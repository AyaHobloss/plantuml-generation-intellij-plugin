package com.kn.diagrams.generator.config

import com.intellij.psi.PsiClass
import com.kn.diagrams.generator.graph.*


// TODO cleanup
class VcsConfiguration(rootClass: PsiClass,
                       var projectClassification: ProjectClassification,
                       var graphRestriction: GraphRestriction,
                       var graphTraversal: GraphTraversal,
                       var details: VcsDiagramDetails) : DiagramConfiguration(rootClass) {

    override fun restrictionFilter() = GraphRestrictionFilter(projectClassification, graphRestriction)

    override fun traversalFilter(rootNode: GraphNode) = GraphTraversalFilter(rootNode, projectClassification, graphTraversal)
}

enum class CommitFilter{ All, Matching, NotMatching }

// TODO split this class
// TODO show x first edges sorted by weight - and related nodes
// TODO show solo nodes by what? relative to visible edge nodes or better?
// TODO add file extension filter
class VcsDiagramDetails(
        @CommentWithValue("a cross-product is calculated and consumes your resources")
        var ignoreCommitsAboveFileCount: Int = 600,
        var squashCommitsContainingOneTicketReference: Boolean = true,
        var showPackageLevels: Int = 1,
        var repositoryBranch: String = "master",
        var commitContainsPattern: String = "[QC-",
        @CommentWithEnumValues
        var commitFilter: CommitFilter = CommitFilter.All,
        @CommentWithValue("surrounding edges are still shown, otherwise use restriction filter") // TODO check this
        var includedComponents: String = "",
        var showMaximumNumberOfEdges: Int = 30,
        var showMaximumNumberOfIsolatedNodes: Int = 20,
        var startDay: String? = "",
        var endDay: String? = "",
        @CommentWithEnumValues
        var nodeColorCoding: NodeColorCoding = NodeColorCoding.Component,
        @CommentWithEnumValues
        var nodeSize: NodeSizing = NodeSizing.FileCount,
        var nodeSizeFactor: Double = 1.0,
        @CommentWithValue("depends on visible edges / nodes") // TODO automatic scaling towards # shown edges / nodes
        var coloredNodeFactor: Double = 1.0,
        @CommentWithEnumValues
        var edgeColorCoding: EdgeColorCoding = EdgeColorCoding.None,
        var coloredEdgeFactor: Double = 15.0,

        var coloredEdgeWidthFactor: Double = 1.0,

        @CommentWithEnumValues
        var nodeAggregation: VcsNodeAggregation = VcsNodeAggregation.None,
        @CommentWithEnumValues
        var componentEdgeAggregationMethod: EdgeAggregation = EdgeAggregation.ClassRatioWithCommitSize,
        @CommentWithValue("weight * (1 / size ratio)^sizeNormalization, one commit should have a higher impact for smaller aggregates but a smaller impact on bigger aggregates")
        var sizeNormalization: Double = 0.0

)

enum class NodeSizing { None, FileCount, WeightDistribution }
enum class NodeColorCoding { None, Layer, Component, WeightDistribution, CodeCoverage }
enum class EdgeColorCoding { None, WeightDistribution }

enum class EdgeAggregation{ GraphConnections, CommitCount, TotalTouchedClasses, TouchedClassesOfCommit, ClassRatioWithCommitSize }

enum class VcsNodeAggregation{
    None, Component, Layer, ComponentAndLayer
}
