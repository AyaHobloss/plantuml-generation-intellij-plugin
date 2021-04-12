import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.io.isFile
import java.nio.file.Files
import java.nio.file.Paths

abstract class AbstractPsiContextTest : LightJavaCodeInsightFixtureTestCase(){

    override fun getTestDataPath(): String? {
        return "src/test/java"
    }

    open fun getTestDataDirectories(): List<String> = listOf(
        "./src/test/java/testdata",
        "./src/test/java/javax",
        "./src/test/java/org/springframework"
    )

    override fun setUp() {
        super.setUp()

        getTestDataDirectories().forEach { baseDirectory ->
            Files.walk(Paths.get(baseDirectory))
                .filter{ it.isFile() }
                .forEach { myFixture.configureByFile(it.toString().substringAfter("\\java\\")) }
        }
    }
}
