package filtering

import com.kn.diagrams.generator.graph.included
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IncludeFilteringTest {

    @Test
    fun testNoPattern(){
        assertTrue("TestService".included(""))
    }

    @Test
    fun testExactPattern(){
        assertTrue("TestService".included("TestService"))
    }

    @Test
    fun testExactWithWildcardPattern(){
        assertTrue("TestService".included("TestService*"))
    }

    @Test
    fun testWildcardPattern(){
        assertTrue("TestService".included("*Service*"))
    }

    @Test
    fun testMultiplePatternWithPartialIncluded(){
        assertTrue("TestService".included("*Service*;*Test*;OtherStuff;Other*"))
    }

    @Test
    fun testNotIncluded(){
        assertFalse("TestService".included("OtherStuff;Other*"))
    }

}
