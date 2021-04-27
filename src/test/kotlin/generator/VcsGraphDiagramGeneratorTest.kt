package generator

import org.junit.Test
import testdata.oneComponent.dto.TestDataDto
import testdata.oneComponent.entity.TestData
import testdata.oneComponent.entity.ds.TestDataDs

class VcsGraphDiagramGeneratorTest : AbstractVcsDiagramGeneratorTest() {

    @Test
    fun testSingleCommitIsFullyConnected() {
        diagram = vcsDiagram(commits()
                .commit(TestData::class, TestDataDs::class, TestDataDto::class)
        )

        assertClassEdge(TestDataDs::class, TestData::class)
        assertClassEdge(TestDataDs::class, TestDataDto::class)
        assertClassEdge(TestDataDto::class, TestData::class)
    }

    @Test
    fun testTwoCommitsAreSummedUp() {
        diagram = vcsDiagram(commits()
                .commit(TestData::class, TestDataDs::class, TestDataDto::class)
                .commit(TestDataDs::class, TestDataDto::class)
        )

        assertClassEdge(TestDataDs::class, TestData::class, "label=1")
        assertClassEdge(TestDataDs::class, TestDataDto::class, "label=2")
        assertClassEdge(TestDataDto::class, TestData::class, "label=1")
    }

    @Test
    fun testMaximumNumberOfConnections() {
        diagram = vcsDiagram(commits()
                .commit(TestData::class, TestDataDs::class, TestDataDto::class)
                .commit(TestDataDs::class, TestDataDto::class)
        ){
            details.showMaximumNumberOfEdges = 1 // only the edges with the highest weight are taken
        }

        assertClassEdge(TestDataDs::class, TestDataDto::class, "label=2")
        assertNoClassEdge(TestDataDs::class, TestData::class, "label=1")
        assertNoClassEdge(TestDataDto::class, TestData::class, "label=1")
    }

    @Test
    fun testMaximumNumberOfConnectionsMatchingAllEdges() {
        diagram = vcsDiagram(commits()
                .commit(TestData::class, TestDataDs::class, TestDataDto::class)
                .commit(TestDataDs::class, TestDataDto::class)
        ){
            details.showMaximumNumberOfEdges = 3
        }

        assertClassEdge(TestDataDs::class, TestDataDto::class, "label=2")
        assertClassEdge(TestDataDs::class, TestData::class, "label=1")
        assertClassEdge(TestDataDto::class, TestData::class, "label=1")
    }

}

