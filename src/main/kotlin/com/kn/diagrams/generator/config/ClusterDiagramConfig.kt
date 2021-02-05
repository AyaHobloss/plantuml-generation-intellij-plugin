package com.kn.diagrams.generator.config

import com.intellij.psi.PsiClass
import com.kn.diagrams.generator.generator.ClusterAggregation
import com.kn.diagrams.generator.graph.*

class ClusterConfiguration(rootClass: PsiClass,
                           var projectClassification: ProjectClassification,
                           var graphRestriction: GraphRestriction,
                           var graphTraversal: GraphTraversal,
                           var details: ClusterDiagramDetails) : DiagramConfiguration(rootClass) {

    override fun restrictionFilter() = GraphRestrictionFilter(rootClass.reference(), null, projectClassification, graphRestriction)

    override fun traversalFilter(rootNode: GraphNode) = GraphTraversalFilter(rootNode, projectClassification, graphTraversal)
}

enum class ClusterSource{ File, Connectivity, Louvian, Leiden }

enum class LaidenQuality{CPM, Modularity}

class ClusterDiagramDetails(
    var nodeAggregation: ClusterAggregation = ClusterAggregation.Class,
    @CommentWithEnumValues
    var edgeMode: EdgeMode = EdgeMode.MethodsOnly,
    @CommentWithEnumValues
    var source: ClusterSource = ClusterSource.Louvian,
    var clusterDefinitionFile: String = "",
    var levelOfConnectivity: Int = 0,
    var louvianMaxLevels: Int = 10,
    var louvianMaxIterations: Int = 10,
    var leidenResolution: Double = 1.0,
    var leidenRandomness: Double = 0.01,
    var leidenStarts: Int = 1,
    var leidenIterations: Int = 10,
    var leidenMinimumNodesPerCluster: Int = 1,
    @CommentWithEnumValues
    var leidenQualityFunction: LaidenQuality = LaidenQuality.CPM,
    var showEdgesAboveCallCount: Int = 0,
    var showEdgesBelowCallCount: Int = 500

)
