package com.kn.diagrams.generator.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiMethod
import com.kn.diagrams.generator.config.FlowConfiguration
import com.kn.diagrams.generator.generator.FlowDiagramGenerator
import com.kn.diagrams.generator.generator.relevantFlowElements
import com.kn.diagrams.generator.graph.annotationsMapped
import com.kn.diagrams.generator.settings.ConfigurationDefaults

class GenerateFlowDiagramsAction : AbstractDiagramAction<FlowConfiguration>() {

    override fun createDiagramContent(event: AnActionEvent): List<Pair<String, String>> {
        return generateWith(event.methodBasedContext{ hasTerminalAnnotation() })
    }

    override fun generateWith(actionContext: ActionContext): List<Pair<String, String>> {
        return FlowDiagramGenerator().createUmlContent(actionContext)
    }

}

fun PsiMethod.hasTerminalAnnotation() = annotationsMapped().any { a -> a.type.name == "FlowDiagramTerminal"  }
