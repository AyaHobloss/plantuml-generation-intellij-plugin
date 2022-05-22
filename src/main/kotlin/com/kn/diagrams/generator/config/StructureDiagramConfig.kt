package com.kn.diagrams.generator.config

import com.google.gson.annotations.Until
import com.kn.diagrams.generator.generator.code.StructureColorCoding
import com.kn.diagrams.generator.graph.*

class StructureConfiguration(rootClass: String,
                             extensionCallbackMethod: String? = "",
                             var projectClassification: ProjectClassification,
                             var graphRestriction: GraphRestriction,
                             var graphTraversal: GraphTraversal,
                             var details: StructureDiagramDetails) : DiagramConfiguration(rootClass, extensionCallbackMethod) {

    override fun restrictionFilter() = GraphRestrictionFilter(projectClassification, graphRestriction)

    override fun traversalFilter() = GraphTraversalFilter(projectClassification, graphTraversal)

    override fun brandWithRootNode(rootNodeId: String) {
        rootClass = rootNodeId
    }
}

class StructureDiagramDetails(
    @CommentWithEnumValues
    @Until(1.2)
    var aggregation: Aggregation = Aggregation.GroupByClass,
    @CommentWithEnumValues
    var nodeAggregation: NodeAggregation = NodeAggregation.None,
    @CommentWithEnumValues
    var nodeGrouping: NodeGrouping = NodeGrouping.Component,
    var showClassGenericTypes: Boolean = true,
    var showMethods: Boolean = true,
    var showMethodParameterNames: Boolean = false,
    var showMethodParameterTypes: Boolean = false,
    var showMethodReturnType: Boolean = false,
    var showPackageLevels: Int = 99,
    var showDetailedClassStructure: Boolean = true,
    @CommentWithEnumValues
    var methodColorCoding: StructureColorCoding = StructureColorCoding.None,
    @CommentWithEnumValues
    var classColorCoding: StructureColorCoding = StructureColorCoding.None
)

