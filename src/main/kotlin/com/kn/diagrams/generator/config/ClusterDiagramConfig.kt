package com.kn.diagrams.generator.config

import com.intellij.psi.PsiClass
import com.kn.diagrams.generator.generator.ClusterAggregation
import com.kn.diagrams.generator.graph.*

class ClusterConfiguration(var projectClassification: ProjectClassification,
                           var graphRestriction: GraphRestriction,
                           var graphTraversal: GraphTraversal,
                           var details: ClusterDiagramDetails) : BaseDiagramConfiguration {
    override fun diagramFileName() = "Cluster_Diagram" // TODO find better naming

    override fun restrictionFilter() = GraphRestrictionFilter(projectClassification, graphRestriction)

    fun traversalFilter() = GraphTraversalFilter(projectClassification, graphTraversal)
}


enum class ClusterSource{ TakeFromFile, Package, Leiden }


class NodeSelection(
    var className: String = "", 
    var classPackage: String = "", 
    var methodName: String = "", 
    var clusterNameFromFile: String = ""
)

enum class ClusterVisualization{ Cluster, ClusterWithoutDetails, Nodes, NodesSimplified }

class LeidenParameters(
    @CommentWithValue("higher resolution produces smaller clusters")
    var resolution: Double = 1.0,
    var randomness: Double = 0.01,
    var randomStarts: Int = 1,
    @CommentWithValue("more iterations produce smaller clusters")
    var iterations: Int = 10,
    var minimumNodesPerCluster: Int = 1,

    @CommentWithValue("goal: min(average cluster dependencies * standard deviation of cluster dependencies) - clusters should be encapsulated and equal sized to avoid one mega cluster")
    var optimizeClusterDistribution: Boolean = false,
    var leidenOptimization: LeidenParametersVariation = LeidenParametersVariation(),
)

class LeidenParametersVariation(
    var resolution: List<Double> = listOf(0.8, 0.6, 0.4, 0.25, 0.20, 0.15,0.1, 0.05, 0.025, 0.0125, 0.00125, 0.000125),
    var randomness: List<Double> = listOf(0.01, 0.1, 0.3),
    var randomStarts: List<Int> = listOf(1,5),
    var iterations: List<Int> =listOf(1,2,3,4,5,8,12,14,18,25),
    var minimumNodesPerCluster: List<Int> = listOf(5, 10, 20),
)

class ClusterDiagramDetails(
    @CommentWithEnumValues
    var edgeMode: EdgeMode = EdgeMode.MethodsOnly,
    var nodeSelection: NodeSelection = NodeSelection(),
    @CommentWithEnumValues
    var nodeAggregation: ClusterAggregation = ClusterAggregation.Class,
    @CommentWithEnumValues
    var visualization: ClusterVisualization = ClusterVisualization.Cluster,
    @CommentWithEnumValues
    var clusterDefinitionOutputFile:String = "",
    var clusterDefinitionFile: String = "",
    var clusteringAlgorithm: ClusterSource = ClusterSource.Leiden,

    var leiden: LeidenParameters = LeidenParameters(),

    var packageLevels: Int = 1,
    @CommentWithValue("A->B->C + A->C then A->C is removed to reduce number of edges")
    var removedTransientDependencies: Boolean = false,
    @CommentWithValue("Interface (componentA) -> Implementation (componentB) is shown with a red arrow: <-")
    var showInvertedDependenciesExplicitly: Boolean = false,
    @CommentWithValue("only visible in SVGs")
    var showCallsInEdgeToolTips: Boolean = false,
)
