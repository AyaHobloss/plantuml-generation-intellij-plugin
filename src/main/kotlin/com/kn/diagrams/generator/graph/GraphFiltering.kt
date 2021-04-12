package com.kn.diagrams.generator.graph

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
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
                global.includedProjects.bySemicolon().any { path.startsWith(it) }
                        && isIncludedAndNotExcluded(classNameExcludeFilter, classNameIncludeFilter) { name }
                        && isIncludedAndNotExcluded(classPackageExcludeFilter, classPackageIncludeFilter) { path }
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
                isIncludedAndNotExcluded(methodNameExcludeFilter, methodNameIncludeFilter) { name }
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

        val classExcluded = with(restriction) {
            !isIncludedAndNotExcluded(removeByClassPackage, "") { clazz.path }
                    || !isIncludedAndNotExcluded(removeByClassName, "") { clazz.name }
        }

        return byAnnotation || byInheritance || classExcluded
    }

}

class GraphTraversalFilter(private val rootNode: GraphNode, val global: ProjectClassification, private val traversal: GraphTraversal) : TraversalFilter {
    override fun accept(node: GraphNode) = when (node) {
        rootNode -> true
        is AnalyzeClass -> node.reference.accept()
        is AnalyzeMethod -> node.accept() && node.containingClass.accept()
        else -> notReachable()
    }

    private fun AnalyzeMethod.accept(): Boolean {
        return with(traversal) { with(global){
            isIncludedAndNotExcluded(methodNameExcludeFilter, methodNameIncludeFilter) { name }
                    && hidePrivateMethods inCase (visibility != MethodVisibility.PUBLIC)
                    && hideInterfaceCalls inCase insideInterface()
                    && hideMethodsInDataStructures inCase (containingClass.isDataStructure() || containingClass.isInterfaceStructure())
        }}
    }

    private fun ClassReference.accept(): Boolean {
        return with(global) {
            with(traversal) {
                isIncludedAndNotExcluded(classNameExcludeFilter, classNameIncludeFilter) { name }
                        && isIncludedAndNotExcluded(classPackageExcludeFilter, classPackageIncludeFilter) { path }
                        && hideDataStructures inCase isDataStructure()
                        && hideMappings inCase isMapping()
                        && hideInterfaceCalls inCase (classType == ClassType.Interface)
                        && (!onlyShowApplicationEntryPoints || isEntryPoint())
            }
        }
    }

}

// TODO does it work for combined / additive excluded/include filter?!
fun isIncludedAndNotExcluded(excludes: String, includes: String, extractor: () -> String) =
        emptyOr(includes) { regEx -> regEx.any { it.matches(extractor()) } }
                && emptyOr(excludes) { regEx -> regEx.none { it.matches(extractor()) } }


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

