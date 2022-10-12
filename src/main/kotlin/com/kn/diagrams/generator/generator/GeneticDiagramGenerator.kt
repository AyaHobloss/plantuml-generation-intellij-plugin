package com.kn.diagrams.generator.generator

import com.kn.diagrams.generator.actions.ActionContext
import com.kn.diagrams.generator.builder.addLink
import com.kn.diagrams.generator.config.ClusterSourceGenetics
import com.kn.diagrams.generator.config.GeneticsClusterVisualization
import com.kn.diagrams.generator.config.GeneticsConfiguration
import com.kn.diagrams.generator.generator.code.*
import com.kn.diagrams.generator.graph.AnalyzeClass
import com.kn.diagrams.generator.graph.AnalyzeMethod
import com.kn.diagrams.generator.graph.ClassReference


fun createGeneticDiagramUmlContent(actionContext: ActionContext): List<Pair<String, String>> {

    return actionContext.createGeneticsClusterContext{
        searchEdgesBySelectedNodesGenetics()
        geneticsClustering { algorithm -> when(algorithm){
            ClusterSourceGenetics.Layer -> loadLayerClustersGenetics()
            ClusterSourceGenetics.Package -> loadPackageClustersGenetics()
            ClusterSourceGenetics.LSSGA -> loadGeneticsClusters()
            ClusterSourceGenetics.None ->geneticsRootNodes.clusterToGenetics("cluster_0")
            // leave empty?

        } }
        // TODO visualization Node does not work with Layer/Package clustering
    }.buildNodeBasedDiagramGenetics {
        if(configGenetics.details.visualization == GeneticsClusterVisualization.Nodes){
            nodes.forEach { node ->
                when (node) {
                    // TODO wrapper for Method class inside createShape()?!
                    is AnalyzeMethod -> node.grouping().addNode(node.createShape(geneticsVisualConfig))
                    is ClassReference -> node.grouping().addNode(node.createBoxShape())
                    is AnalyzeClass -> node.grouping().addNode(node.createBoxOrTableShape(geneticsVisualConfig))
                }
            }
            // TODO fix aggregation to class
            edges.forEach { dot.addDirectLink(it, geneticsVisualConfig) }
        }else{
            // TODO generify, is it used anymore?! check presentation
            visualizeGroupedByClustersSimplifiedGenetics()
        }
    }
        .buildClusterAggregatedDiagramGenetics {
            forEachNodeGenetics { dot.nodes.add(this.toClusterDotNodeGenetics()) }

            forEachEdgeGenetics {
                dot.addLink(from, to) {
                    // IMPROVE: find the GraphDirectedEdge (maybe hidden) between both clusters and count the context
                    if(configGenetics.details.showCallsInEdgeToolTips){
                        tooltip = calls.allCallsGenetics(false)
                    }
                    if(inverted){
                        label = "inverted calls =    " + calls.size // counts parallel edges only once!!
                        color = "red"
                    }else{
                        label = "calls =    " + calls.size // counts parallel edges only once!!
                    }
                }
            }
        }
        .Geneticsbuild()


}


fun GeneticsConfiguration.visualizationGeneticsConfig() = DiagramVisualizationConfiguration(
    rootNode = null,
    projectClassification,
    details.packageLevels,
    showClassGenericTypes = false,
    showClassMethods = false,
    showMethodParametersTypes = false,
    showMethodParametersNames = false,
    showMethodReturnType = false,
    showCallOrder = false,
    showDetailedClass = false
)




