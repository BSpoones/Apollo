@file:OptIn(ExperimentalCompilerApi::class)
package com.beespoon.apollo.ksp
import com.tschuchort.compiletesting.KotlinCompilation
import java.io.File
import kotlin.test.*
import com.beespoon.apollo.Apollo
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
class ApolloProcessorTest {
    @AfterEach fun tearDown() = Apollo.reset()
    @Test fun generatedRegistrarRegistersScanFree(@TempDir dir: File) {
        val module = compileModule("""
            package test

            import kotlinx.serialization.Serializable
            import com.beespoon.apollo.annotation.FileConfig

            @FileConfig @Serializable
            data class HuntsConfig(val updateSecs: Long = 60)

            @FileConfig("custom-name") @Serializable
            internal data class TuckedAway(val x: Int = 1)
            """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, module.result.exitCode, module.result.messages)
        val registrarName = module.serviceFile!!.readText().trim()
        assertTrue(registrarName.startsWith("com.beespoon.apollo.generated.ApolloRegistrar_"), registrarName)
        Apollo.build { basePath = dir; registrar(module.registrar()) }
        assertTrue(File(dir, "hunts.json").exists())
        assertTrue(File(dir, "custom-name.json").exists())
        assertTrue(File(dir, "hunts.json").readText().contains("update-secs"))
    }
    @Test fun namespaceFromMetadataAnnotationNestsConfigs(@TempDir dir: File) {
        val module = compileModule("""
            package test

            import kotlinx.serialization.Serializable
            import com.beespoon.apollo.annotation.FileConfig

            annotation class PluginMetadata(val id: String)

            @PluginMetadata("safari")
            object SafariPlugin

            @FileConfig @Serializable
            data class ZonesConfig(val radius: Int = 5)
            """.trimIndent(), options = mapOf("apollo.namespaceAnnotation" to "test.PluginMetadata", "apollo.defaultNamespace" to "fallback"))
        assertEquals(KotlinCompilation.ExitCode.OK, module.result.exitCode, module.result.messages)
        assertTrue(module.generatedSource.contains("namespace = \"safari\""))
        Apollo.build { basePath = dir; registrar(module.registrar()) }
        assertTrue(File(dir, "safari/zones.json").exists())
    }
    @Test fun defaultNamespaceAppliesWhenNoMetadataResolves() {
        val module = compileModule("""
            package test

            import kotlinx.serialization.Serializable
            import com.beespoon.apollo.annotation.FileConfig

            @FileConfig @Serializable
            data class ZonesConfig(val radius: Int = 5)
            """.trimIndent(), options = mapOf("apollo.namespaceAnnotation" to "test.PluginMetadata", "apollo.defaultNamespace" to "fallback"))
        assertEquals(KotlinCompilation.ExitCode.OK, module.result.exitCode, module.result.messages)
        assertTrue(module.generatedSource.contains("namespace = \"fallback\""))
    }
    @Test fun literalNamespaceOptionWinsOverAnnotation() {
        val module = compileModule("""
            package test

            import kotlinx.serialization.Serializable
            import com.beespoon.apollo.annotation.FileConfig

            annotation class PluginMetadata(val id: String)

            @PluginMetadata("safari")
            object SafariPlugin

            @FileConfig @Serializable
            data class ZonesConfig(val radius: Int = 5)
            """.trimIndent(), options = mapOf("apollo.namespaceAnnotation" to "test.PluginMetadata", "apollo.namespace" to "forced"))
        assertEquals(KotlinCompilation.ExitCode.OK, module.result.exitCode, module.result.messages)
        assertTrue(module.generatedSource.contains("namespace = \"forced\""))
    }
    @Test fun requireNamespaceFailsCompilationWhenUnresolved() {
        val module = compileModule("""
            package test

            import kotlinx.serialization.Serializable
            import com.beespoon.apollo.annotation.FileConfig

            @FileConfig @Serializable
            data class ZonesConfig(val radius: Int = 5)
            """.trimIndent(), options = mapOf("apollo.namespaceAnnotation" to "test.PluginMetadata", "apollo.requireNamespace" to "true"))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, module.result.exitCode)
        assertTrue(module.result.messages.contains("apollo.requireNamespace is set but no namespace resolved"))
    }
    @Test fun requireNamespaceIgnoresConfiglessModules() {
        val module = compileModule("""
            package test

            import kotlinx.serialization.Serializable

            @Serializable
            data class JustAModel(val x: Int = 1)
            """.trimIndent(), options = mapOf("apollo.requireNamespace" to "true"))
        assertEquals(KotlinCompilation.ExitCode.OK, module.result.exitCode, module.result.messages)
        assertTrue(module.generatedSources.isEmpty())
    }
    @Test fun ambiguousNamespaceFailsCompilation() {
        val module = compileModule("""
            package test

            import kotlinx.serialization.Serializable
            import com.beespoon.apollo.annotation.FileConfig

            annotation class PluginMetadata(val id: String)

            @PluginMetadata("one") object PluginOne
            @PluginMetadata("two") object PluginTwo

            @FileConfig @Serializable
            data class ZonesConfig(val radius: Int = 5)
            """.trimIndent(), options = mapOf("apollo.namespaceAnnotation" to "test.PluginMetadata"))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, module.result.exitCode)
        assertTrue(module.result.messages.contains("ambiguous namespace"))
    }
    @Test fun metadataWithoutNamespaceParameterFailsCompilation() {
        val module = compileModule("""
            package test

            import kotlinx.serialization.Serializable
            import com.beespoon.apollo.annotation.FileConfig

            annotation class PluginMetadata(val title: String)

            @PluginMetadata("safari")
            object SafariPlugin

            @FileConfig @Serializable
            data class ZonesConfig(val radius: Int = 5)
            """.trimIndent(), options = mapOf("apollo.namespaceAnnotation" to "test.PluginMetadata"))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, module.result.exitCode)
        assertTrue(module.result.messages.contains("carries no string parameter 'id'"))
    }
    @Test fun directoryPropertySplitsIntoElementFiles(@TempDir dir: File) {
        val module = compileModule("""
            package test

            import kotlinx.serialization.Serializable
            import com.beespoon.apollo.annotation.DirectoryConfig
            import com.beespoon.apollo.annotation.FileConfig

            @Serializable
            data class Entry(val price: Int = 10)

            @FileConfig @Serializable
            data class ShopConfig(val title: String = "shop", @DirectoryConfig(name = "stock") val entries: List<Entry> = listOf(Entry()))
            """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, module.result.exitCode, module.result.messages)
        Apollo.build { basePath = dir; registrar(module.registrar()) }
        assertTrue(File(dir, "shop/shop.json").exists())
        assertTrue(File(dir, "shop/stock/000000.json").exists())
    }
    @Test fun invalidConfigsFailCompilationWithEachReason() {
        val module = compileModule("""
            package test

            import kotlinx.serialization.Serializable
            import com.beespoon.apollo.annotation.DirectoryConfig
            import com.beespoon.apollo.annotation.FileConfig

            @FileConfig @Serializable
            data class Generic<T>(val x: Int = 1)

            @FileConfig
            data class NotSerializable(val x: Int = 1)

            @FileConfig @Serializable
            private data class Hidden(val x: Int = 1)

            @FileConfig @Serializable
            object ObjectConfig

            @FileConfig @Serializable
            abstract class AbstractConfig

            @FileConfig @Serializable
            sealed class SealedConfig

            @Serializable
            data class Item(val id: String = "i")

            @FileConfig @Serializable
            data class BadDir(@DirectoryConfig val s: String = "x")

            @FileConfig @Serializable
            data class BadMapKey(@DirectoryConfig val byId: Map<Int, Item> = emptyMap())

            @FileConfig @Serializable
            data class PrivateProp(@DirectoryConfig private val items: List<Item> = emptyList())
            """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, module.result.exitCode)
        val messages = module.result.messages
        assertTrue(messages.contains("test.Generic is generic"))
        assertTrue(messages.contains("test.NotSerializable must be @Serializable"))
        assertTrue(messages.contains("test.Hidden must be public or internal"))
        assertTrue(messages.contains("must target a top-level or nested class"))
        assertTrue(messages.contains("test.AbstractConfig must be a concrete class"))
        assertTrue(messages.contains("test.SealedConfig must be a concrete class"))
        assertTrue(messages.contains("must be a Collection<*> or Map<String, *>"))
        assertTrue(messages.contains("must be a Map with String keys"))
        assertTrue(messages.contains("directory property 'items'"))
        assertTrue(module.generatedSources.isEmpty())
    }
    @Test fun moduleWithNothingToRegisterGeneratesNoFiles() {
        val module = compileModule("""
            package test

            import kotlinx.serialization.Serializable

            @Serializable
            data class JustAModel(val x: Int = 1)
            """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, module.result.exitCode, module.result.messages)
        assertTrue(module.generatedSources.isEmpty())
        assertNull(module.serviceFile)
    }
    @Test fun polymorphicHierarchyRegistersScanFree(@TempDir dir: File) {
        val module = compileModule("""
            package test

            import kotlinx.serialization.Polymorphic
            import kotlinx.serialization.SerialName
            import kotlinx.serialization.Serializable
            import com.beespoon.apollo.annotation.FileConfig

            @Polymorphic
            interface Reward

            @Serializable @SerialName("xp")
            data class XpReward(val xp: Int = 5) : Reward

            @Serializable
            abstract class AbstractReward : Reward

            class BrokenReward : Reward

            @Serializable
            private data class SecretReward(val s: Int = 1) : Reward

            @Polymorphic
            private interface HiddenBase

            @FileConfig @Serializable
            data class RewardsConfig(val first: Reward = XpReward())
            """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.OK, module.result.exitCode, module.result.messages)
        assertTrue(module.generatedSource.contains("test.XpReward::class"))
        assertFalse(module.generatedSource.contains("BrokenReward"))
        assertFalse(module.generatedSource.contains("SecretReward"))
        assertFalse(module.generatedSource.contains("AbstractReward"))
        assertFalse(module.generatedSource.contains("HiddenBase"))
        assertTrue(module.result.messages.contains("BrokenReward has no @Serializable serialiser"))
        assertTrue(module.result.messages.contains("SecretReward isn't visible to generated code"))
        assertTrue(module.result.messages.contains("HiddenBase isn't visible to generated code"))
        Apollo.build { basePath = dir; registrar(module.registrar()) }
        assertTrue(File(dir, "rewards.json").readText().contains("\"xp\""))
    }
}
