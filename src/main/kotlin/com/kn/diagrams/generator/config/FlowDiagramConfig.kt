package com.kn.diagrams.generator.config

import com.kn.diagrams.generator.graph.*

class FlowConfiguration(rootClass: String,
                        var rootMethod: String?,
                        extensionCallbackMethod: String? = "",
                        var projectClassification: ProjectClassification,
                        var graphRestriction: GraphRestriction,
                        var graphTraversal: GraphTraversal) : DiagramConfiguration(rootClass, extensionCallbackMethod) {
    override fun restrictionFilter() = GraphRestrictionFilter(projectClassification, graphRestriction)

    override fun traversalFilter() = GraphTraversalFilter(projectClassification, graphTraversal)

    override fun brandWithRootNode(rootNodeId: String) {
        rootClass = rootNodeId.substringBefore("#")
        rootMethod = rootNodeId
    }
}

