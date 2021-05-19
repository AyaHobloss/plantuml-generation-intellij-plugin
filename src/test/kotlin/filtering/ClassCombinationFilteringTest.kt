package filtering

import AbstractPsiContextTest
import com.kn.diagrams.generator.graph.ClassReference
import com.kn.diagrams.generator.graph.includedAndNotExcluded
import org.junit.Test
import testdata.oneComponent.service.impl.TestServiceImpl

class ClassCombinationFilteringTest : AbstractPsiContextTest(){

    @Test
    fun testOnlyIncluded(){
        assertTrue(serviceClass().includedAndNotExcluded(
                nameIncludedPattern = "*Service*",
                nameExcludedPattern = "",
                pathIncludedPattern = "*.service*",
                pathExcludedPattern = ""
        ))
    }

    @Test
    fun testOnlyExcluded(){
        assertFalse(serviceClass().includedAndNotExcluded(
                nameIncludedPattern = "",
                nameExcludedPattern = "*Service*",
                pathIncludedPattern = "",
                pathExcludedPattern = "*.service*"
        ))
    }

    @Test
    fun testIncludedButExcluded(){
        assertFalse(serviceClass().includedAndNotExcluded(
                nameIncludedPattern = "*Service*",
                nameExcludedPattern = "*Service*",
                pathIncludedPattern = "*.service*",
                pathExcludedPattern = "*.service*"
        ))
    }
    @Test
    fun testIncludedButNotExcluded(){
        assertTrue(serviceClass().includedAndNotExcluded(
                nameIncludedPattern = "*Service*",
                nameExcludedPattern = "Service",
                pathIncludedPattern = "*.service*",
                pathExcludedPattern = "service"
        ))
    }

    @Test
    fun testNotIncludedButExcluded(){
        assertFalse(serviceClass().includedAndNotExcluded(
                nameIncludedPattern = "otherStuff",
                nameExcludedPattern = "*Service*",
                pathIncludedPattern = "otherStuff",
                pathExcludedPattern = "*.service*"
        ))
    }
    @Test
    fun testNotIncludedButNotExcluded(){
        assertFalse(serviceClass().includedAndNotExcluded(
                nameIncludedPattern = "otherStuff",
                nameExcludedPattern = "Service",
                pathIncludedPattern = "otherStuff",
                pathExcludedPattern = "service"
        ))
    }

    @Test
    fun testNotIncludedButNoExcludePattern(){
        assertFalse(serviceClass().includedAndNotExcluded(
                nameIncludedPattern = "otherStuff",
                nameExcludedPattern = "",
                pathIncludedPattern = "otherStuff",
                pathExcludedPattern = ""
        ))
    }


    private fun serviceClass() = ClassReference(TestServiceImpl::class.asPsiClass())
}
