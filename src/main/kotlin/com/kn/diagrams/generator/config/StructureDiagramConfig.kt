package com.kn.diagrams.generator.config

import com.intellij.openapi.project.Project
import com.kn.diagrams.generator.generator.Aggregation
import com.kn.diagrams.generator.graph.*

class StructureConfiguration(rootClass: String,
                             var projectClassification: ProjectClassification,
                             var graphRestriction: GraphRestriction,
                             var graphTraversal: GraphTraversal,
                             var details: StructureDiagramDetails) : DiagramConfiguration(rootClass) {

        override fun restrictionFilter(project: Project) = GraphRestrictionFilter(
                rootClass.psiClassFromQualifiedName(project)!!.reference(),
                null,
                projectClassification,
                graphRestriction)

        override fun traversalFilter(rootNode: GraphNode) = GraphTraversalFilter(rootNode, projectClassification, graphTraversal)
}

class StructureDiagramDetails(
    @CommentWithEnumValues
        var aggregation: Aggregation = Aggregation.GroupByClass,
    var showClassGenericTypes: Boolean = true,
    var showMethods: Boolean = true,
    var showMethodParameterNames: Boolean = false,
    var showMethodParameterTypes: Boolean = false,
    var showMethodReturnType: Boolean = false,
    var showPackageLevels: Int = 99,
    var showDetailedClassStructure: Boolean = true
)

