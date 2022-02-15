package com.kn.diagrams.generator.config

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.util.lang.UrlClassLoader
import com.kn.diagrams.generator.cast
import com.kn.diagrams.generator.graph.GraphNode
import com.kn.diagrams.generator.graph.RestrictionFilter
import com.kn.diagrams.generator.graph.TraversalFilter
import com.kn.diagrams.generator.graph.psiClassFromQualifiedName
import com.kn.diagrams.generator.notifications.notifyError
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Paths

val identityCallback: (String) -> String = { it }

abstract class DiagramConfiguration(
    val rootClass: String,
    @CommentWithValue("qualified.class.name#methodName - signature: public static String method(String)")
    var extensionCallbackMethod: String? = ""
) {

    abstract fun restrictionFilter(project: Project): RestrictionFilter

    abstract fun traversalFilter(rootNode: GraphNode): TraversalFilter

    companion object // use for serialization

    fun diagramExtension(project: Project): ((String) -> String){
        return extensionCallbackMethod
            .findModuleForClass(project)
            .classLoader()
            .findCustomizationClass(project)
            .findCustomizationMethod(project)
            .extensionCallback(project)
            ?: identityCallback
    }

    private fun Class<*>?.findCustomizationMethod(project: Project): Method? {
        if(this == null) return null

        val methodName = extensionCallbackMethod?.substringAfter("#")
        val method = this.methods.firstOrNull{ it.isStaticStringToString(methodName) }

        if(method == null) notifyError(project, "no suitable method found in class ${this.simpleName} with signature 'public static String $methodName(String)'")

        return method
    }

    private fun ClassLoader?.findCustomizationClass(project: Project): Class<*>? {
        if(this == null) return null

        val className = extensionCallbackMethod?.substringBefore("#")

        return try {
            loadClass(className)
        }catch (e: ClassNotFoundException){
            notifyError(project, e.message ?: "unable to find class for: $className")
            null
        }
    }

}

private fun Method.isStaticStringToString(methodName: String?) = (
        name == methodName
                && parameters.size == 1
                && parameters[0]?.type?.canonicalName == "java.lang.String"
                && returnType?.canonicalName == "java.lang.String"
                && Modifier.isStatic(modifiers)
                && Modifier.isPublic(modifiers)
        )

private fun Method?.extensionCallback(project: Project): ((String) -> String)? {
    if (this == null) return null

    return { diagramText ->
        try {
            invoke(null, diagramText).cast()!!
        }catch (e: Throwable){
            val exception = if(e is InvocationTargetException) e.cause ?: e else e
            notifyError(project, "An exception occurred while invoking the extension point: ${exception.message} - ${exception.stackTraceToString()}")
            diagramText
        }
    }
}

private fun String?.findModuleForClass(project: Project) = this
    ?.takeIf{ "#" in it }
    ?.substringBefore("#")
    ?.let { className ->
        val psiClass = className.psiClassFromQualifiedName(project)

        if(psiClass == null) notifyError(project, "unable to find class for: $className")

        psiClass
    }
    ?.let { ModuleUtilCore.findModuleForPsiElement(it.containingFile) }

private fun Module?.classLoader(): ClassLoader?{
    if(this == null) return null

    val original = Thread.currentThread().contextClassLoader

    val roots = ModuleRootManager.getInstance(this).orderEntries()
        .let { it.classes().roots + it.allLibrariesAndSdkClassesRoots }
        .mapNotNull { it.fileSystem.getNioPath(it) ?: Paths.get(it.path
            .replace("file://", "")
            .replace("jar://", "")
            .replace("//", "")
            .replace("!", "")
        )}


    return try {
        UrlClassLoader.build().parent(original).useCache()
            .files(roots)
            .allowBootstrapResources().get()
    } catch (e: Exception){
        notifyError(project, e.message ?: "unable to create ClassLoader")
        null
    }
}


fun String.attacheMetaData(config: DiagramConfiguration) = replace("@startuml", "@startuml\n\n" + config.metaDataSection() + "\n\n")

