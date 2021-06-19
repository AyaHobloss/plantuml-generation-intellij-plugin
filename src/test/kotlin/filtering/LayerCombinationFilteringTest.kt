package filtering

import AbstractPsiContextTest
import com.kn.diagrams.generator.graph.*
import org.junit.Test
import testdata.oneComponent.service.impl.TestServiceImpl

class LayerCombinationFilteringTest : AbstractPsiContextTest(){


    private fun classification(): ProjectClassification {
        return ProjectClassification(customLayers = mapOf("Service" to LayerDefinition("*Service*", "")))
    }

    @Test
    fun testOnlyIncluded(){
        assertTrue(serviceClass().layerIncludedAndNotExcluded(classification(), listOf("Service"), listOf()))
    }


    @Test
    fun testOnlyExcluded(){
        assertTrue(serviceClass().layerIncludedAndNotExcluded(classification(), listOf(), listOf("Service")))
    }

    @Test
    fun testIncludedButExcluded(){
        assertFalse(serviceClass().layerIncludedAndNotExcluded(classification(), listOf("Service"), listOf("Service")))
    }

    @Test
    fun testIncludedButNotExcluded(){
        assertTrue(serviceClass().layerIncludedAndNotExcluded(classification(), listOf("Service"), listOf("NoService")))
    }

    @Test
    fun testNotIncludedButExcluded(){
        assertFalse(serviceClass().layerIncludedAndNotExcluded(classification(), listOf("NoService"), listOf("Service")))
    }
    @Test
    fun testNotIncludedButNotExcluded(){
        assertFalse(serviceClass().layerIncludedAndNotExcluded(classification(), listOf("NoService"), listOf("NoService")))
    }

    @Test
    fun testNotIncludedButNoExcludePattern(){
        assertFalse(serviceClass().layerIncludedAndNotExcluded(classification(), listOf("NoService"), listOf()))
    }

    private fun serviceClass() = ClassReference(TestServiceImpl::class.asPsiClass())
}
