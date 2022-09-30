package com.kn.diagrams.generator.generator

import com.kn.diagrams.generator.actions.ActionContext
import com.kn.diagrams.generator.builder.addLink
import com.kn.diagrams.generator.config.ClusterSourceGenetics
import com.kn.diagrams.generator.config.GeneticsClusterVisualization
import com.kn.diagrams.generator.generator.code.*
import com.kn.diagrams.generator.graph.AnalyzeClass
import com.kn.diagrams.generator.graph.AnalyzeMethod
import com.kn.diagrams.generator.graph.ClassReference


fun createGeneticDiagramUmlContent(actionContext: ActionContext): List<Pair<String, String>> {
    return actionContext.createGeneticsClusterContext{
        searchEdgesBySelectedNodes()
        geneticsClustering { algorithm -> when(algorithm){
            ClusterSourceGenetics.Layer -> loadLayerClusters()
            ClusterSourceGenetics.Package -> loadPackageClusters()
            ClusterSourceGenetics.None -> geneticsRootNodes.clusterTo("cluster_0")
            ClusterSourceGenetics.LSSGA -> loadGeneticsClusters()// leave empty?
        } }
        // TODO visualization Node does not work with Layer/Package clustering
    }.buildNodeBasedDiagram {
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
            visualizeGroupedByClustersSimplified()
        }
    }
        .buildClusterAggregatedDiagram {
            forEachNode { dot.nodes.add(this.toClusterDotNode()) }

            forEachEdge {
                dot.addLink(from, to) {
                    // IMPROVE: find the GraphDirectedEdge (maybe hidden) between both clusters and count the context
                    if(configGenetics.details.showCallsInEdgeToolTips){
                        tooltip = calls.allCalls(false)
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


