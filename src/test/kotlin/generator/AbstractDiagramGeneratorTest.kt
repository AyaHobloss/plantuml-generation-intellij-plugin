package generator

import AbstractPsiContextTest
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.kn.diagrams.generator.actions.ActionContext
import com.kn.diagrams.generator.actions.call
import com.kn.diagrams.generator.actions.structure
import com.kn.diagrams.generator.config.*
import com.kn.diagrams.generator.createIfNotExists
import com.kn.diagrams.generator.generator.*
import com.kn.diagrams.generator.generator.vcs.VcsCommit
import com.kn.diagrams.generator.graph.*
import java.io.File
import kotlin.math.absoluteValue
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty

abstract class AbstractVcsDiagramGeneratorTest : AbstractGeneratorTest() {

    fun vcsDiagram(commits: VcsCommits, config: (VcsConfiguration.() -> Unit)? = null): String {
        val configuration = VcsConfiguration(ProjectClassification(),  GraphRestriction(), VcsDiagramDetails())

        defaultClassification(configuration.projectClassification)

        with(configuration.graphRestriction){
            cutTests = false
            cutDataStructures = false
            cutClient = false
            cutMappings = false
            cutInterfaceStructures = false
        }

        config?.invoke(configuration)

        val actionContext = ActionContext(project, mutableListOf(), configuration, null, null)

        return createVcsContent(actionContext) { rawCommits += commits.commits }.first().second
    }

    fun commits() = VcsCommits()

    class VcsCommits{
        val commits: MutableList<VcsCommit> = mutableListOf()
        private var messageCounter = 0L

        fun commit(vararg classes: KClass<*>, commitTime: Long = messageCounter, message: String = "commit $messageCounter"): VcsCommits {

            commits += VcsCommit(commitTime, message, classes.map { ClassReference(it.qualifiedName ?: "") })

            messageCounter++

            return this
        }

    }

}

abstract class AbstractCallDiagramGeneratorTest : AbstractGeneratorTest() {

    fun callDiagram(method: KFunction<*>, config: (CallConfiguration.() -> Unit)? = null): String {
        val rootMethod = method.psiMethod()

        val configuration = CallConfiguration(
                rootMethod.containingClass!!.qualifiedName!!,
                rootMethod.id(),
                ProjectClassification(),  GraphRestriction(), GraphTraversal(), CallDiagramDetails()
        )

        defaultClassification(configuration.projectClassification)

        configuration.graphRestriction.cutTests = false

        with(configuration){
            graphTraversal.forwardDepth = 9
            graphTraversal.backwardDepth = 9
        }

        config?.invoke(configuration)

        val actionContext = ActionContext(project, mutableListOf(rootMethod.id()), configuration, null, null)

        return createCallDiagramUmlContent(actionContext.call())
            .first().second
    }

    // TODO check if each link has a node defined

}

abstract class AbstractStructureDiagramGeneratorTest : AbstractGeneratorTest() {

    fun classDiagram(clazz: KClass<*>, config: (StructureConfiguration.() -> Unit)? = null): String {
        val rootClass = clazz.asPsiClass()
        val configuration = StructureConfiguration(rootClass.qualifiedName!!, ProjectClassification(),  GraphRestriction(), GraphTraversal(), StructureDiagramDetails())

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

        val actionContext = ActionContext(project, mutableListOf(rootClass.reference().id()), configuration, null, null)

        return createStructureDiagramUmlContent(actionContext.structure())
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

abstract class AbstractGeneratorTest : AbstractPsiContextTest() {

    protected var diagram: String? = null

    fun assertCall(call: Pair<KFunction<*>, KFunction<*>>) {
        assertEdge(call.first.psiMethod().diagramId(), call.second.psiMethod().diagramId(), true)
    }

    fun assertNoCall(call: Pair<KFunction<*>, KFunction<*>>) {
        assertEdge(call.first.psiMethod().diagramId(), call.second.psiMethod().diagramId(), false)
    }

    private fun nodesSection() = diagram!!.substringBefore("'edges").substringAfter("diagram meta data end '/")

    private fun edgesSection() = diagram!!.substringAfter("'edges")

    private fun String.node(id: String): String? {
        val matcher = "[^\\S]$id\\[[\\S\\s]*];".toRegex()
        return matcher.find(this)?.value
    }

    private fun String.assertRow(content: String, needsMatch: Boolean): String {
        val matchPattern = "<TD[\\s\\S]*$content[\\s\\S]*</TD>"
        val all = matchPattern.toRegex().findAll(this)
        val match = all.count() == 1

        if(match != needsMatch){
            val expectation = (if(needsMatch) "row " else "no row ") + " content '$content' expected"
            assertFalse("$expectation\n\n actual:\n$this", true)
        }

        return all.firstOrNull()?.value ?: ""
    }

    fun assertClassField(field: KProperty<*>, vararg keywords: String){
        assertNode(field.psiClass().diagramId(), true)
            .assertRow(field.name, true)
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
        assertNode(method.psiMethod().diagramId(), true, *keywords)
    }

    fun assertNoMethodNode(method: KFunction<*>){
        assertNode(method.psiMethod().diagramId(), true)
    }

    fun assertNode(nodeId: String, needsMatch: Boolean, vararg keywords: String): String {
        val nodesSection = nodesSection()
        val node = nodesSection.node(nodeId)
        if((node == null) == needsMatch){
            val expectation = (if(needsMatch) "node " else "no node ") + " with id '$nodeId' expected"
            assertFalse("$expectation\n\n actual:\n$nodesSection", true)
        }

        keywords.forEach {
            assertTrue("keyword '$it' expected", node!!.contains(it))
        }

        return node ?: ""
    }

    fun assertCallEdge(fromMethod: KFunction<*>, toMethod: KFunction<*>, keyword: String? = null){
        assertEdge(fromMethod.psiMethod().diagramId(), toMethod.psiMethod().diagramId(), true, keyword)
    }

    fun assertNoCallEdge(fromMethod: KFunction<*>, toMethod: KFunction<*>){
        assertEdge(fromMethod.psiMethod().diagramId(), toMethod.psiMethod().diagramId(), false)
    }

    fun assertFieldEdge(field: KProperty<*>, targetClass: KClass<*>){
        assertEdge(field.psiClass().diagramId(), targetClass.asPsiClass().diagramId(), true, field.name)
    }

    fun assertNoFieldEdge(field: KProperty<*>, targetClass: KClass<*>){
        assertEdge(field.psiClass().diagramId(), targetClass.asPsiClass().diagramId(), false, field.name)
    }

    fun assertEdge(fromId: String, toId: String, needsMatch: Boolean, keyword: String? = null) {
        val edgeSection = edgesSection()

        val matchPattern = "[\"]?$fromId[\"]? -> [\"]?$toId[\"]?[\\s\\S]*${ keyword?.let { "$it[\\s\\S]*" } ?: "" };"
        val match = matchPattern.toRegex().findAll(edgeSection).count() == 1

        if(match != needsMatch){
            val expectation = (if(needsMatch) "association " else "no association ") + matchPattern + " expected"
            assertFalse("$expectation\n\n actual:\n$edgeSection", true)
        }

    }

    private fun PsiMethod.diagramId() = containingClass?.diagramId()+"XXX"+name+parameterList.parameters.joinToString { it.type.presentableText }.hashCode().absoluteValue

    private fun PsiClass.diagramId() = name + qualifiedName?.substringBeforeLast(".").hashCode().absoluteValue

    private fun Class<*>.diagramId() = simpleName + name.substringBeforeLast(".").hashCode().absoluteValue

    private fun assertCall(call: Pair<KFunction<*>, KFunction<*>>, needsMatch: Boolean){
        val matchPattern = call.first.psiMethod().diagramId() + ".*" + call.second.psiMethod().diagramId()
        val match = matchPattern.toRegex().findAll(diagram!!).count() == 1
        if(match != needsMatch){
            val expectation = (if(needsMatch) "call " else "no call ") + matchPattern.replace(".*", " -> ") + " expected"
            assertFalse("$expectation\n\n actual:\n${diagram!!.substringAfter("'edges")}", true)
        }
    }

    fun assertClassEdge(sourceClass: KClass<*>, targetClass: KClass<*>, keyword: String? = null){
        assertEdge(sourceClass.asPsiClass().diagramId(), targetClass.asPsiClass().diagramId(), true, keyword)
    }
    fun assertNoClassEdge(sourceClass: KClass<*>, targetClass: KClass<*>, keyword: String? = null){
        assertEdge(sourceClass.asPsiClass().diagramId(), targetClass.asPsiClass().diagramId(), false, keyword)
    }

    fun saveDiagram(path: String){
        val file = File(path)
        println(file.absolutePath)
        file.createIfNotExists()
        file.writeText(diagram ?: "")
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
