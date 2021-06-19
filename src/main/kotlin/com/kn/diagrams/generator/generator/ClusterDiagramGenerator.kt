package com.kn.diagrams.generator.generator

import com.intellij.openapi.project.Project
import com.kn.diagrams.generator.builder.DiagramDirection
import com.kn.diagrams.generator.builder.DotDiagramBuilder
import com.kn.diagrams.generator.config.ClusterConfiguration
import com.kn.diagrams.generator.config.ClusterSource
import com.kn.diagrams.generator.config.ClusterVisualization
import com.kn.diagrams.generator.config.attacheMetaData
import com.kn.diagrams.generator.graph.analysisCache
import com.kn.diagrams.generator.inReadAction


fun createClusterDiagramUmlContent(config: ClusterConfiguration, project: Project): List<Pair<String, String>> {
    val restrictionFilter = inReadAction { config.restrictionFilter() }
    val cache = analysisCache.getOrCompute(project, restrictionFilter, config.projectClassification.searchMode)

    val filter = config.traversalFilter()
    val clusterRootNodes = cache.nodesForClustering(filter, config.details)
    val edges = cache.search(filter) {
        roots = clusterRootNodes
        forwardDepth = config.graphTraversal.forwardDepth
        backwardDepth = config.graphTraversal.backwardDepth
        edgeMode = config.details.edgeMode
    }.flatten().distinct()

    val visualizationConfiguration = config.visualizationConfig()
    val dot = DotDiagramBuilder()
    dot.direction = DiagramDirection.LeftToRight


    when(config.details.visualization){
        ClusterVisualization.Cluster, ClusterVisualization.ClusterWithoutDetails -> dot.aggregateToCluster(cache, edges, config, visualizationConfiguration)
        ClusterVisualization.Nodes -> {
            val clusters = if(config.details.clusteringAlgorithm == ClusterSource.TakeFromFile) {
                loadPredefinedClusters(config.details.clusterDefinitionFile)
            }else if(config.details.clusteringAlgorithm == ClusterSource.Package) {
                loadPackageClusters(edges, config, visualizationConfiguration)
            } else {
                ClusterDefinition(clusterRootNodes.map { it.nameInCluster(config.details.nodeAggregation) to "cluster_0" }.toMap())
            }
            dot.visualizeGroupedByClusters(edges, config, visualizationConfiguration, clusters)
        }
        ClusterVisualization.NodesSimplified -> {
            val clusters = if(config.details.clusteringAlgorithm == ClusterSource.TakeFromFile) {
                loadPredefinedClusters(config.details.clusterDefinitionFile)
            } else {
                ClusterDefinition(clusterRootNodes.map { it.nameInCluster(config.details.nodeAggregation) to "cluster_0" }.toMap())
            }
            dot.visualizeGroupedByClustersSimplified(edges, config, visualizationConfiguration, clusters)
        }
    }


    return listOf("cluster" to dot.create().attacheMetaData(config))
}
