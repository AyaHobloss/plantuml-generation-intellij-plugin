package com.kn.diagrams.generator.config

import com.kn.diagrams.generator.graph.GraphRestriction
import com.kn.diagrams.generator.graph.GraphRestrictionFilter
import com.kn.diagrams.generator.graph.ProjectClassification


class VcsConfiguration(var projectClassification: ProjectClassification,
                       var graphRestriction: GraphRestriction,
                       var details: VcsDiagramDetails) : BaseDiagramConfiguration{

        override fun restrictionFilter() = GraphRestrictionFilter(projectClassification, graphRestriction)

        override fun brandWithRootNode(rootNodeId: String) {
                // directory based diagrams have no root node
        }
}

class VcsDiagramDetails(
        @CommentWithValue("a cross-product is calculated and consumes your resources")
        var ignoreCommitsAboveFileCount: Int = 600,
        var squashCommitsContainingOneTicketReference: Boolean = true,
        var showPackageLevels: Int = 1,
        var repositoryBranch: String = "master",
        var commitContainsPattern: String = "[QC-",
        @CommentWithEnumValues
        var commitFilter: CommitFilter = CommitFilter.All,
        var showMaximumNumberOfEdges: Int = 30,
        var showMaximumNumberOfIsolatedNodes: Int = 20,
        var startDay: String? = "",
        var endDay: String? = "",
        @CommentWithEnumValues
        var nodeColorCoding: NodeColorCoding = NodeColorCoding.Component,
        @CommentWithEnumValues
        var nodeSize: NodeSizing = NodeSizing.FileCount,
        var nodeSizeFactor: Double = 1.0,
        @CommentWithValue("depends on visible edges / nodes")
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

enum class CommitFilter{ All, Matching, NotMatching }
enum class NodeSizing { None, FileCount, WeightDistribution }
enum class NodeColorCoding { None, Layer, Component, WeightDistribution, CodeCoverage }
enum class EdgeColorCoding { None, WeightDistribution }
enum class EdgeAggregation{ GraphConnections, CommitCount, TotalTouchedClasses, TouchedClassesOfCommit, ClassRatioWithCommitSize }
enum class VcsNodeAggregation{ None, Component, Layer, ComponentAndLayer }
