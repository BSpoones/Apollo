package com.beespoon.apollo
import java.io.File
import kotlin.test.*
import com.beespoon.apollo.testdata.*
import com.beespoon.namespaced.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
class BuilderRegistrationTest {
    @AfterEach fun tearDown() = Apollo.reset()
    @Test fun explicitNameOverridesDerivedName(@TempDir dir: File) {
        val apollo = Apollo.build { basePath = dir; register<PlainConfig>(name = "renamed") }
        assertTrue(File(dir, "renamed.json").exists())
        assertEquals("bar", apollo.config<PlainConfig>().value.foo)
    }
    @Test fun programmaticDirectorySplit(@TempDir dir: File) {
        val apollo = Apollo.build { basePath = dir; register<PlainDirConfig> { directory(PlainDirConfig::rewards) } }
        assertTrue(File(dir, "plaindir/plaindir.json").exists())
        assertTrue(File(dir, "plaindir/rewards/000000.json").exists())
        assertEquals("coin", apollo.config<PlainDirConfig>().value.rewards.single().id)
    }
    @Test fun programmaticDirectoryWithCustomNames(@TempDir dir: File) {
        Apollo.build { basePath = dir; register<PlainDirConfig>(name = "boxed") { directory(PlainDirConfig::rewards, name = "loot") } }
        assertTrue(File(dir, "boxed/boxed.json").exists())
        assertTrue(File(dir, "boxed/loot/000000.json").exists())
    }
    @Test fun programmaticDirectoryOverridesAnnotation(@TempDir dir: File) {
        Apollo.build { basePath = dir; register<ListDirectoryConfig> { directory(ListDirectoryConfig::rewards, name = "explicit") } }
        assertTrue(File(dir, "listdirectory/explicit/000000.json").exists())
        assertFalse(File(dir, "listdirectory/rewards").exists())
    }
    @Test fun explicitRegistrationOverridesScannedSpec(@TempDir dir: File) {
        Apollo.build { basePath = dir; classLoader = ScannableConfig::class.java.classLoader; packages("com.beespoon.apollo"); register<ScannableConfig>(name = "renamed-scan") }
        assertTrue(File(dir, "renamed-scan.json").exists())
        assertFalse(File(dir, "scannable.json").exists())
    }
    @Test fun polymorphicBaseRegisteredProgrammatically(@TempDir dir: File) {
        val apollo = Apollo.build { basePath = dir; classLoaders(PlainShapeConfig::class.java.classLoader); packages("com.beespoon.namespaced"); polymorphic<PlainShape>(); register<PlainShapeConfig>() }
        assertEquals(PDot(), apollo.config<PlainShapeConfig>().value.shape)
    }
}
