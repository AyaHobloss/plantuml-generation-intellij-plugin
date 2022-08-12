import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.io.isFile
import com.kn.diagrams.generator.graph.ClassReference
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaMethod

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

    fun KProperty<*>.psiClass() = javaField!!.declaringClass.psiClass()
    fun KFunction<*>.psiClass() = javaMethod!!.declaringClass.psiClass()

    private fun Class<*>.psiClass() = myFixture.findClass(this.name)

    fun KFunction<*>.psiMethod(): PsiMethod {
        return psiClass().methods.first { it.name == name }
    }

    fun KProperty<*>.psiField(): PsiField {
        return psiClass().fields.first { it.name == name }
    }

    fun KClass<*>.asPsiClass(): PsiClass {
        return myFixture.findClass(this.qualifiedName!!)
    }

    fun ClassReference.asPsiClass(): PsiClass {
        return myFixture.findClass(qualifiedName())
    }
}
