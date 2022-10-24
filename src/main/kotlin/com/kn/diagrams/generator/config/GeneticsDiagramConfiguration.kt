package com.kn.diagrams.generator.config

import com.kn.diagrams.generator.graph.*

class GeneticsConfiguration(override var extensionCallbackMethod: String? = "",
                             var projectClassification: ProjectClassification,
                             var graphRestriction: GraphRestriction,
                             var graphTraversal: GraphTraversal,
                             var details: GeneticsDiagramDetails) : BaseDiagramConfiguration {
    override fun restrictionFilter() = GraphRestrictionFilter(projectClassification, graphRestriction)

    override fun brandWithRootNode(rootNodeId: String) {

    }

     fun traversalFilter() = GraphTraversalFilter(projectClassification, graphTraversal)
}


enum class ClusterSourceGenetics{ Layer, Package, LSSGA, None }


class NodeSelectionG(
    var className: String = "",
    var classPackage: String = "",
    var methodName: String = "",
)

enum class GeneticsClusterVisualization{ Cluster, ClusterWithoutDetails, Nodes, NodesSimplified }


class GeneticsParameters(
    var iterations : Int=10,
    var parentSize:Int=10,
    var childSize:Int=10,
    var crossoverRate: Double=0.04,
    var mutationRate: Double= 0.05,
/*
    @CommentWithValue("goal: min(average cluster dependencies * standard deviation of cluster dependencies) - clusters should be encapsulated and equal sized to avoid one mega cluster")
    var geneticsOptimizeClusterDistribution: Boolean = false,
    var geneticsOptimization: GeneticsParametersVariation = GeneticsParametersVariation(),


 */

)



/*class GeneticsParametersVariation(
    var iterations: List<Int> = listOf(200,300,400,1000,5000),
    var parentsize: List<Int> = listOf(1000,2000),
    var childSize: List<Int> = listOf(1000,2000),
    var crossoverRate: List<Double> = listOf(0.01, 0.1, 0.3),
    var mutationRate: List<Double> = listOf(0.01, 0.1, 0.3),
)

 */



enum class GeneticsClusterAggregation { None, Class }

class GeneticsDiagramDetails(
    @CommentWithEnumValues
    var edgeMode: EdgeMode = EdgeMode.TypesAndMethods,
    var nodeSelectionG: NodeSelectionG = NodeSelectionG(),
    @CommentWithEnumValues
    var nodeAggregation: GeneticsClusterAggregation = GeneticsClusterAggregation.Class,
    @CommentWithEnumValues
    var visualization: GeneticsClusterVisualization = GeneticsClusterVisualization.Cluster,
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






