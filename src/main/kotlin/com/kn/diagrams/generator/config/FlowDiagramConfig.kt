package com.kn.diagrams.generator.config

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.kn.diagrams.generator.graph.*

class FlowConfiguration(rootClass: PsiClass,
                        var rootMethod: PsiMethod?,
                        var projectClassification: ProjectClassification,
                        var graphRestriction: GraphRestriction,
                        var graphTraversal: GraphTraversal) : DiagramConfiguration(rootClass) {
    override fun restrictionFilter() = GraphRestrictionFilter(projectClassification, graphRestriction)

    override fun traversalFilter() = GraphTraversalFilter(projectClassification, graphTraversal)

}

