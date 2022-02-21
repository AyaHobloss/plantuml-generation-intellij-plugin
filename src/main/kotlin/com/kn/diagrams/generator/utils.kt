package com.kn.diagrams.generator

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.util.castSafelyTo
import com.intellij.util.concurrency.NonUrgentExecutor
import com.kn.diagrams.generator.config.DiagramConfiguration
import com.kn.diagrams.generator.config.loadFromMetadata
import com.kn.diagrams.generator.graph.psiClassFromQualifiedName
import java.io.File
import com.kn.diagrams.generator.graph.psiClassFromQualifiedName
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

fun File.createIfNotExists(): File {
    File(path.substringBeforeLast("\\")).mkdirs()
    if(!exists()){
        createNewFile()
    }

    return this
}

infix fun <T> Sequence<T>.union(elements: Sequence<T>): Sequence<T> {
    return sequenceOf(this, elements).flatten()
}

// !!! do not stack read actions - when a write action comes in, it will produce a dead lock !!!
fun <T> inReadAction(action: () -> T): T { // read access needed for PSI classes/searches
    return ApplicationManager.getApplication().runReadAction<T>(action)
}

// !!! do not stack read actions - when a write action comes in, it will produce a dead lock !!!
fun doReadAction(action: () -> Unit) { // read access needed for PSI classes/searches
    return ApplicationManager.getApplication().runReadAction(action)
}

fun <T : Any> T?.toSingleList() = listOfNotNull(this)

fun notReachable(): Nothing {
    throw RuntimeException()
}

fun asyncWriteAction(action: () -> Unit){
    ApplicationManager.getApplication().invokeLater {
        ApplicationManager.getApplication().runWriteAction {
            action()
        }
    }
}

fun writeDiagramFile(directory: PsiDirectory, diagramFileName: String, diagramContent: String) {
    val umlFile = directory.findFile(diagramFileName)
    if (umlFile == null) {
        val type = FileTypeRegistry.getInstance().getFileTypeByFileName(diagramFileName)
        val newFile = PsiFileFactory.getInstance(directory.project).createFileFromText(diagramFileName, type, diagramContent)
        directory.add(newFile)
    } else {
        PsiDocumentManager.getInstance(directory.project).getDocument(umlFile.containingFile)
                ?.setText(diagramContent)
    }
}

fun <K, V> MutableMap<K, MutableSet<V>>.append(key: K, value: V) {
    val entry = getOrPut(key, { mutableSetOf() })
    entry.add(value)
}

inline fun <reified T> Any?.cast(): T?{
    if(this == null) return null

    return if (this is T) this else null
}

fun <T> nonBlockingRead(action: () -> T): T? = ReadAction
        .nonBlocking<T> { action() }
        .submit(NonUrgentExecutor.getInstance())
        .blockingGet(2, TimeUnit.MINUTES)

// language.id is not set when PlantUML Integration plugin is not installed
fun PsiFile?.isPlantUML() = this?.name?.endsWith(".puml", ignoreCase = true) ?: false
fun PsiFile?.isJava() = this?.language?.id == "JAVA"

fun PsiFile.findClasses():List<PsiClass> {
    val classes = mutableListOf<PsiClass>()

    when (name.substringAfterLast(".").toLowerCase()) {
        "java" -> {
            val visitor = object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    if (element is PsiClass) {
                        classes.add(element)
                    }
                    super.visitElement(element)
                }
            }
            accept(visitor)
        }
        "puml" -> DiagramConfiguration
                .loadFromMetadata(text)
                ?.castSafelyTo<DiagramConfiguration>()
                ?.rootClass?.let { it.psiClassFromQualifiedName(project)?.let { it1 -> classes.add(it1) } }
    }

    return classes
}

infix fun Boolean.inCase(condition: Boolean) = !this || !condition

fun String.escape() = this
        .replace("<", "\\<")
        .replace(">", "\\>")
        .replace("\"", "\\\"")

fun String.escapeHTML() = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

fun throwExceptionIfCanceled() {
    if (ProgressManager.getGlobalProgressIndicator()?.isCanceled == true) throw RuntimeException()
}

class ProgressBar{
    companion object {
        var text: String?
            get() = ProgressManager.getGlobalProgressIndicator()?.text
            set(value) {
                throwExceptionIfCanceled()
                ProgressManager.getGlobalProgressIndicator()?.text = value
            }
    }
}

fun Int.clamp(min: Int, max: Int) = min(max, max(this, min))

fun Double?.default(default: Double) = if(this == null) default else this
