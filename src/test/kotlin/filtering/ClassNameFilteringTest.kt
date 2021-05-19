package filtering

import AbstractPsiContextTest
import com.intellij.patterns.PsiJavaPatterns.psiClass
import com.kn.diagrams.generator.graph.AnalyzeMethod
import com.kn.diagrams.generator.graph.ClassReference
import com.kn.diagrams.generator.graph.includedAndNotExcluded
import org.junit.Test
import testdata.oneComponent.service.impl.TestServiceImpl

class ClassNameFilteringTest : AbstractPsiContextTest(){


    @Test
    fun testNoPattern(){
        assertTrue(serviceClass().includedAndNotExcluded("", "", "", ""))
    }

    @Test
    fun testIncludeWithMultiplePattern(){
        assertTrue(serviceClass().includedAndNotExcluded("TestService*;TestServiceImpl;other*;otherStuff", "", "", ""))
    }
    @Test
    fun testExcludeWithMultiplePattern(){
        assertFalse(serviceClass().includedAndNotExcluded("", "TestService*;TestServiceImpl;other*;otherStuff", "", ""))
    }

    @Test
    fun testIncludedButNotExcluded(){
        assertTrue(serviceClass().includedAndNotExcluded("TestServiceImpl", "other*;otherStuff", "", ""))
    }

    @Test
    fun testIncludedButExcluded(){
        assertFalse(serviceClass().includedAndNotExcluded("TestServiceImpl", "TestServiceImpl", "", ""))
    }

    @Test
    fun testNotIncludedButNotExcluded(){
        assertFalse(serviceClass().includedAndNotExcluded("NoTestServiceImpl", "other*;otherStuff", "", ""))
    }

    @Test
    fun testNotIncludedButExcluded(){
        assertFalse(serviceClass().includedAndNotExcluded("NoTestServiceImpl", "TestServiceImpl", "", ""))
    }

    private fun serviceClass() = ClassReference(TestServiceImpl::class.asPsiClass())
}
