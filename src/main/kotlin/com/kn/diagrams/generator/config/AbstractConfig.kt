package com.kn.diagrams.generator.config

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.util.lang.UrlClassLoader
import com.kn.diagrams.generator.cast
import com.kn.diagrams.generator.graph.GraphNode
import com.kn.diagrams.generator.graph.RestrictionFilter
import com.kn.diagrams.generator.graph.TraversalFilter
import com.kn.diagrams.generator.graph.psiClassFromQualifiedName
import java.lang.reflect.Method
import java.lang.reflect.Modifier

abstract class DiagramConfiguration(val rootClass: String, var extensionCallbackMethod: String? = "") {

    abstract fun restrictionFilter(project: Project): RestrictionFilter

    abstract fun traversalFilter(rootNode: GraphNode): TraversalFilter

    companion object // use for serialization

    fun diagramExtension(project: Project): ((String) -> String){
        return project
            .takeIf { extensionCallbackMethod?.contains("#") == true }
            ?.let { callbackClass(it) }
            ?.findCustomizationMethod()
            ?.let { method -> { diagramText -> method.invoke(null, diagramText).cast()!!  } }
            ?: { it }
    }

    private fun Class<*>?.findCustomizationMethod(): Method? {
        val methodName = extensionCallbackMethod?.substringAfter("#")

        return this?.methods
            ?.firstOrNull{ it.isStaticStringToString(methodName) }
    }

    private fun callbackClass(project: Project): Class<*>? {
        val className = extensionCallbackMethod?.substringBefore("#")

        val module = extensionCallbackMethod?.substringBefore("#")
            ?.psiClassFromQualifiedName(project)
            ?.let { ModuleUtilCore.findModuleForPsiElement(it.containingFile); }

        return module?.let { CompilerModuleExtension.getInstance(it) }
            ?.compilerOutputPath?.toNioPath()
            ?.let {
                val original = Thread.currentThread().contextClassLoader
                UrlClassLoader.build().parent(original).useCache().files(listOf(it)).allowBootstrapResources().get()
                    .loadClass(className)
            }
    }

    private fun Method.isStaticStringToString(methodName: String?) = (
            name == methodName
            && parameters.size == 1
            && parameters[0]?.type?.canonicalName == "java.lang.String"
            && returnType?.canonicalName == "java.lang.String"
            && Modifier.isStatic(modifiers)
    )
}


fun String.attacheMetaData(config: DiagramConfiguration) = replace("@startuml", "@startuml\n\n" + config.metaDataSection() + "\n\n")

