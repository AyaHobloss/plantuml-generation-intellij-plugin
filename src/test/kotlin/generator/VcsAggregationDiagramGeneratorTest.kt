package generator

import com.kn.diagrams.generator.config.CommitFilter
import com.kn.diagrams.generator.config.EdgeAggregation
import com.kn.diagrams.generator.config.NodeColorCoding
import com.kn.diagrams.generator.config.VcsNodeAggregation
import com.kn.diagrams.generator.createIfNotExists
import org.junit.Test
import testdata.oneComponent.dataaccess.TestDataDao
import testdata.oneComponent.dto.TestDataDto
import testdata.oneComponent.entity.SubTestData
import testdata.oneComponent.entity.TestData
import testdata.oneComponent.entity.ds.TestDataDs
import testdata.oneComponent.richclient.TestFacade
import testdata.oneComponent.service.TestDataService
import java.io.File

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

        // Data Structure has one inner change so other three edges have 25%
        assertEdge("Entry Point", "Interface Structure", true, "label=\"1 / 25.0% / 25.0T%")
        assertEdge("Entry Point", "Data Structure", true, "label=\"1 / 25.0% / 25.0T%")
        assertEdge("Interface Structure", "Data Structure", true, "label=\"1 / 25.0% / 25.0T%")
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
        assertEdge("Data Structure", "Interface Structure", true, "141 / 100.0% / 100.0T%")
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

