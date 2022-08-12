package com.kn.diagrams.generator.generator.code

import com.kn.diagrams.generator.builder.DotLink
import com.kn.diagrams.generator.builder.DotShape
import com.kn.diagrams.generator.builder.Oval
import com.kn.diagrams.generator.builder.Rectangle
import com.kn.diagrams.generator.cast
import com.kn.diagrams.generator.generator.*
import com.kn.diagrams.generator.graph.*
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

    fieldNodes.forEach { field ->
        dot.nodes.add(DotShape(field.containingClass?.name+"."+field.name, field.diagramId()).with {
            shape = Rectangle
            style = "filled"
        })
    }

    traceNodes.forEach {
        if (it is TraceNode){
            dot.nodes.add(DotShape(it.text, it.diagramId()).with {
                shape = Oval
                style = "filled"
            })
        }
    }

    traceEdges.forEach { edge ->
        dot.edges.add(DotLink(edge.from.diagramId(), edge.to.diagramId()).with {
            label = edge.context.firstOrNull()?.cast<TraceContext>()?.text
        })
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
                is TraceContext -> {
                    label = context.text
                }
                // structure diagram related edges
                is ClassAssociation -> {
                    label = context.reference
                }
                is FieldWithTargetType -> {
                    label = context.field.name + "\n" + context.field.cardinality(visualizationConfig.projectClassification.treatFinalFieldsAsMandatory)
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

            // TODO check for other types
            if(hasMoreEdges && context !is MethodClassUsage && context !is AnalyzeCall)  {
                arrowHead = "none"
                arrowTail = null
                dir = null
            }
        }.stream()
    }.toList().let { dot.edges.addAll(it) }
}

