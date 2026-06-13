package com.beespoon.apollo
import java.io.File
import kotlin.test.*
import com.beespoon.apollo.testdata.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
class NamespaceTest {
    @AfterEach fun tearDown() = Apollo.reset()
    @Test fun resolverNestsConfigUnderSegment(@TempDir dir: File) {
        val apollo = Apollo.build { basePath = dir; namespace { _, _ -> "myplugin" }; register<SimpleConfig>() }
        assertTrue(File(dir, "myplugin/simple.json").exists())
        assertEquals("default", apollo.config<SimpleConfig>().value.name)
    }
    @Test fun resolverNestsDirectoryConfigUnderSegment(@TempDir dir: File) {
        Apollo.build { basePath = dir; namespace { _, _ -> "myplugin" }; register<ListDirectoryConfig>() }
        assertTrue(File(dir, "myplugin/listdirectory/listdirectory.json").exists())
    }
    @Test fun resolverPerClassRouting(@TempDir dir: File) {
        Apollo.build { basePath = dir; namespace { clazz, _ -> if (clazz == SimpleConfig::class) "a" else "b" }; register<SimpleConfig>(); register<Standalone>() }
        assertTrue(File(dir, "a/simple.json").exists()); assertTrue(File(dir, "b/standalone.json").exists())
    }
    @Test fun resolverReceivesTheClassLoader(@TempDir dir: File) {
        Apollo.build { basePath = dir; namespace { _, loader -> if (loader === SimpleConfig::class.java.classLoader) "by-loader" else null }; register<SimpleConfig>() }
        assertTrue(File(dir, "by-loader/simple.json").exists())
    }
    @Test fun resolverNullFallsBackToBasePath(@TempDir dir: File) {
        Apollo.build { basePath = dir; namespace { _, _ -> null }; register<SimpleConfig>() }
        assertTrue(File(dir, "simple.json").exists())
    }
    @Test fun resolverEmptySegmentFallsBackToBasePath(@TempDir dir: File) {
        Apollo.build { basePath = dir; namespace { _, _ -> "" }; register<SimpleConfig>() }
        assertTrue(File(dir, "simple.json").exists())
    }
    @Test fun explicitNamespaceParamNestsConfig(@TempDir dir: File) {
        Apollo.build { basePath = dir; register<SimpleConfig>(namespace = "my-plugin") }
        assertTrue(File(dir, "my-plugin/simple.json").exists())
    }
    @Test fun explicitNamespaceParamWinsOverResolver(@TempDir dir: File) {
        Apollo.build { basePath = dir; namespace { _, _ -> "resolved" }; register<SimpleConfig>(namespace = "explicit") }
        assertTrue(File(dir, "explicit/simple.json").exists()); assertFalse(File(dir, "resolved").exists())
    }
    @Test fun codeSourceOfKeyedResolverRoutesByJar(@TempDir dir: File) {
        val source = codeSourceOf(SimpleConfig::class)
        assertTrue(source != null)
        Apollo.build { basePath = dir; namespace { clazz, _ -> if (codeSourceOf(clazz) == source) "from-jar" else null }; register<SimpleConfig>() }
        assertTrue(File(dir, "from-jar/simple.json").exists())
    }
}
