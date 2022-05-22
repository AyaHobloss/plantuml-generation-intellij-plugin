package filtering

import com.kn.diagrams.generator.graph.excluded
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExcludeFilteringTest {

    @Test
    fun testNoPattern(){
        assertFalse("TestService".excluded(""))
    }

    @Test
    fun testExactPattern(){
        assertTrue("TestService".excluded("TestService"))
    }

    @Test
    fun testExactWithWildcardPattern(){
        assertTrue("TestService".excluded("TestService*"))
    }

    @Test
    fun testWildcardPattern(){
        assertTrue("TestService".excluded("*Service*"))
    }

    @Test
    fun testMultiplePatternWithPartialIncluded(){
        assertTrue("TestService".excluded("*Service*;*Test*;OtherStuff;Other*"))
    }

    @Test
    fun testNotExcluded(){
        assertFalse("TestService".excluded("OtherStuff;Other*"))
    }

}
