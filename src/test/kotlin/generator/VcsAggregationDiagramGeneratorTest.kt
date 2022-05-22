package generator

import com.kn.diagrams.generator.config.CommitFilter
import com.kn.diagrams.generator.config.EdgeAggregation
import com.kn.diagrams.generator.config.NodeColorCoding
import com.kn.diagrams.generator.config.VcsNodeAggregation
import org.junit.Test
import testdata.oneComponent.dataaccess.TestDataDao
import testdata.oneComponent.dto.TestDataDto
import testdata.oneComponent.entity.SubTestData
import testdata.oneComponent.entity.TestData
import testdata.oneComponent.entity.ds.TestDataDs
import testdata.oneComponent.richclient.TestFacade

// TODO UI tests for actions and Sidebar - basic generate & regenerate
class VcsAggregationDiagramGeneratorTest : AbstractVcsDiagramGeneratorTest() {

    @Test
    fun testSingleCommitOverMultipleLayers() {
        diagram = vcsDiagram(commits()
            .commit(TestData::class, TestDataDs::class, TestDataDto::class, TestFacade::class)
        ){
            details.nodeAggregation = VcsNodeAggregation.Layer
            details.componentEdgeAggregationMethod = EdgeAggregation.CommitCount
        }

        // one edge per node and one between every node
        assertEdge("Entry Point", "Interface Structure", true, "label=\"1 / 16.7% / 16.7%oT")
        assertEdge("Entry Point", "Data Structure", true, "label=\"1 / 16.7% / 16.7%oT")
        assertEdge("Interface Structure", "Data Structure", true, "label=\"1 / 16.7% / 16.7%oT")
    }

    @Test
    fun testTwoCommitsOverMultipleLayers() {
        diagram = vcsDiagram(commits()
            .commit(TestData::class, TestDataDs::class, TestDataDto::class, TestFacade::class)
            .commit(TestDataDto::class, TestFacade::class)
        ){
            details.nodeAggregation = VcsNodeAggregation.Layer
            details.componentEdgeAggregationMethod = EdgeAggregation.CommitCount
        }

        // one edge per node and one between every node
        assertEdge("Entry Point", "Interface Structure", true, "label=\"2 / 22.2% / 22.2%oT")
        assertEdge("Entry Point", "Data Structure", true, "label=\"1 / 11.1% / 11.1%oT")
        assertEdge("Interface Structure", "Data Structure", true, "label=\"1 / 11.1% / 11.1%oT")
    }

    @Test
    fun testClassRatioWithNormalizationOverLayers() {
        diagram = vcsDiagram(commits()
            .commit(TestData::class, TestDataDs::class, TestDataDto::class,  message = "init")
            .commit(TestDataDs::class, TestDataDto::class,  message = "change")
        ){
            details.commitContainsPattern = "init"
            details.commitFilter = CommitFilter.NotMatching

            details.nodeAggregation = VcsNodeAggregation.Layer
            details.componentEdgeAggregationMethod = EdgeAggregation.ClassRatioWithCommitSize
            details.sizeNormalization = 0.5
        }

        // Data Structure has one inner change so other three edges have 25%
        assertEdge("Data Structure", "Interface Structure", true, "200 / 41.5% / 41.5%oT")
    }

    @Test
    fun testClassRatioWithNormalizationInnerChange() {
        diagram = vcsDiagram(commits()
                .commit(SubTestData::class, TestData::class, TestDataDs::class, TestDataDto::class,  message = "init")
                .commit(TestDataDs::class, TestData::class,  message = "inner change")
        ){
            details.commitContainsPattern = "init"
            details.commitFilter = CommitFilter.NotMatching

            details.nodeAggregation = VcsNodeAggregation.Layer
            details.componentEdgeAggregationMethod = EdgeAggregation.ClassRatioWithCommitSize
            details.sizeNormalization = 0.5
        }

        // Data Structure has one inner change so other three edges have 25%
        assertEdge("Data Structure", "Interface Structure", false)
    }

    @Test
    fun testTwoCommitsAreSummedUp() {
        diagram = vcsDiagram(commits()
                .commit(TestData::class, TestDataDs::class, TestDataDto::class)
                .commit(TestDataDs::class, TestDataDto::class)
        ){
            details.nodeAggregation = VcsNodeAggregation.Layer
        }

        assertEdge("Interface Structure", "Data Structure", true)
    }

    @Test
    fun testMaximumNumberOfConnections() {
        diagram = vcsDiagram(commits()
                .commit(TestData::class, TestDataDs::class, TestDataDto::class)
                .commit(TestDataDs::class, TestDataDto::class)
        ){
            details.showMaximumNumberOfEdges = 1 // only the edges with the highest weight are taken
            details.nodeAggregation = VcsNodeAggregation.Layer
        }

        assertEdge("Interface Structure", "Data Structure", true)
    }

    @Test
    fun testMaximumNumberOfConnectionsMatchingAllEdges() {
        diagram = vcsDiagram(commits()
                .commit(TestData::class, TestDataDs::class, TestDataDto::class)
                .commit(TestDataDs::class, TestDataDto::class)
        ){
            details.showMaximumNumberOfEdges = 3
            details.nodeAggregation = VcsNodeAggregation.Layer

        }

        assertEdge("Interface Structure", "Data Structure", true)
    }

    @Test
    fun testBase() {
        diagram = vcsDiagram(commits()
                .commit(TestData::class)
                .commit(TestDataDao::class, TestData::class)
                .commit(TestDataDs::class, TestFacade::class)
        ){
            details.showMaximumNumberOfEdges = 99
            details.nodeAggregation = VcsNodeAggregation.None
            details.nodeColorCoding = NodeColorCoding.Layer
            details.componentEdgeAggregationMethod = EdgeAggregation.GraphConnections
            graphRestriction.cutDataAccess = false
        }

        println(diagram)
        saveDiagram("./base.puml")
    }
    @Test
    fun testBaseAggregated() {
        diagram = vcsDiagram(commits()
                .commit(TestData::class)
                .commit(TestDataDao::class, TestData::class)
                .commit(TestDataDs::class, TestFacade::class)
        ){
            details.showMaximumNumberOfEdges = 99
            details.nodeAggregation = VcsNodeAggregation.Layer
            details.nodeColorCoding = NodeColorCoding.Layer
            details.componentEdgeAggregationMethod = EdgeAggregation.GraphConnections
            graphRestriction.cutDataAccess = false
        }

        println(diagram)
        saveDiagram("./baseAggregated.puml")
    }
}

