package com.kn.diagrams.generator.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.kn.diagrams.generator.asyncWriteAction
import com.kn.diagrams.generator.config.BaseDiagramConfiguration
import com.kn.diagrams.generator.findClasses
import com.kn.diagrams.generator.isJava
import com.kn.diagrams.generator.notifications.notifyErrorMissingClass
import com.kn.diagrams.generator.writeDiagramFile


abstract class AbstractDiagramAction<T : BaseDiagramConfiguration> : AnAction() {

    init {
        this.setInjectedContext(true)
    }

    override fun actionPerformed(event: AnActionEvent) {
        event.startBackgroundAction("Generate Diagrams ") { progressIndicator ->
            val diagrams: MutableMap<String, String> = mutableMapOf()

            val plantUMLDiagrams = createDiagramContent(event)

            for ((diagramFile, diagramContent) in plantUMLDiagrams) {
                diagrams[diagramFile] = diagramContent
            }

            progressIndicator.fraction = 0.98

            asyncWriteAction {
                val directory = event.file().containingDirectory!!
                for ((diagramFileName, diagramContent) in diagrams) {
                    writeDiagramToFile(directory, diagramFileName, diagramContent)
                }
            }
        }
    }

    protected open fun writeDiagramToFile(directory: PsiDirectory, diagramFileName: String, diagramContent: String) {
        writeDiagramFile(directory, diagramFileName, diagramContent)
    }

    override fun update(anActionEvent: AnActionEvent) {
        val project = anActionEvent.getData(CommonDataKeys.PROJECT)
        val file = anActionEvent.getData(CommonDataKeys.PSI_FILE)
        val indexesAreReady = project?.let { !DumbService.isDumb(it) } ?: false

        anActionEvent.presentation.isVisible = project != null && file.isJava() && indexesAreReady
    }

    protected abstract fun createDiagramContent(event: AnActionEvent): List<Pair<String, String>>

    abstract fun generateWith(actionContext: ActionContext): List<Pair<String, String>>
}

fun AnActionEvent.findFirstClass(): PsiClass {
    val psiClass = file().findClasses().firstOrNull()

    if (psiClass == null) {
        notifyErrorMissingClass(project)
    }

    return psiClass!!
}

fun AnActionEvent.document(file: PsiFile) = PsiDocumentManager.getInstance(project!!).getDocument(file.containingFile)
fun AnActionEvent.file() = getData(CommonDataKeys.PSI_FILE)!! // ensured by update()

fun AnActionEvent.startBackgroundAction(title: String, action: (ProgressIndicator) -> Unit) {
    ProgressManager.getInstance() // make it non-blocking with progress bar
            .run(object : Task.Backgroundable(getData(CommonDataKeys.PROJECT)!!, title) {

                override fun run(progressIndicator: ProgressIndicator) {
                    progressIndicator.isIndeterminate = false
                    progressIndicator.fraction = 0.0

                    action(progressIndicator)
                }
            })
}

fun PsiDirectory.findNonExistingFile(diagramFileName: String): String {
    return if(findFile(diagramFileName) != null){
        val noneExistingFileName = IntRange(0, 100)
                .map { diagramFileName + it }
                .firstOrNull { findFile(diagramFileName) == null }

        noneExistingFileName ?: diagramFileName
    } else diagramFileName
}
