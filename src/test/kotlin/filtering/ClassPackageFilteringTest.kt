package filtering

import AbstractPsiContextTest
import com.kn.diagrams.generator.graph.ClassReference
import com.kn.diagrams.generator.graph.includedAndNotExcluded
import org.junit.Test
import testdata.oneComponent.service.impl.TestServiceImpl

class ClassPackageFilteringTest : AbstractPsiContextTest(){


    @Test
    fun testNoPattern(){
        assertTrue(serviceClass().includedAndNotExcluded("", "", "", ""))
    }

    @Test
    fun testIncludeWithExactMatch(){
        assertTrue(serviceClass().includedAndNotExcluded("", "", "testdata.oneComponent.service.impl", ""))
    }
    @Test
    fun testExcludeWithExactMatch(){
        assertFalse(serviceClass().includedAndNotExcluded("", "", "", "testdata.oneComponent.service.impl"))
    }

    @Test
    fun testIncludeWithMultiplePattern(){
        assertTrue(serviceClass().includedAndNotExcluded("", "", "*.service*;*oneComponent.service*;*other*;otherstuff*", ""))
    }
    @Test
    fun testExcludeWithMultiplePattern(){
        assertFalse(serviceClass().includedAndNotExcluded("", "", "", "*.service*;*oneComponent.service*;*other*;otherstuff*"))
    }

    @Test
    fun testIncludedButNotExcluded(){
        assertTrue(serviceClass().includedAndNotExcluded("", "", "*.service*", "other*;otherStuff"))
    }

    @Test
    fun testIncludedButExcluded(){
        assertFalse(serviceClass().includedAndNotExcluded("", "", "*.service*", "*.service*"))
    }

    @Test
    fun testNotIncludedButNotExcluded(){
        assertFalse(serviceClass().includedAndNotExcluded("", "", "service", "other*;otherStuff"))
    }

    @Test
    fun testNotIncludedButExcluded(){
        assertFalse(serviceClass().includedAndNotExcluded("", "", "service", "*.service*"))
    }

    private fun serviceClass() = ClassReference(TestServiceImpl::class.asPsiClass())
}
