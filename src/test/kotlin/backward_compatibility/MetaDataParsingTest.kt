package backward_compatibility

import com.kn.diagrams.generator.config.DiagramConfiguration
import com.kn.diagrams.generator.config.loadFromMetadata
import AbstractPsiContextTest
import org.junit.Test
import java.io.File
import kotlin.test.assertNotNull

class MetaDataParsingTest : AbstractPsiContextTest(){

    @Test
    fun testReadAllMetaDataFilesFromOldVersions(){
        versionDirectories().forEach { (version, puml) ->
            println("testing $version - ${puml.name}")
            val config = DiagramConfiguration.loadFromMetadata(puml.readText())
            assertNotNull(config, "incompatible change for $version in diagram file: ${puml.name}")
        }
    }

    private fun versionDirectories() = File("src/test/kotlin/backward_compatibility").listFiles()!!
        .filter { it.isDirectory }
        .flatMap { it.listFiles()!!.map { puml -> it.name to puml } }

    override fun getTestDataDirectories() = listOf("./src/test/java/examples")

}
