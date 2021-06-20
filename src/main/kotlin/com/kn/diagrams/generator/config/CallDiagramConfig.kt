package com.kn.diagrams.generator.config

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.kn.diagrams.generator.generator.Aggregation
import com.kn.diagrams.generator.graph.*


class CallConfiguration(rootClass: String,
                        var rootMethod: String?,
                        var projectClassification: ProjectClassification,
                        var graphRestriction: GraphRestriction,
                        var graphTraversal: GraphTraversal,
                        var details: CallDiagramDetails) : DiagramConfiguration(rootClass) {

        override fun restrictionFilter(project: Project) = GraphRestrictionFilter(
                rootClass.psiClassFromQualifiedName(project)!!.reference(),
                rootMethod?.psiMethodFromSimpleReference(project)?.id(),
                projectClassification,
                graphRestriction
        )

        override fun traversalFilter(rootNode: GraphNode) = GraphTraversalFilter(rootNode, projectClassification, graphTraversal)
}

class CallDiagramDetails(
        @CommentWithEnumValues
        var aggregation: Aggregation = Aggregation.GroupByClass,
        var showMethodParametersTypes: Boolean = false,
        var showMethodParametersNames: Boolean = false,
        var showMethodReturnType: Boolean = false,
        var showPackageLevels: Int = 2,
        var showCallOrder: Boolean = true,
        @CommentWithEnumValues
        var edgeMode: EdgeMode = EdgeMode.MethodsOnly,
        var showDetailedClassStructure: Boolean = false
)
