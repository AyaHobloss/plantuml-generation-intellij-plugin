package com.kn.diagrams.generator.generator

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.kn.diagrams.generator.builder.DiagramDirection
import com.kn.diagrams.generator.builder.DotDiagramBuilder
import com.kn.diagrams.generator.config.CallConfiguration
import com.kn.diagrams.generator.config.attacheMetaData
import com.kn.diagrams.generator.graph.*
import com.kn.diagrams.generator.inReadAction
import com.kn.diagrams.generator.notifications.notifyErrorMissingPublicMethod
import com.kn.diagrams.generator.toSingleList


class CallDiagramGenerator {

    fun createUmlContent(config: CallConfiguration, project: Project): List<Pair<String, String>> {
        return config.perPublicMethod(project) { rootMethod ->
            val restrictionFilter = inReadAction { config.restrictionFilter(project) }

            val cache = GraphCache(project, restrictionFilter, config.projectClassification.searchMode)
            val root = inReadAction { cache.methodFor(rootMethod)!! }

            val edges = cache.search(config.traversalFilter(root)) {
                roots = root.toSingleList()
                forwardDepth = config.graphTraversal.forwardDepth
                backwardDepth = config.graphTraversal.backwardDepth
                edgeMode = config.details.edgeMode
            }.flatten()

            val dot = DotDiagramBuilder()
            val visualizationConfig = inReadAction { config.visualizationConfig(cache, project) }
            dot.direction = DiagramDirection.LeftToRight

            when (config.details.aggregation) {
                Aggregation.ByClass -> dot.aggregateByClass(edges, visualizationConfig)
                Aggregation.GroupByClass -> dot.groupByClass(edges, visualizationConfig)
                Aggregation.None -> dot.noAggregation(edges, visualizationConfig)
            }

            dot.create()
        }
    }
}



private fun CallConfiguration.perPublicMethod(project: Project, creator: (PsiMethod) -> String): List<Pair<String, String>> {
    val requestedMethod = rootMethod
    val diagramExtension = diagramExtension(project)

    val diagramsPerMethod =
        inReadAction {
            rootClass.psiClassFromQualifiedName(project)!!.methods
                .filter { requestedMethod == null || requestedMethod == inReadAction { it.toSimpleReference() } }
                .filter { requestedMethod != null || !it.isPrivate() }
        }.mapIndexed { i, rootMethod ->
                this.rootMethod = inReadAction { rootMethod.toSimpleReference() }
                val plainDiagram = creator(rootMethod)
                val diagramText = plainDiagram.attacheMetaData(this)

                "${i}_${inReadAction { rootMethod.name }}_calls" to diagramExtension(diagramText)
            }

    if (diagramsPerMethod.isEmpty()) {
        notifyErrorMissingPublicMethod(project, rootClass, rootMethod)
    }

    return diagramsPerMethod
}


fun CallConfiguration.visualizationConfig(cache: GraphCache, project: Project) = DiagramVisualizationConfiguration(
    rootMethod?.let { cache.methodFor(it.psiMethodFromSimpleReference(project)) } ?: cache.classes[ClassReference(rootClass).id()]!!,
    projectClassification,
    projectClassification.includedProjects,
    projectClassification.pathEndKeywords,
    details.showPackageLevels,
    false,
    false,
    details.showMethodParametersTypes,
    details.showMethodParametersNames,
    details.showMethodReturnType,
    details.showCallOrder,
    details.showDetailedClassStructure
)


