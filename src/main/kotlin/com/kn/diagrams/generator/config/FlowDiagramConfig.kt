package com.kn.diagrams.generator.config

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.kn.diagrams.generator.graph.*

class FlowConfiguration(rootClass: String,
                        var rootMethod: String?,
                        var projectClassification: ProjectClassification,
                        var graphRestriction: GraphRestriction,
                        var graphTraversal: GraphTraversal) : DiagramConfiguration(rootClass) {
    override fun restrictionFilter(project: Project) = GraphRestrictionFilter(
            rootClass.psiClassFromQualifiedName(project)!!.reference(),
            rootMethod?.psiMethodFromSimpleReference(project)?.id(),
            projectClassification,
            graphRestriction
    )

    override fun traversalFilter(rootNode: GraphNode) = GraphTraversalFilter(rootNode, projectClassification, graphTraversal)

}

