package com.kn.diagrams.generator.config

import com.intellij.psi.PsiClass
import com.kn.diagrams.generator.graph.GraphNode
import com.kn.diagrams.generator.graph.GraphRestrictionFilter
import com.kn.diagrams.generator.graph.TraversalFilter

interface BaseDiagramConfiguration {

    fun restrictionFilter(): GraphRestrictionFilter

    fun brandWithRootNode(rootNodeId: String)
}

abstract class DiagramConfiguration(var rootClass: String): BaseDiagramConfiguration {

    abstract fun traversalFilter(): TraversalFilter

    companion object // use for serialization
}

fun String.attacheMetaData(config: Any) = replace("@startuml", "@startuml\n\n" + config.metaDataSection() + "\n\n")

