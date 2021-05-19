package filtering

import AbstractPsiContextTest
import com.kn.diagrams.generator.graph.AnalyzeMethod
import com.kn.diagrams.generator.graph.includedAndNotExcluded
import org.junit.Test
import testdata.oneComponent.service.impl.TestServiceImpl
import kotlin.reflect.KFunction

class MethodFilteringTest : AbstractPsiContextTest(){


    @Test
    fun testNoPattern(){
        assertTrue(loadMethod().includedAndNotExcluded("", ""))
    }


    @Test
    fun testIncludeWithMultiplePattern(){
        assertTrue(loadMethod().includedAndNotExcluded("lo*;load;other*;otherStuff", ""))
    }
    @Test
    fun testExcludeWithMultiplePattern(){
        assertFalse(loadMethod().includedAndNotExcluded("", "lo*;load;other*;otherStuff"))
    }

    @Test
    fun testIncludedButNotExcluded(){
        assertTrue(loadMethod().includedAndNotExcluded("load", "other*;otherStuff"))
    }

    @Test
    fun testIncludedButExcluded(){
        assertFalse(loadMethod().includedAndNotExcluded("load", "load"))
    }

    @Test
    fun testNotIncludedButNotExcluded(){
        assertFalse(loadMethod().includedAndNotExcluded("noload", "other*;otherStuff"))
    }

    @Test
    fun testNotIncludedButExcluded(){
        assertFalse(loadMethod().includedAndNotExcluded("noload", "load"))
    }

    private fun loadMethod() = AnalyzeMethod(TestServiceImpl::load.psiMethod())
}
