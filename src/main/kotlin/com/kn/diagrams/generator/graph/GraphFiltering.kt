package com.kn.diagrams.generator.graph

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.kn.diagrams.generator.generator.code.layer
import com.kn.diagrams.generator.inCase
import com.kn.diagrams.generator.notReachable


interface TraversalFilter {
    fun accept(node: GraphNode): Boolean
}

val ClassReference.isEnum get() = classType == ClassType.Enum

class GraphRestrictionFilter(val global: ProjectClassification, private val restriction: GraphRestriction)  {
    fun acceptClass(clazz: ClassReference): Boolean {
        return clazz.accept()
    }

    private fun ClassReference.accept() =
        with(global) {
            with(restriction) {
                (global.includedProjects == "" || global.includedProjects.bySemicolon().any { path.startsWith(it) })
                        && includedAndNotExcluded(classNameIncludeFilter, classNameExcludeFilter, classPackageIncludeFilter, classPackageExcludeFilter)
                        && layerIncludedAndNotExcluded(global, includeLayers, excludeLayers)
                        && cutTests inCase isTest()
                        && cutClient inCase isClient()
                        && cutMappings inCase isMapping()
                        && cutDataAccess inCase isDataAccess()
                        && cutDataStructures inCase isDataStructure()
                        && cutEnum inCase isEnum
                        && cutInterfaceStructures inCase isInterfaceStructure()
            }
        }

    fun acceptMethod(method: AnalyzeMethod): Boolean {
        return with(method) {
            with(restriction) {
                includedAndNotExcluded(methodNameIncludeFilter, methodNameExcludeFilter)
                        && !isJavaObjectMethod()
                        && (containingClass.classType == ClassType.Interface || cutGetterAndSetter inCase isGetterOrSetter())
                        && cutConstructors inCase isConstructor
            }
        }
    }

    fun removeClass(clazz: ClassReference, graph: GraphDefinition): Boolean {
        val inheritedClasses = graph.allInheritedClasses(clazz)
        val inheritedIncludingSelf = (graph.classes[clazz.id()]?.annotations
                ?: emptyList()) union inheritedClasses.flatMap { it.annotations }

        val byAnnotation = notEmptyAnd(restriction.removeByAnnotation) { reqExs ->
            reqExs.any { reqEx ->
                inheritedIncludingSelf.any { anyAnnotation -> reqEx.matches(anyAnnotation.type.name) }
            }
        }
        val byInheritance = notEmptyAnd(restriction.removeByInheritance) { reqExs ->
            reqExs.any { reqEx -> inheritedClasses.any { inherited -> reqEx.matches(inherited.reference.name) } }
        }

        val classExcluded = clazz.excluded(restriction.removeByClassName, restriction.removeByClassPackage)

        return byAnnotation || byInheritance || classExcluded
    }

}

class GraphTraversalFilter(val global: ProjectClassification, private val traversal: GraphTraversal) : TraversalFilter {
    override fun accept(node: GraphNode) = when (node) {
        is AnalyzeClass -> node.reference.accept()
        is AnalyzeMethod -> node.accept() && node.containingClass.accept()
        is AnalyzeField -> true
        is TraceNode -> true
        else -> notReachable()
    }

    private fun AnalyzeMethod.accept(): Boolean {
        return with(traversal) { with(global){
            includedAndNotExcluded(methodNameIncludeFilter, methodNameExcludeFilter)
                    && hidePrivateMethods inCase (visibility != MethodVisibility.PUBLIC)
                    && hideInterfaceCalls inCase insideInterface()
                    && hideMethodsInDataStructures inCase (containingClass.isDataStructure() || containingClass.isInterfaceStructure())
        }}
    }

    private fun ClassReference.accept(): Boolean {
        return with(global) {
            with(traversal) {
                includedAndNotExcluded(classNameIncludeFilter, classNameExcludeFilter, classPackageIncludeFilter, classPackageExcludeFilter)
                        && layerIncludedAndNotExcluded(global, includeLayers, excludeLayers)
                        && hideDataStructures inCase isDataStructure()
                        && hideMappings inCase isMapping()
                        && hideInterfaceCalls inCase (classType == ClassType.Interface)
                        && (!onlyShowApplicationEntryPoints || isEntryPoint())
            }
        }
    }


}

fun ClassReference.layerIncludedAndNotExcluded(classification: ProjectClassification, includes: List<String>, excludes: List<String>): Boolean{
    val layer = layer(classification).name

    val included = includes.isEmpty() || layer in includes
    val excluded = includes.isNotEmpty() && layer in excludes

    return included && !excluded
}

interface Filterable{
    val name: String
    val path: String
}

fun AnalyzeMethod.includedAndNotExcluded(
        nameIncludedPattern: String,
        nameExcludedPattern: String): Boolean {
    return name.included(nameIncludedPattern) && !name.excluded(nameExcludedPattern)
}

fun Filterable.includedAndNotExcluded(
        nameIncludedPattern: String,
        nameExcludedPattern: String,
        pathIncludedPattern: String,
        pathExcludedPattern: String): Boolean {
    return included(nameIncludedPattern, pathIncludedPattern) && !excluded(nameExcludedPattern, pathExcludedPattern)
}

fun String.included(semicolonSeparatedPatterns: String): Boolean {
    return emptyOr(semicolonSeparatedPatterns) { regEx -> regEx.any { it.matches(this) } }
}

fun String.excluded(semicolonSeparatedPatterns: String): Boolean {
    return notEmptyAnd(semicolonSeparatedPatterns) { regEx -> regEx.any { it.matches(this) } }
}

fun Filterable.included(namePattern: String, pathPattern: String): Boolean {
    return name.included(namePattern) && path.included(pathPattern)
}

fun Filterable.excluded(namePattern: String, pathPattern: String): Boolean {
    return name.excluded(namePattern) || path.excluded(pathPattern)
}

fun AnalyzeMethod.insideInterface() = containingClass.classType == ClassType.Interface

fun AnalyzeMethod.isGetterOrSetter(): Boolean {
    return (name.startsWith("get") && parameter.isEmpty() && !isVoid())
            || (name.startsWith("is") && parameter.isEmpty() && !isVoid())
            || (name.startsWith("has") && parameter.isEmpty() && !isVoid())
            || (name.startsWith("with") && parameter.isNotEmpty() && !isVoid())
            || (name.startsWith("set") && isVoid())
}

private fun AnalyzeMethod.isVoid() = returnTypeDisplay == null || returnTypeDisplay == "void"

fun PsiMethod.isPrivate(): Boolean {
    return !hasModifierProperty(PsiModifier.PUBLIC)
}

fun AnalyzeMethod.isJavaObjectMethod(): Boolean {
    return sequenceOf("toString", "equals", "hasCode", "getClass").contains(name)
}

