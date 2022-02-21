package com.kn.diagrams.generator.config

import com.google.gson.annotations.Until
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.kn.diagrams.generator.generator.code.StructureColorCoding
import com.kn.diagrams.generator.graph.*


class CallConfiguration(rootClass: String,
                        var rootMethod: String?,
                        var projectClassification: ProjectClassification,
                        var graphRestriction: GraphRestriction,
                        var graphTraversal: GraphTraversal,
                        var details: CallDiagramDetails) : DiagramConfiguration(rootClass) {

    override fun restrictionFilter() = GraphRestrictionFilter(projectClassification, graphRestriction)

    override fun traversalFilter() = GraphTraversalFilter(projectClassification, graphTraversal)

    override fun brandWithRootNode(rootNodeId: String) {
        rootClass = rootNodeId.substringBefore("#")
        rootMethod = rootNodeId
    }
}

enum class NodeAggregation{ None, Class }
enum class NodeGrouping{ None, Component, Layer }

@Deprecated("replaced by NodeAggregation/NodeGrouping")
enum class Aggregation {
        ByClass, GroupByClass, GroupByLayer, GroupByComponent, None
}

class CallDiagramDetails(
        @CommentWithEnumValues
        @Until(1.2)
        var aggregation: Aggregation = Aggregation.GroupByClass,
        @CommentWithEnumValues
        var nodeAggregation: NodeAggregation = NodeAggregation.None,
        @CommentWithEnumValues
        var nodeGrouping: NodeGrouping = NodeGrouping.Component,
        var wrapMethodsWithItsClass: Boolean = true,
        var showMethodParametersTypes: Boolean = false,
        var showMethodParametersNames: Boolean = false,
        var showMethodReturnType: Boolean = false,
        var showPackageLevels: Int = 2,
        var showCallOrder: Boolean = true,
        @CommentWithEnumValues
        var edgeMode: EdgeMode = EdgeMode.MethodsOnly,
        var showDetailedClassStructure: Boolean = false,
        @CommentWithEnumValues
        var methodColorCoding: StructureColorCoding = StructureColorCoding.None,
        @CommentWithEnumValues
        var classColorCoding: StructureColorCoding = StructureColorCoding.None
)
