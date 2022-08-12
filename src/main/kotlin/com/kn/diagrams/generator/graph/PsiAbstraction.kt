package com.kn.diagrams.generator.graph

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTypesUtil
import com.kn.diagrams.generator.*
import org.jetbrains.java.generate.psi.PsiAdapter
import java.util.*

// TODO later migrate to UAST? - get decoupled from Java and allow other languages - https://plugins.jetbrains.com/docs/intellij/uast.html

//class VariableInteraction(val method: AnalyzeMethod, val variableName: String, val text: String): GraphNode {
//    val id = method.id+"$"+variableName
//
//    override fun id() = id
//
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (other !is VariableInteraction) return false
//
//        if (id != other.id) return false
//
//        return true
//    }
//
//    override fun hashCode(): Int {
//        return id.hashCode()
//    }
//
//
//}

class TraceNode(val id: String, val text: String): GraphNode{

    override fun id() = id

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TraceNode) return false

        if (id != other.id) return false
        if (text != other.text) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + text.hashCode()
        return result
    }

    override fun toString(): String {
        return "TraceNode(id='$id', text='$text')"
    }
}

class TraceContext(val text: String): EdgeContext

class EdgeBuilder {
    var fromNode: TraceNode? = null
    var toNode: TraceNode? = null

    fun from(text: String, vararg nodeComponents: Any?){
        fromNode = TraceNode(id = calcId(*nodeComponents), text = text)
    }

    fun  to(text: String, vararg nodeComponents: Any?){
        toNode = TraceNode(id = calcId(*nodeComponents), text = text)

        fromNode!!.let { traceNodes += it  }
        toNode!!.let { traceNodes += it  }
    }

    fun buildEdge(){
        traceEdges += GraphDirectedEdge(fromNode!!, toNode!!, TraceContext(fromNode!!.text +" -> " + toNode!!.text).toSingleList())
    }

    private fun calcId(vararg nodeComponents: Any?): String{
        return nodeComponents.joinToString(";;") {
            when(it) {
                is AnalyzeField -> it.id()
                is AnalyzeMethod -> it.id()
                is AnalyzeCall -> it.id()
                is AnalyzeClass -> it.id()
                is PsiField -> it.id()
                is PsiMethod -> it.id()
                is String -> it
                null -> "null"
                else ->  {
                    "unknown $it"
                }
            }
        }
    }

}

fun edge(builder: EdgeBuilder.() -> Unit){
    builder(EdgeBuilder())
}

val traceNodes: MutableList<GraphNode> = mutableListOf()
val traceEdges: MutableList<GraphDirectedEdge> = mutableListOf()

private val cache = mutableMapOf<GraphNode, MutableList<GraphDirectedEdge>>()
fun traceCache(): MutableMap<GraphNode, MutableList<GraphDirectedEdge>> {
    if(cache.isEmpty()){
        traceEdges.groupBy { it.to }
            .mapValues { it.value.map { GraphDirectedEdge(it.to, it.from, it.context) } }
            .forEach{ cache.computeIfAbsent(it.key){ mutableListOf() }.addAll(it.value)   }
        traceEdges.groupBy { it.from }.forEach{ cache.computeIfAbsent(it.key){ mutableListOf() }.addAll(it.value)   }
    }

    return cache
}

class AnalyzeClass(clazz: PsiClass, filter: GraphRestrictionFilter) : GraphNode {
    val reference: ClassReference = ClassReference(clazz)
    val classType: ClassType = clazz.type()
    val fields: Map<String, AnalyzeField>
    val methods: Map<String, AnalyzeMethod>
    val calls: Map<String, List<AnalyzeCall>>
    val superTypes: List<ClassReference> = clazz.supers.map { it.reference() }.filter { filter.acceptClass(it) }
    val annotations: List<AnalyzeAnnotation> = clazz.annotations.map { AnalyzeAnnotation(it) }


    init {
        fields = clazz.fields
                .filterNot { it.hasModifierProperty(PsiModifier.STATIC) && it !is PsiEnumConstant }
                .map { AnalyzeField(it) }
                .associateBy { it.name }

        val relevantMethods = clazz.methods.asSequence().map { it to AnalyzeMethod(it) }.filter { filter.acceptMethod(it.second) }
        methods = relevantMethods.map { it.second.id() to it.second }.toMap()

        calls = relevantMethods.asSequence()
            .map { it.first }
            .flatMap { psiMethod ->
                val virtualInheritanceCalls = psiMethod.findSuperMethodSignaturesIncludingStatic(true).map {
                    // TODO Trace
                    AnalyzeCall(it.method, psiMethod, sequence = -1)
                }.asSequence()
                val directCalls = psiMethod.visitCalls<AnalyzeCall> { psiCall, i ->
                    val targetMethod = psiCall.resolveMethod()
                    if (targetMethod?.containingClass != null) {
                        add(AnalyzeCall(psiMethod, targetMethod, psiCall, i))



                        psiCall.argumentList?.expressions?.forEachIndexed { i, argument ->
                            if(argument.lastChild.text.startsWith("(") || (psiMethod.name == "save" && psiMethod?.containingClass?.name == "TestFacadeImpl")){
                                "".toString()
                            }
                            val arg = when(argument){
                                is PsiReferenceExpression -> argument.lastChild.text
                                else -> argument.text
                            }
                            val targetParameter = targetMethod.parameters[i].name

                            edge {
                                from("call argument $i", psiMethod, arg)
                                to("${targetMethod?.containingClass?.name}.${targetMethod?.name}(def $targetParameter)", targetMethod, targetParameter)
                                buildEdge()
                            }
                        }
                    }
                }

                directCalls union virtualInheritanceCalls
            }.groupBy { it.source.classReference.id() }

        relevantMethods.asSequence()
            .forEach { (psiMethod, method) ->
                val variables = mutableMapOf<String, PsiVariable>()
                val variableTrace = mutableMapOf<String, MutableList<GraphNode>>()

                fun EdgeBuilder.timeTraceVariable(variable: String){
                    val trace = variableTrace.computeIfAbsent(variable){ mutableListOf() }
                    trace.add(toNode!!)
                    if(trace.size > 1){
                        traceEdges += GraphDirectedEdge(trace[trace.size-2], trace[trace.size-1], TraceContext("time").toSingleList())
                    }
                }

                psiMethod.visitAssignments { psiElement, i ->
                    if(psiElement is PsiVariable){
//                        variables[psiElement.name ?: "noName"] = psiElement // TODO multiple definitions in other blocks
                    }

                    if(psiElement is PsiDeclarationStatement){
                        val varName = psiElement.firstChild.children[3].text ?: "noName"
                        variables[varName] = psiElement.firstChild as PsiLocalVariable
                        variableTrace.computeIfAbsent(varName){ mutableListOf() }.add(TraceNode(psiElement.text, psiElement.text))
                    }

                    if(psiElement is PsiReturnStatement){
                        if (psiMethod.containingClass?.name?.contains("TestTraceMapperImpl") == true) {
                            "".toString()
                        }
                        psiElement.children.forEach {
                            if(it is PsiReferenceExpression){
                                edge {
                                    from("local variable usage", method, psiElement.children[2].lastChild.text)
                                    to("method return", method, "return")
                                    buildEdge()
                                }
                            }
                        }
                    }

                    if(psiElement is PsiAssignmentExpression) {



                        val leftSideVariable = when(psiElement.firstChild.firstChild){
                            is PsiReferenceExpression -> psiElement.firstChild.firstChild.text
                            is PsiReferenceParameterList -> psiElement.firstChild.text
                            else -> null
                        }
                        val leftSideVariableProperty = if(psiElement.firstChild.firstChild is PsiReferenceExpression){
                            psiElement.firstChild?.lastChild?.text
                        } else null

                        val rightSideVariable = when(psiElement.lastChild){
                            is PsiReferenceExpression -> psiElement.lastChild.firstChild.takeIf { it is PsiReferenceExpression }?.text ?: psiElement.lastChild.lastChild.text
                            else -> null
                        }

                        val rightSideVariableProperty = if(psiElement.lastChild is PsiReferenceExpression && psiElement.lastChild.firstChild is PsiReferenceExpression){
                            psiElement.lastChild.lastChild.text
                        } else null

                        val rightSideLiteral = if(psiElement.lastChild is PsiLiteralExpression){
                            psiElement.lastChild.text
                        } else null

                        if (psiMethod.containingClass?.name?.contains("TestFacadeImpl") == true) {
                            "".toString()
                        }

                        val leftSideClass = PsiTypesUtil.getPsiClass(variables[leftSideVariable]?.type)
                        val rightSideClass = PsiTypesUtil.getPsiClass(variables[rightSideVariable]?.type)
                        val leftSideField = leftSideClass?.fields?.firstOrNull { it.name == leftSideVariableProperty }
                        val rightSideField = rightSideClass?.fields?.firstOrNull { it.name == rightSideVariableProperty }

                        when(val rightSide = psiElement.lastChild){
                            is PsiMethodCallExpression -> {
                                edge {
                                    val nodeComponents = rightSide.resolveMethod()
                                    from("assignment right side with method return value ${nodeComponents?.name}(...)", nodeComponents, "return")
                                    // TODO can be direct assignment, by property, by method call - but different type
                                    to(psiElement.text.escape(), method, leftSideVariable, psiElement.text)
                                    timeTraceVariable(leftSideVariable!!)
                                }
                            }
                        }

                        if(leftSideVariable != null){
                            if(rightSideLiteral != null){
                                edge {
                                    from("literal assignment '${rightSideLiteral.escape()}'", method, rightSideLiteral.escape())
                                    to(psiElement.text.escape(), method, leftSideVariable, psiElement.text)
                                    timeTraceVariable(leftSideVariable!!)
                                }
                            }else if(rightSideVariable != null){
                                edge {
                                    from("assignment by variable $rightSideVariable", method, rightSideVariable)
                                    to(psiElement.text.escape(), method, leftSideVariable, psiElement.text)
                                    timeTraceVariable(leftSideVariable)

                                    if(rightSideField != null){
                                        traceEdges += GraphDirectedEdge(AnalyzeField(rightSideField), fromNode!!, TraceContext("field read").toSingleList())
                                    }
                                    if(leftSideField != null){
                                        traceEdges += GraphDirectedEdge(toNode!!, AnalyzeField(leftSideField), TraceContext("field write").toSingleList())
                                    }
                                }
                            }
                        }

                        if(rightSideField != null && leftSideField != null){
                            val sourceLocalVariable = variables[rightSideVariable]
                            if(sourceLocalVariable is PsiParameter){
                                edge {
                                    from("local variable usage with property $rightSideVariable.$rightSideVariableProperty", method, rightSideVariable)
                                    to(psiElement.text.escape(), leftSideField, psiElement.text)
                                    timeTraceVariable(leftSideVariable!!)
                                }
                            }

                            if (psiMethod.containingClass?.name?.contains("TraceMapper") == true) {
                                "".toString()
                            }
                        }

                    }
                }
                if (psiMethod.containingClass?.name?.contains("TraceMapper") == true) {
                    "".toString()
                }
            }


    }

    override fun id() = reference.id()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnalyzeClass) return false

        if (reference != other.reference) return false

        return true
    }

    override fun hashCode(): Int {
        return reference.hashCode()
    }

    override fun toString(): String {
        return reference.name
    }

}


inline fun <T> PsiMethod.visitCalls(crossinline action: MutableList<T>.(PsiCall, Int) -> Unit): Sequence<T> {
    val callVisitor = object : PsiRecursiveElementVisitor() {
        var counter: Int = 0
        var elements: MutableList<T> = mutableListOf()
        override fun visitElement(call: PsiElement) {
            if (call is PsiCall) {
                action(elements, call, counter)
                counter++
            }
            super.visitElement(call)
        }
    }
    accept(callVisitor)

    return callVisitor.elements.asSequence()
}

inline fun PsiMethod.visitAssignments(crossinline action: (PsiElement, Int) -> Unit) {
    val callVisitor = object : PsiRecursiveElementVisitor() {
        var counter: Int = 0
        override fun visitElement(call: PsiElement) {
            action(call, counter)
            counter++
            super.visitElement(call)
        }
    }
    accept(callVisitor)
}


class AnalyzeMethod(method: PsiMethod) : AnalyzeAttribute(method.name, method.annotationsMapped()), GraphNode {
    val id: String = method.id()
    val containingClass: ClassReference = method.containingClass!!.reference()
    val visibility: MethodVisibility = method.modifierList.visibility()
    val returnTypeDisplay: String? = method.returnType?.presentableText
    val returnTypes: List<ClassReference> = method.returnType.structureRelevantTypes().map { it.reference() }
    val parameter: List<MethodParameter> = method.parameterList.parameters
            .map { MethodParameter(it.name, it.type, it.annotationsMapped()) }
    val javaDoc = method.javaDoc()
    val isConstructor = method.isConstructor

    override fun id(): String {
        return id
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnalyzeMethod) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return id
    }
}

class AnalyzeField(field: PsiField) : GraphNode, Variable(field.name, field.type, field.annotationsMapped()) {
    val id = field.id()
    val containingClass: ClassReference? = field.containingClass?.reference()
    val visibility: MethodVisibility = field.modifierList?.visibility() ?: MethodVisibility.PACKAGE_LOCAL
    val isEnumInstance: Boolean = field is PsiEnumConstant
    val isFinal: Boolean = field.hasModifier(JvmModifier.FINAL)

    override fun id() = id

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AnalyzeField

        if (containingClass != other.containingClass) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = containingClass?.hashCode() ?: 0
        result = 31 * result + name.hashCode()
        return result
    }

    override fun toString(): String {
        return "${containingClass?.toString()}.$name"
    }

}


class AnnotationParameter(val name: String, val value: String)

open class AnalyzeAttribute(val name: String, val annotations: List<AnalyzeAnnotation>) {
    open fun id() = name
}

fun List<AnalyzeAnnotation>.withName(name: String) = firstOrNull { it.type.name == name }


class ClassReference: Filterable {
    val displayName: String
    override val name: String
    override val path: String
    val classType: ClassType
    val absolutePath: String?

    constructor(annotation: PsiAnnotation) {
        classType = ClassType.Class // no annotation type needed yet
        absolutePath = annotation.containingFile.originalFile.virtualFile?.canonicalPath
        name = annotation.qualifiedName?.substringAfterLast(".") ?: "no qualified name"
        displayName = name
        path = annotation.qualifiedName?.substringBeforeLast(".") ?: "no qualified name"
    }

    constructor(clazz: PsiClass) {
        classType = clazz.type()
        absolutePath = clazz.containingFile.originalFile.virtualFile?.canonicalPath
        name = clazz.qualifiedName?.substringAfterLast(".") ?: "no qualified name"
        displayName = clazz.name + (clazz.typeParameterList?.text ?: "")
        path = clazz.qualifiedName?.substringBeforeLast(".") ?: "no qualified name"
    }

    constructor(qualifiedName: String){
        // TODO fix interface mapping!
        classType = if(qualifiedName.endsWith("Service") || qualifiedName.endsWith("Facade")) ClassType.Interface else ClassType.Class

        path = qualifiedName.substringBeforeLast(".")
        name = qualifiedName.substringAfterLast(".")
        displayName = name
        absolutePath = ""

    }


    fun id() = qualifiedName()

    fun qualifiedName() = "$path.$name"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClassReference) return false

        if (name != other.name) return false
        if (path != other.path) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + path.hashCode()
        return result
    }

    override fun toString(): String {
        return name
    }
}

data class MethodReference(val classReference: ClassReference, val method: String) {
    fun id() = classReference.id().replace(";", ".") + ";" + method
}


enum class MethodVisibility(val value: String) {
    PUBLIC("public"),
    PROTECTED("protected"),
    PRIVATE("private"),
    PACKAGE_LOCAL("packageLocal")
}

class AnalyzeCall(callSource: PsiMethod, callTarget: PsiMethod, call: PsiCall? = null, val sequence: Int) : EdgeContext {
    val source: MethodReference = callSource.reference()
    val target: MethodReference = callTarget.reference()
    val parameters: List<String> = call?.argumentList?.expressions?.map { it.text } ?: emptyList()

    val annotations: List<AnalyzeAnnotation> = call?.parent?.children
            ?.mapNotNull { it.cast<PsiModifierList>() }
            ?.flatMap {
                it.children
                        .mapNotNull { it.cast<PsiAnnotation>() }
                        .map { AnalyzeAnnotation(it) }
            } ?: emptyList()

    fun id() = source.method + "#" + target.method

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnalyzeCall) return false

        if (id() != other.id()) return false

        return true
    }

    override fun hashCode(): Int {
        return id().hashCode()
    }

    override fun toString(): String {
        return source.method + "->" + target.method
    }
}


fun PsiMethod.javaDoc(): String? {
    var javadoc = (docComment ?: modifierList.children.asSequence()
            .firstOrNull { it is PsiDocComment })
            ?.text
            ?.replace("\n", "&#10;")
            ?.replace("/**", "")
            ?.replace("*/", "")
            ?.replace("    ", "")
            ?.replace("  ", " ")
            ?.replace("  ", " ")
            ?.replace("*", "")

    if (javadoc == null) {
        javadoc = findSuperMethods()
                .mapNotNull {
                    (it.docComment ?: it.modifierList.children.asSequence().firstOrNull { it is PsiDocComment })
                }
                .firstOrNull()
                ?.text
                ?.replace("\n", "&#10;")
                ?.replace("/**", "")
                ?.replace("*/", "")
                ?.replace("    ", "")
                ?.replace("  ", " ")
                ?.replace("  ", " ")
                ?.replace("*", "")
    }

    return javadoc?.escape()
}

class AnalyzeAnnotation(annotation: PsiAnnotation) {
    val type: ClassReference = ClassReference(annotation)
    val parameter: List<AnnotationParameter> = annotation.parameterList.attributes
            .map {
                AnnotationParameter(it.attributeName, it.literalValue
                        ?: "")
            }

    fun attribute(name: String) = parameter.firstOrNull { it.name == name }
}

private fun PsiType.isCollectionOrMap(): Boolean {
    val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return false

    return PsiAdapter.isCollectionType(PsiElementFactory.getInstance(project), this)
            || PsiAdapter.isMapType(PsiElementFactory.getInstance(project), this)
}
abstract class Variable(val name: String, type: PsiType, val annotations: List<AnalyzeAnnotation>) {
    val types: List<ClassReference> = type.structureRelevantTypes().map { it.reference() }
    val isCollection: Boolean = type is PsiArrayType || type.isCollectionOrMap()
    val isPrimitive: Boolean = type is PsiPrimitiveType
    val typeDisplay: String = type.presentableText
}

class MethodParameter(name: String, type: PsiType, annotations: List<AnalyzeAnnotation>) : Variable(name, type, annotations)

fun PsiType?.structureRelevantTypes(): List<PsiClass> {
    if (this == null) return emptyList()

    val processed = mutableSetOf<PsiType>()
    val allTypes = mutableSetOf<PsiClass>()
    val typesToSearch = Stack<PsiType>()
    typesToSearch.push(this)

    while (typesToSearch.isNotEmpty()) {
        val current = typesToSearch.pop()
        processed.add(current)

        if (current is PsiArrayType) {
            typesToSearch.add(current.componentType)
        }

        if (current is PsiClassReferenceType) {
            typesToSearch.addAll(current.parameters)
        }

        val currentClass = PsiTypesUtil.getPsiClass(current) ?: continue
        allTypes.add(currentClass)
    }

    return allTypes.toList()
}

enum class ClassType { Interface, Enum, Class }

fun PsiClass.type() = when {
    isInterface -> ClassType.Interface
    isEnum -> ClassType.Enum
    else -> ClassType.Class
}

fun PsiMethod.simpleSignature() = "$name(${parameterList.parameters.joinToString(",") { it.type.presentableText }})"
fun AnalyzeMethod.simpleSignature() = "$name(${parameter.joinToString(",") { it.typeDisplay }})"

fun String.psiClassFromQualifiedName(project: Project) = JavaPsiFacade.getInstance(project)
            .findClass(this, GlobalSearchScope.allScope(project))

fun PsiMethod.toSimpleReference() = inReadAction { containingClass?.qualifiedName + "#" + simpleSignature() }

fun String.psiMethodFromSimpleReference(project: Project) = inReadAction {
    val methodSignature = substringAfter("#")

    val classOfMethod = JavaPsiFacade.getInstance(project)
            .findClass(substringBefore("#"), GlobalSearchScope.allScope(project))

    return@inReadAction classOfMethod?.methods?.firstOrNull { it.simpleSignature() == methodSignature }
}


fun PsiClass.reference() = ClassReference(this)
fun PsiMethod.id() = containingClass?.qualifiedName + "#" + simpleSignature()
fun PsiMethod.reference() = MethodReference(containingClass!!.reference(), id())
fun PsiMethod.annotationsMapped() = annotations.map { AnalyzeAnnotation(it) }
fun PsiField.id() = containingClass?.qualifiedName + "#" + name
fun PsiParameter.annotationsMapped() = annotations.map { AnalyzeAnnotation(it) }
fun PsiField.annotationsMapped() = annotations.map { AnalyzeAnnotation(it) }
fun PsiModifierList.visibility() = MethodVisibility.values().asSequence().firstOrNull { hasModifierProperty(it.value) }
        ?: MethodVisibility.PACKAGE_LOCAL
