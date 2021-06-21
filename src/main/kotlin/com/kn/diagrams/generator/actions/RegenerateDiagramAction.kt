package com.kn.diagrams.generator.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.file.PsiJavaDirectoryImpl
import com.intellij.util.castSafelyTo
import com.kn.diagrams.generator.asyncWriteAction
import com.kn.diagrams.generator.config.*
import com.kn.diagrams.generator.generator.*
import com.kn.diagrams.generator.inReadAction
import com.kn.diagrams.generator.isPlantUML
import com.kn.diagrams.generator.notifications.notifyErrorOccurred

open class RegenerateDiagramAction : AnAction() {

    protected var processDirectoriesRecursive: Boolean = false

    init {
        this.setInjectedContext(true)
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project!!

        event.filesFromDirectoryOrSelection().forEach { file ->
            event.startBackgroundAction("Regenerate Diagram") {
                val newDiagramText = when (val loadedConfig = DiagramConfiguration.loadFromMetadata(inReadAction { file.text })) {
                    is CallConfiguration -> {
                        val newMethodDiagrams = createCallDiagramUmlContent(loadedConfig, project)
                        newMethodDiagrams.firstOrNull()?.second
                    }
                    is StructureConfiguration -> {
                        createStructureDiagramUmlContent(loadedConfig, project).firstOrNull()?.second
                    }
                    is FlowConfiguration -> {
                        FlowDiagramGenerator().createUmlContent(loadedConfig, project).firstOrNull()?.second
                    }
                    is ClusterConfiguration -> {
                        createClusterDiagramUmlContent(loadedConfig, project).firstOrNull()?.second
                    }
                    is VcsConfiguration -> {
                        createVcsContent(loadedConfig, project).firstOrNull()?.second
                    }
                    else -> null
                }

                if (newDiagramText != null) {
                    asyncWriteAction {
                        val document = PsiDocumentManager.getInstance(project).getDocument(file.containingFile)
                        document?.setText(newDiagramText)
                    }
                } else {
                    notifyErrorOccurred(project)
                }
            }
        }

    }

    private fun AnActionEvent.filesFromDirectoryOrSelection() = getData(CommonDataKeys.PSI_FILE)?.let { sequenceOf(it) }
            ?: pumlFiles(getData(CommonDataKeys.PSI_ELEMENT))

    @OptIn(ExperimentalStdlibApi::class)
    fun pumlFiles(data: PsiElement?): Sequence<PsiFile> {
        val directories = mutableListOf(data.castSafelyTo<PsiDirectory>())

        val allFiles = mutableListOf<PsiFile>()
        while (directories.isNotEmpty()) {
            val currentDirectory = directories.removeLast() ?: continue

            allFiles.addAll(currentDirectory.files.filter { it.isPlantUML() })

            if (processDirectoriesRecursive) directories.addAll(currentDirectory.subdirectories)
        }

        return allFiles.asSequence()
    }

    override fun update(anActionEvent: AnActionEvent) {
        val project = anActionEvent.getData(CommonDataKeys.PROJECT)
        val file = anActionEvent.getData(CommonDataKeys.PSI_FILE)
        val directory = anActionEvent.getData(CommonDataKeys.PSI_ELEMENT).castSafelyTo<PsiJavaDirectoryImpl>()

        anActionEvent.presentation.isVisible = project != null
                && (file.isPlantUML() || directory?.isDirectory == true)
    }

}
