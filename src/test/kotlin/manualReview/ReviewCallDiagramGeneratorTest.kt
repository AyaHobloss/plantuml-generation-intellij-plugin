package manualReview

import com.kn.diagrams.generator.config.NodeAggregation
import com.kn.diagrams.generator.config.NodeGrouping
import generator.AbstractCallDiagramGeneratorTest
import org.junit.Test
import testdata.oneComponent.richclient.impl.TestFacadeImpl

class ReviewCallDiagramGeneratorTest : AbstractCallDiagramGeneratorTest() {

    @Test
    fun testGroupByComponent() {
        diagram = callDiagram(TestFacadeImpl::load){
            details.nodeGrouping = NodeGrouping.Component
        }
        saveDiagram(location("groupByComponent.puml"))
    }

    @Test
    fun testGroupByLayer() {
        diagram = callDiagram(TestFacadeImpl::load){
            details.nodeGrouping = NodeGrouping.Layer
        }
        saveDiagram(location("groupByLayer.puml"))
    }

    @Test
    fun testGroupByNothing() {
        diagram = callDiagram(TestFacadeImpl::load){
            details.nodeGrouping = NodeGrouping.None
        }
        saveDiagram(location("groupByNothing.puml"))
    }

    @Test
    fun testNoGroupNoClassBox() {
        diagram = callDiagram(TestFacadeImpl::load){
            details.nodeGrouping = NodeGrouping.None
            details.wrapMethodsWithItsClass = false
        }

        saveDiagram(location("noGroupNoClassBox.puml"))
    }

    @Test
    fun testLayerGroupNoClassBox() {
        diagram = callDiagram(TestFacadeImpl::load){
            details.nodeGrouping = NodeGrouping.Layer
            details.wrapMethodsWithItsClass = false
        }

        saveDiagram(location("layerGroupNoClassBox.puml"))
    }

    @Test
    fun testClassAggregationByLayer() {
        diagram = callDiagram(TestFacadeImpl::load){
            details.nodeGrouping = NodeGrouping.Layer
            details.nodeAggregation = NodeAggregation.Class
            details.wrapMethodsWithItsClass = false
        }

        saveDiagram(location("classAggregationByLayer.puml"))
    }

    @Test
    fun testClassAggregationByComponent() {
        diagram = callDiagram(TestFacadeImpl::load){
            details.nodeGrouping = NodeGrouping.Component
            details.nodeAggregation = NodeAggregation.Class
            details.wrapMethodsWithItsClass = false
        }

        saveDiagram(location("classAggregationByComponent.puml"))
    }

    private fun location(fileName: String? = null) = "./manualReview/call" + (fileName?.let { "/$it" } ?: "")

}
