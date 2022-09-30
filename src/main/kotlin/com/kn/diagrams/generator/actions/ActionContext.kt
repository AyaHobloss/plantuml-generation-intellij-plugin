package com.kn.diagrams.generator.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.kn.diagrams.generator.cast
import com.kn.diagrams.generator.config.BaseDiagramConfiguration
import com.kn.diagrams.generator.config.DiagramConfiguration
import com.kn.diagrams.generator.config.loadFromMetadata
import com.kn.diagrams.generator.findClasses
import com.kn.diagrams.generator.graph.*
import com.kn.diagrams.generator.inReadAction
import com.kn.diagrams.generator.isPlantUML


// encapsulate and decouple 'read actions'
open class ActionContext(val project: Project) {

    constructor(project: Project, rootNodeIds: MutableList<String>, config: BaseDiagramConfiguration?, fileName: String?, pattern: (ActionContext.(GraphNode, Int) -> String)?): this(project){
        this.rootNodeIds.addAll(rootNodeIds)
        this.config = config
        this.fileName = fileName
        this.plantUmlNamingPattern = pattern
    }

    val rootNodeIds: MutableList<String> = mutableListOf()
    var config: BaseDiagramConfiguration? = null

    var fileName: String? = null
    var plantUmlNamingPattern: (ActionContext.(GraphNode, Int) -> String)? = null

    fun defaultConfig(configDefaulter: () -> BaseDiagramConfiguration): ActionContext {
        if(config == null){
            config = configDefaulter()
        }
        return this
    }

    fun plantUmlNamingPattern(pattern: ActionContext.(GraphNode, Int) -> String): ActionContext{
        plantUmlNamingPattern = pattern
        return this
    }

    fun <C: BaseDiagramConfiguration> config(): C = config as C



}

class CallActionContext(project: Project, rootNodeIds: MutableList<String>, config: BaseDiagramConfiguration?, fileName: String?, pattern: (ActionContext.(GraphNode, Int) -> String)?): ActionContext(project, rootNodeIds, config, fileName, pattern)
class StructureActionContext(project: Project, rootNodeIds: MutableList<String>, config: BaseDiagramConfiguration?, fileName: String?, pattern: (ActionContext.(GraphNode, Int) -> String)?): ActionContext(project, rootNodeIds, config, fileName, pattern)

fun ActionContext.call() = CallActionContext(project, rootNodeIds, config, fileName, plantUmlNamingPattern)
fun ActionContext.structure() = StructureActionContext(project, rootNodeIds, config, fileName, plantUmlNamingPattern)

fun PsiFile.fileBasedContext(): ActionContext {
    return inReadAction {
        ActionContext(project).apply {
            fileName = name
            config = DiagramConfiguration.loadFromMetadata(text)
            val rootId = config.rootMethodId() ?: config.rootClassId()
            rootId?.let { rootNodeIds.add(it) }
        }
    }
}


fun AnActionEvent.guiBasedContext(configuration: DiagramConfiguration): ActionContext {
    return inReadAction {
        val rootClass = findFirstClass()

        ActionContext(project!!).apply {
            fileName = getData(CommonDataKeys.PSI_FILE)!!.name.substringBeforeLast(".").substringBefore("_")
            config = configuration
            rootNodeIds.add(rootClass.reference().id())
        }
    }
}

fun AnActionEvent.classBasedContext(): ActionContext {
    return readSafeContext { javaOrPumlFile ->
        if(javaOrPumlFile.isPlantUML()){
            config = DiagramConfiguration.loadFromMetadata(javaOrPumlFile.text)
            config.rootClassId()?.let { rootNodeIds.add(it) }
        } else {
            javaOrPumlFile
                    .findClasses().firstOrNull()
                    ?.reference()?.id()
                    ?.let { rootNodeIds.add(it) }
        }
    }
}

fun AnActionEvent.methodBasedContext(filter: PsiMethod.() -> Boolean = { true }): ActionContext {
    return readSafeContext { javaOrPumlFile ->
        if(javaOrPumlFile.isPlantUML()){
            config = DiagramConfiguration.loadFromMetadata(javaOrPumlFile.text)
            config.rootMethodId()?.let { rootNodeIds.add(it) }
        }else{
            val psiClass = javaOrPumlFile.findClasses().firstOrNull()
            psiClass?.methods
                    ?.filter { !it.isPrivate() && filter(it) }
                    ?.map { it.id() }
                    ?.let { rootNodeIds.addAll(it) }
        }
    }
}

fun AnActionEvent.directoryBasedContext(): ActionContext {
    return readSafeContext { fileOrDirectory ->
        config = fileOrDirectory.psiFile()
                ?.let { DiagramConfiguration.loadFromMetadata(it.text) }
    }
}

private fun AnActionEvent.readSafeContext(actions: ActionContext.(PsiFile) -> Unit): ActionContext{
    return inReadAction {
        val javaOrPumlFile= getData(CommonDataKeys.PSI_FILE)!!

        ActionContext(project!!).apply{
            fileName = javaOrPumlFile.name.substringBeforeLast(".")

            actions(this, javaOrPumlFile)
        }
    }
}

private fun PsiElement.psiFile(): PsiFile?{
    return when(this){
        is PsiFile -> this
        else -> null
    }
}

private fun BaseDiagramConfiguration?.rootClassId(): String? {
    if(this == null) return null

    return javaClass
            .let {
                try {
                    it.getMethod("getRootClass")
                }catch (e: NoSuchMethodException){
                    null
                }
            }
            ?.invoke(this)?.cast<String>()
            ?.let { ClassReference(it).id() }
}

private fun BaseDiagramConfiguration?.rootMethodId(): String? {
    if(this == null) return null

    return javaClass
            .let {
                try {
                    it.getMethod("getRootMethod")
                }catch (e: NoSuchMethodException){
                    null
                }
            }
            ?.invoke(this)?.cast<String>()
}
