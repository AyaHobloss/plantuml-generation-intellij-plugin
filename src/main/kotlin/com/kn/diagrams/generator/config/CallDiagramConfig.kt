package com.kn.diagrams.generator.config

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.kn.diagrams.generator.generator.Aggregation
import com.kn.diagrams.generator.graph.*


class CallConfiguration(rootClass: PsiClass,
                        var rootMethod: PsiMethod?,
                        var projectClassification: ProjectClassification,
                        var graphRestriction: GraphRestriction,
                        var graphTraversal: GraphTraversal,
                        var details: CallDiagramDetails) : DiagramConfiguration(rootClass) {

        override fun restrictionFilter() = GraphRestrictionFilter(projectClassification, graphRestriction)

        override fun traversalFilter() = GraphTraversalFilter(projectClassification, graphTraversal)
}

enum class NodeAggregation{ None, Class }
enum class NodeGrouping{ None, Component, Layer }

class CallDiagramDetails(
        @CommentWithEnumValues
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
        var showDetailedClassStructure: Boolean = false
)
