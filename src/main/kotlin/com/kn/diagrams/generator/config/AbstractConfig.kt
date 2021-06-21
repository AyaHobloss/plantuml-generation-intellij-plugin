package com.kn.diagrams.generator.config

import com.intellij.psi.PsiClass
import com.kn.diagrams.generator.graph.GraphNode
import com.kn.diagrams.generator.graph.GraphRestrictionFilter
import com.kn.diagrams.generator.graph.TraversalFilter

interface BaseDiagramConfiguration {

    fun diagramFileName(): String

    fun restrictionFilter(): GraphRestrictionFilter
}

abstract class DiagramConfiguration(val rootClass: String): BaseDiagramConfiguration {

    abstract fun traversalFilter(): TraversalFilter

    override fun diagramFileName() = rootClass.substringAfterLast(".")

    companion object // use for serialization
}

fun String.attacheMetaData(config: Any) = replace("@startuml", "@startuml\n\n" + config.metaDataSection() + "\n\n")

