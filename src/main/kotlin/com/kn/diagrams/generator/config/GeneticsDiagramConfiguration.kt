package com.kn.diagrams.generator.config

import com.kn.diagrams.generator.graph.*


class GeneticsConfiguration(override var extensionCallbackMethod: String? = "",
                             var projectClassification: ProjectClassification,
                             var graphRestriction: GraphRestriction,
                             var graphTraversal: GraphTraversal,
                             var details: GeneticsDiagramDetails
) : BaseDiagramConfiguration {


    override fun restrictionFilter() = GraphRestrictionFilter(projectClassification, graphRestriction)

    override fun brandWithRootNode(rootNodeId: String) {
        TODO("Not yet implemented")
    }

     fun traversalFilter() = GraphTraversalFilter(projectClassification, graphTraversal)
}



enum class ClusterSourceGenetics{ Layer, Package, LSSGA, None }


class GeneticsNodeSelection(
    var className: String = "",
    var classPackage: String = "",
    var methodName: String = "",
)

enum class GeneticsClusterVisualization{ Cluster, ClusterWithoutDetails, Nodes, NodesSimplified }
enum class GeneticsClusterAggregation { None, Class }


class GeneticsParameters(
    var iterations : Int=1000,
    var parentSize:Int=1000,
    var childSize:Int=2000,
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
/*class GeneticsDiagramDetails(): DiagramDetails{
    override var edgeMode: EdgeMode = EdgeMode.MethodsOnly
    override var nodeSelection: NodeSelection= NodeSelection()
    override var nodeAggregation: ClusterAggregation= ClusterAggregation.Class
    override var visualization: ClusterVisualization = ClusterVisualization.Cluster
    override var clusteringAlgorithm: ClusterSource=ClusterSource.LSSGA
    var LSSGA: GeneticsParameters = GeneticsParameters()
    override var packageLevels: Int=1
    override var removedTransientDependencies: Boolean = false
    override var showInvertedDependenciesExplicitly: Boolean = false
    override var showCallsInEdgeToolTips: Boolean = false
}

 */
class GeneticsDiagramDetails(
    @CommentWithEnumValues
    var edgeMode: EdgeMode = EdgeMode.MethodsOnly,
    var nodeSelection: NodeSelection = NodeSelection(),
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






