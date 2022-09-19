package com.kn.diagrams.generator.config

import com.kn.diagrams.generator.graph.*

class GeneticsConfiguration(override var extensionCallbackMethod: String?= "",
                            var projectClassification: ProjectClassification,
                            var graphRestriction: GraphRestriction,
                            var graphTraversal: GraphTraversal,
                            var details: GeneticsDiagramDetails
) : BaseDiagramConfiguration{


    override fun restrictionFilter() = GraphRestrictionFilter(projectClassification, graphRestriction)

    override fun brandWithRootNode(rootNodeId: String) {
        TODO("Not yet implemented")
    }

    fun traversalFilter() = GraphTraversalFilter(projectClassification, graphTraversal)
}


enum class ClusterSourceGenetics{ Layer, Package, LSSGA, None }



class GeneticsParameters(
   /* @CommentWithValue("higher resolution produces smaller clusters")
    var resolution: Double = 1.0,
    var randomness: Double = 0.01,
    var randomStarts: Int = 1,
    @CommentWithValue("more iterations produce smaller clusters")
    var iterations: Int = 10,
    var minimumNodesPerCluster: Int = 1,

    @CommentWithValue("goal: min(average cluster dependencies * standard deviation of cluster dependencies) - clusters should be encapsulated and equal sized to avoid one mega cluster")
    var optimizeClusterDistribution: Boolean = false,
    var leidenOptimization: LeidenParametersVariation = LeidenParametersVariation(),

    */

    var iterations : Int=400,
    var parentSize:Int=300,
    var childSize:Int=300,
    var crossoverRate: Double=0.04,
    var mutationRate: Double= 0.05,


)
/*
class GeneticsParametersVariation(
    var resolution: List<Double> = listOf(0.8, 0.6, 0.4, 0.25, 0.20, 0.15,0.1, 0.05, 0.025, 0.0125, 0.00125, 0.000125),
    var randomness: List<Double> = listOf(0.01, 0.1, 0.3),
    var randomStarts: List<Int> = listOf(1,5),
    var iterations: List<Int> = listOf(1,2,3,4,5,8,12,14,18,25),
    var minimumNodesPerCluster: List<Int> = listOf(5, 10, 20),
)

*/

class GeneticsDiagramDetails(
    @CommentWithEnumValues
    var edgeMode: EdgeMode = EdgeMode.MethodsOnly,
    var nodeSelection: NodeSelection = NodeSelection(),
    @CommentWithEnumValues
    var nodeAggregation: ClusterAggregation = ClusterAggregation.Class,
    @CommentWithEnumValues
    var visualization: ClusterVisualization = ClusterVisualization.Cluster,
    @CommentWithEnumValues
    var clusteringAlgorithm: ClusterSourceGenetics = ClusterSourceGenetics.LSSGA,
    var LSSGA: GeneticsParameters = GeneticsParameters(),
    var packageLevels: Int = 1,
    @CommentWithValue("A->B->C + A->C then A->C is removed to reduce number of edges")
    var removedTransientDependencies: Boolean = false,
    @CommentWithValue("Interface (componentA) -> Implementation (componentB) is shown with a red arrow: <-")
    var showInvertedDependenciesExplicitly: Boolean = false,
    @CommentWithValue("only visible in SVGs")
    var showCallsInEdgeToolTips: Boolean = false,
)
