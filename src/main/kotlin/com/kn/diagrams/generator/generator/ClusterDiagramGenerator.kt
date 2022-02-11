package com.kn.diagrams.generator.generator

import com.kn.diagrams.generator.actions.ActionContext
import com.kn.diagrams.generator.builder.addLink
import com.kn.diagrams.generator.config.ClusterSource
import com.kn.diagrams.generator.config.ClusterVisualization
import com.kn.diagrams.generator.generator.code.*
import com.kn.diagrams.generator.graph.AnalyzeClass
import com.kn.diagrams.generator.graph.AnalyzeMethod
import com.kn.diagrams.generator.graph.ClassReference


fun createClusterDiagramUmlContent(actionContext: ActionContext): List<Pair<String, String>> {
    return actionContext.createClusterContext{
                searchEdgesBySelectedNodes()
                clustering { algorithm -> when(algorithm){
                    ClusterSource.Layer -> loadLayerClusters()
                    ClusterSource.Package -> loadPackageClusters()
                    ClusterSource.Leiden -> loadLeidenClusters()
                    ClusterSource.None -> clusterRootNodes.clusterTo("cluster_0") // leave empty?
                } }
        // TODO visualization Node does not work with Layer/Package clustering
            }.buildNodeBasedDiagram {
                if(config.details.visualization == ClusterVisualization.Nodes){
                    nodes.forEach { node ->
                        when (node) {
                            // TODO wrapper for Method class inside createShape()?!
                            is AnalyzeMethod -> node.grouping().addNode(node.createShape(visualConfig))
                            is ClassReference -> node.grouping().addNode(node.createBoxShape())
                            is AnalyzeClass -> node.grouping().addNode(node.createBoxOrTableShape(visualConfig))
                        }
                    }
                    // TODO fix aggregation to class
                    edges.forEach { dot.addDirectLink(it, visualConfig) }
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
                        if(config.details.showCallsInEdgeToolTips){
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
            .build()

}
