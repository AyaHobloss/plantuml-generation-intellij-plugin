package examples

import com.kn.diagrams.generator.config.NodeGrouping
import com.kn.diagrams.generator.generator.code.StructureColorCoding
import com.kn.diagrams.generator.graph.EdgeMode
import examples.tinyfullexample.data.client.DialogController
import generator.AbstractCallDiagramGeneratorTest
import org.junit.Test

class ExampleCallDiagramGeneratorTest : AbstractCallDiagramGeneratorTest() {

    @Test
    fun testGroupByComponent() {
        diagram = callDiagram(DialogController::calculate){
            details.methodColorCoding = StructureColorCoding.Layer
            details.nodeGrouping = NodeGrouping.Component
            details.edgeMode = EdgeMode.MethodsAndDirectTypeUsage
            details.showCallOrder = false
            details.showDetailedClassStructure = true
//            details.showMethodParametersTypes = true
//            details.showMethodReturnType = true

            with(projectClassification){
                includedProjects = "examples.tinyfullexample.data"

                isClientName = "*Controller*"
                isEntryPointName = "*Facade*"
                isDataAccessName = "*Repository*"
                isDataStructureName = "*Entity"
                isInterfaceStructuresName = "*Dts"
                isTestName = "*Test"
            }
            graphRestriction.cutClient = false
            graphRestriction.cutDataAccess = false
            graphRestriction.cutMappings = false
            graphRestriction.cutInterfaceStructures = false
            graphRestriction.cutDataStructures = false

            graphTraversal.hidePrivateMethods = false
            graphTraversal.hideDataStructures = false
            graphTraversal.hideMappings = false

        }
        saveDiagram(location("LayerColors_call.puml"))
    }

    override fun getTestDataDirectories() = listOf("./src/test/java/examples/tinyfullexample/data")

    private fun location(fileName: String? = null) = "./src/test/java/examples/tinyfullexample" + (fileName?.let { "/$it" } ?: "")

}
