package grouping

import com.kn.diagrams.generator.builder.DotCluster
import com.kn.diagrams.generator.builder.DotShape
import com.kn.diagrams.generator.cast
import com.kn.diagrams.generator.generator.DotHierarchicalGroupCluster
import com.kn.diagrams.generator.generator.Grouping
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupingHierarchyTest {

    private val hierarchy = DotHierarchicalGroupCluster { i, group, color, isLast ->
        group.config.style = i.toString()
        group.config.fillColor = isLast.toString()
    }

    private val shape1 = DotShape("node1")
    private val shape2 = DotShape("node2")

    @Test
    fun testNoGrouping() {
        hierarchy.groupCluster().addNode(shape1)
        hierarchy.groupCluster().addNode(shape2)
        hierarchy.create() // connects clusters

        assertEquals(2, hierarchy.childs.size)
        assertTrue(hierarchy.childs.any { it.id() == shape1.id() })
        assertTrue(hierarchy.childs.any { it.id() == shape2.id() })
    }

    @Test
    fun testOneGroupingLevel() {
        hierarchy.groupCluster(Grouping("firstLevel")).addNode(shape1)
        hierarchy.groupCluster(Grouping("firstLevel")).addNode(shape2)
        hierarchy.create() // connects clusters

        assertEquals(1, hierarchy.childs.size)
        val groupCluster = hierarchy.groupCluster("firstLevel")

        assertEquals(2, groupCluster.childs.size)
        assertTrue(groupCluster.childs.any { it.id() == shape1.id() })
        assertTrue(groupCluster.childs.any { it.id() == shape2.id() })
    }

    @Test
    fun testNodesOnDifferentLevels() {
        val shape3 = DotShape("node3")
        val shape4 = DotShape("node4")
        val shape5 = DotShape("node5")
        val shape6 = DotShape("node6")
        val shape7 = DotShape("node7")
        val shape8 = DotShape("node8")

        hierarchy.groupCluster(null).addNode(shape1)
        hierarchy.groupCluster(Grouping("firstLevel")).addNode(shape3)
        hierarchy.groupCluster(Grouping("firstLevel")).addNode(shape4)
        hierarchy.groupCluster(Grouping("secondLevel", "firstLevel")).addNode(shape5)
        hierarchy.groupCluster(Grouping("secondLevel", "firstLevel")).addNode(shape6)
        hierarchy.groupCluster(Grouping("thirdLevel", "firstLevel")).addNode(shape7)
        hierarchy.groupCluster(Grouping("thirdLevel", "firstLevel")).addNode(shape8)
        hierarchy.create() // connects clusters

        val groupClusterLvl1 = hierarchy.groupCluster("firstLevel")
        val groupClusterLvl2 = groupClusterLvl1.groupCluster("secondLevel")
        val groupClusterLvl3 = groupClusterLvl1.groupCluster("thirdLevel")

        assertEquals(2, hierarchy.childs.size)
        assertEquals(4, groupClusterLvl1.childs.size)
        assertEquals(2, groupClusterLvl2.childs.size)
        assertEquals(2, groupClusterLvl3.childs.size)
    }

    private fun DotCluster.groupCluster(name: String? = null) = childs
            .first { it is DotCluster && (name == null || it.config.label!!.endsWith(name)) }
            .cast<DotCluster>()!!

}
