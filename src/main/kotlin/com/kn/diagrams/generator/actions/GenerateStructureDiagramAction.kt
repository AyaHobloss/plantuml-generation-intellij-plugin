package com.kn.diagrams.generator.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.actionSystem.AnActionEvent
import com.kn.diagrams.generator.cast
import com.kn.diagrams.generator.config.StructureConfiguration
import com.kn.diagrams.generator.generator.createStructureDiagramUmlContent
import com.kn.diagrams.generator.graph.AnalyzeMethod
import com.kn.diagrams.generator.settings.ConfigurationDefaults


class GenerateStructureDiagramAction : AbstractDiagramAction<StructureConfiguration>() {

    override fun createDiagramContent(event: AnActionEvent): List<Pair<String, String>> {
        return generateWith(event.classBasedContext())
    }

    override fun generateWith(actionContext: ActionContext): List<Pair<String, String>> {
        return createStructureDiagramUmlContent(actionContext)
    }


}
