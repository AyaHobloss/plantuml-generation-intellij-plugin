package generator

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.io.isFile
import com.kn.diagrams.generator.generator.CallDiagramGenerator
import com.kn.diagrams.generator.generator.StructureDiagramGenerator
import com.kn.diagrams.generator.config.CallConfiguration
import com.kn.diagrams.generator.config.CallDiagramDetails
import com.kn.diagrams.generator.config.StructureConfiguration
import com.kn.diagrams.generator.config.StructureDiagramDetails
import com.kn.diagrams.generator.graph.*
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.absoluteValue
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaMethod

abstract class AbstractCallDiagramGeneratorTest : AbstractGeneratorTest() {

    fun callDiagram(method: KFunction<*>, config: (CallConfiguration.() -> Unit)? = null): String {
        val rootMethod = method.asPsiMethod()

        val configuration = CallConfiguration(rootMethod.containingClass!!.qualifiedName!!, rootMethod.toSimpleReference(), ProjectClassification(),  GraphRestriction(), GraphTraversal(), CallDiagramDetails())

        defaultClassification(configuration.projectClassification)

        configuration.graphRestriction.cutTests = false

        with(configuration){
            graphTraversal.forwardDepth = 9
            graphTraversal.backwardDepth = 9
        }

        config?.invoke(configuration)

        return CallDiagramGenerator()
            .createUmlContent(configuration, project)
            .first().second
    }

}

abstract class AbstractStructureDiagramGeneratorTest : AbstractGeneratorTest() {

    fun classDiagram(clazz: KClass<*>, config: (StructureConfiguration.() -> Unit)? = null): String {
        val configuration = StructureConfiguration(clazz.asPsiClass().qualifiedName!!, ProjectClassification(),  GraphRestriction(), GraphTraversal(), StructureDiagramDetails())

        defaultClassification(configuration.projectClassification)

        with(configuration.graphRestriction){
            cutTests = false
            classNameExcludeFilter = "*Test"
            cutDataStructures = false
            cutMappings = false
            cutDataAccess = false
        }

        with(configuration.graphTraversal){
            forwardDepth = 9
            backwardDepth = 9
            hideInterfaceCalls = false
            hideMappings = false
            hideDataStructures = false
        }

        config?.invoke(configuration)

        return StructureDiagramGenerator()
            .createUmlContent(configuration, project)
            .first().second
    }

}

fun defaultClassification(classification: ProjectClassification){
    with(classification){
        includedProjects = "testdata"
        isDataAccessPath = "*.dataaccess"
        isDataStructurePath = "*.entity;*.ds"
        isMappingPath = "*.mapper"
        isMappingName = "*.Mapper;*.MapperImpl"
        isInterfaceStructuresPath = "*.dto"
        isEntryPointName = "*Facade*"
        isTestName = "*Test"
    }

}

abstract class AbstractGeneratorTest : LightJavaCodeInsightFixtureTestCase() {

    protected var diagram: String? = null

    override fun getTestDataPath(): String? {
        return "src/test/java"
    }

    fun assertCall(call: Pair<KFunction<*>, KFunction<*>>) {
        assertEdge(call.first.asPsiMethod().diagramId(), call.second.asPsiMethod().diagramId(), true)
    }

    fun assertNoCall(call: Pair<KFunction<*>, KFunction<*>>) {
        assertEdge(call.first.asPsiMethod().diagramId(), call.second.asPsiMethod().diagramId(), false)
    }

    private fun nodesSection() = diagram!!.substringBefore("'edges").substringAfter("diagram meta data end '/")

    private fun edgesSection() = diagram!!.substringAfter("'edges")

    private fun String.node(id: String): String? {
        return when {
            contains(" $id[") -> id+"["+substringAfter(" $id[").substringBefore("];\n")+"];"
            contains("\n$id[") -> id+"["+substringAfter("\n$id[").substringBefore("];\n")+"];"
            else -> null
        }
    }


    private fun String.edge(fromId: String, toId: String): String? {
        return when {
            contains(" $toId;") -> "$fromId -> $toId" + substringAfter("$fromId -> $toId;")
            contains(" $toId[") -> "$fromId -> $toId[" + substringAfter("$fromId -> $toId[").substringBefore("];") + "]"
            else -> null
        }
    }

    private fun String.assertRow(content: String, needsMatch: Boolean): String {
        val rows = this.substringAfter("<TR>").substringBeforeLast("</TABLE>").split("\n")
        val matchPattern = "<TD[\\s\\S]*$content[\\s\\S]*/TD>"

        val matchingRows = rows.filter { row ->
            val all = matchPattern.toRegex().findAll(row)
            val match = all.count() == 1

            match
        }

        if((matchingRows.size == 1) != needsMatch){
            val expectation = (if(needsMatch) "row " else "no row ") + " content '$content' expected"
            assertFalse("$expectation\n\n actual:\n$this", true)
        }

        return matchingRows.firstOrNull() ?: ""
    }

    fun assertClassField(field: KProperty<*>, vararg keywords: String){
        assertNode(field.psiClass().diagramId(), true)
            .assertRow(field.name+":", true)
            .let { row ->
                keywords.forEach {
                    row.assertRow(it, true)
                }
            }
    }
    fun assertNoClassField(field: KProperty<*>){
        assertNode(field.psiClass().diagramId(), true)
            .assertRow(field.name, false)
    }

    fun assertClassMethod(method: KFunction<*>, vararg keywords: String){
        assertNode(method.psiClass().diagramId(), true)
            .assertRow(method.name, true)
            .let { row ->
                keywords.forEach {
                    row.assertRow(it, true)
                }
            }
    }
    fun assertNoClassMethod(method: KFunction<*>){
        assertNode(method.psiClass().diagramId(), true)
            .assertRow(method.name, false)
    }

    fun assertClass(clazz: KClass<*>){
        assertNode(clazz.asPsiClass().diagramId(), true)
    }

    fun assertNoClass(clazz: KClass<*>){
        assertNode(clazz.asPsiClass().diagramId(), false)
    }

    fun assertMethodNode(method: KFunction<*>, vararg keywords: String){
        assertNode(method.asPsiMethod().diagramId(), true, *keywords)
    }

    fun assertNoMethodNode(method: KFunction<*>){
        assertNode(method.asPsiMethod().diagramId(), true)
    }

    fun assertNode(nodeId: String, needsMatch: Boolean, vararg keywords: String): String {
        val nodesSection = nodesSection()
        val node = nodesSection.node(nodeId)
        if((node == null) == needsMatch){
            val expectation = (if(needsMatch) "node " else "no node ") + " with id '$nodeId' expected"
            assertFalse("$expectation\n\n actual:\n$nodesSection", true)
        }

        keywords.forEach {
            assertTrue("keyword '$it' expected, actual:\n$node", node!!.contains(it))
        }

        return node ?: ""
    }

    fun assertCallEdge(fromMethod: KFunction<*>, toMethod: KFunction<*>, vararg keywords: String): String{
        return assertEdge(fromMethod.asPsiMethod().diagramId(), toMethod.asPsiMethod().diagramId(), true, *keywords)
    }

    fun assertNoCallEdge(fromMethod: KFunction<*>, toMethod: KFunction<*>): String{
        return assertEdge(fromMethod.asPsiMethod().diagramId(), toMethod.asPsiMethod().diagramId(), false)
    }

    fun assertFieldEdge(field: KProperty<*>, targetClass: KClass<*>, vararg keywords: String): String{
        val keys = keywords.toList().plus(field.name).toTypedArray()
        return assertEdge(field.psiClass().diagramId(), targetClass.asPsiClass().diagramId(), true, *keys)
    }

    fun assertNoFieldEdge(field: KProperty<*>, targetClass: KClass<*>): String{
        return assertEdge(field.psiClass().diagramId(), targetClass.asPsiClass().diagramId(), false)
    }

    fun assertClassEdge(sourceClass: KClass<*>, targetClass: KClass<*>, vararg keywords: String): String{
       return assertEdge(sourceClass.asPsiClass().diagramId(), targetClass.asPsiClass().diagramId(), true, *keywords)
    }

    fun assertNoClassEdge(sourceClass: KClass<*>, targetClass: KClass<*>): String{
        return assertEdge(sourceClass.asPsiClass().diagramId(), targetClass.asPsiClass().diagramId(), false)
    }

    private fun assertEdge(fromId: String, toId: String, needsMatch: Boolean, vararg keywords: String): String {
        val edgeSection = edgesSection()

        val edge = edgeSection.edge(fromId, toId)

        if((edge == null) == needsMatch){
            val expectation = (if(needsMatch) "edge " else "no edge ") + " '$fromId -> $toId' expected"
            assertFalse("$expectation\n\n actual:\n$edgeSection", true)
        }

        keywords.forEach {
            assertTrue("keyword '$it' expected, actual:\n$edge", edge!!.contains(it))
        }

        return edge ?: ""
    }

    private fun KProperty<*>.psiClass() = javaField!!.declaringClass.asPsiClass()
    private fun KFunction<*>.psiClass() = javaMethod!!.declaringClass.asPsiClass()
    private fun Class<*>.asPsiClass() = myFixture.findClass(this.name)

    private fun PsiMethod.diagramId() = containingClass?.diagramId()+"XXX"+name+parameterList.parameters.joinToString { it.type.presentableText }.hashCode().absoluteValue

    private fun PsiClass.diagramId() = name + qualifiedName?.substringBeforeLast(".").hashCode().absoluteValue

    private fun Class<*>.diagramId() = simpleName + name.substringBeforeLast(".").hashCode().absoluteValue

    private fun assertCall(call: Pair<KFunction<*>, KFunction<*>>, needsMatch: Boolean){
        val matchPattern = call.first.asPsiMethod().diagramId() + ".*" + call.second.asPsiMethod().diagramId()
        val match = matchPattern.toRegex().findAll(diagram!!).count() == 1
        if(match != needsMatch){
            val expectation = (if(needsMatch) "call " else "no call ") + matchPattern.replace(".*", " -> ") + " expected"
            assertFalse("$expectation\n\n actual:\n${diagram!!.substringAfter("'edges")}", true)
        }
    }

    override fun setUp() {
        super.setUp()

        Files.walk(Paths.get("./src/test/java/testdata"))
                .filter{ it.isFile() }
                .forEach { myFixture.configureByFile(it.toString().substringAfter("\\java\\")) }


        Files.walk(Paths.get("./src/test/java/javax"))
                .filter{ it.isFile() }
                .forEach { myFixture.configureByFile(it.toString().substringAfter("\\java\\")) }

        Files.walk(Paths.get("./src/test/java/org/springframework"))
                .filter { it.isFile() }
                .forEach { myFixture.configureByFile(it.toString().substringAfter("\\java\\")) }
    }

    fun KFunction<*>.asPsiMethod(): PsiMethod {
        return psiClass().methods.first { it.name == name }
    }

    fun KClass<*>.asPsiClass(): PsiClass {
        return myFixture.findClass(this.qualifiedName!!)
    }

}

fun cardinalityOptional() = cardinality("0" to "1")
fun cardinalityMandatory() = "\\[1\\]"
fun cardinalityUnbounded() = cardinality("0" to "*")
fun cardinality(range: Pair<String, String>) = "\\[${range.first}..${range.second}\\]"

fun noReturnType() = "\\)[^:]"
fun noParameters() = "\\(\\)"
fun returnType(clazz: KClass<*>) = ": "+clazz.simpleName!!
fun parameters(vararg classes: KClass<*>) = classes.joinToString(", ") { it.simpleName!! }
fun noArrowHeads() = "arrowhead=none]"
fun aggregationArrowHead() = "arrowhead=none, arrowtail=odiamond, dir=both"
fun inheritanceArrowHead() = "arrowhead=none, arrowtail=empty, dir=both"
