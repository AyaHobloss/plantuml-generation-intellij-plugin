package com.kn.diagrams.generator.generator.code

import com.kn.diagrams.generator.generator.*
import com.kn.diagrams.generator.graph.AnalyzeCall
import com.kn.diagrams.generator.graph.FieldWithTargetType
import com.kn.diagrams.generator.graph.InheritanceType
import com.kn.diagrams.generator.graph.MethodClassUsage
import kotlin.streams.toList

val callAndStructureDiagramTemplate: RootNodeBuildContext.() -> Unit = {
    methodNodes.forEach { method ->
        method.grouping().addShape(method.signature(), method.diagramId()) {
            penWidth = method.rootNodePenWidth()
            tooltip = method.containingClass.name + "\n\n" + method.javaDoc
            fontColor = method.visibility.color()
            style = "filled"
            fillColor = method.visibilityOrStructureBasedColor()
        }
    }

    classNodes.forEach { clazz ->
        clazz.grouping().addNode {
            if (visualizationConfig.showDetailedClass) {
                clazz.createHTMLShape(visualizationConfig)
                        .apply { config.fillColor = clazz.structureBasedColor() }
            } else {
                clazz.createBoxShape()
                        .apply { config.fillColor = clazz.structureBasedColor() }
            }
        }
    }

    edges.parallelStream().flatMap { edge ->
        edge.createLinksPerContext { context, hasMoreEdges ->
            when (context) {
                is MethodClassUsage -> {
                    style = "dashed"
                    if (!hasMoreEdges) {
                        label = context.reference
                    }
                }
                is AnalyzeCall -> {
                    label = context.sequence.toString().takeIf { it != "-1" }.takeIf { visualizationConfig.showCallOrder }
                }
                is FieldWithTargetType -> {
                    label = context.field.name + "\n" + context.field.cardinality()
                }
                is InheritanceType -> {
                    val hasFullInheritance = edge.edges().all { InheritanceType.Implementation in it.context || InheritanceType.SubClass in it.context }
                    if(hasFullInheritance){
                        dir = "both"
                        arrowHead = "none"
                        arrowTail = "empty"
                    }
                }
            }
        }.stream()
    }.toList().let { dot.edges.addAll(it) }
}

