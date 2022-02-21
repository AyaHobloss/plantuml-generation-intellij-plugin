package com.kn.diagrams.generator.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.actionSystem.AnActionEvent
import com.kn.diagrams.generator.config.CallConfiguration
import com.kn.diagrams.generator.generator.createCallDiagramUmlContent


class GenerateCallDiagramsAction : AbstractDiagramAction<CallConfiguration>() {

    override fun createDiagramContent(event: AnActionEvent): List<Pair<String, String>> {
        return generateWith(event.methodBasedContext())
    }

    override fun generateWith(actionContext: ActionContext): List<Pair<String, String>> {
        return createCallDiagramUmlContent(actionContext)
    }


}
