@file:OptIn(ExperimentalCompilerApi::class)
package com.beespoon.apollo.ksp
import com.tschuchort.compiletesting.*
import java.io.File
import com.beespoon.apollo.ApolloRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationComponentRegistrar
internal class CompiledModule(private val compilation: KotlinCompilation, val result: JvmCompilationResult) {
    val generatedSources: List<File> get() = compilation.kspSourcesDir.walkTopDown().filter { it.extension == "kt" }.toList()
    val generatedSource: String get() = generatedSources.single().readText()
    val serviceFile: File? get() = compilation.workingDir.walkTopDown().firstOrNull { it.name == "com.beespoon.apollo.ApolloRegistrar" }
    fun registrar(): ApolloRegistrar {
        val className = serviceFile!!.readText().trim()
        return result.classLoader.loadClass(className).getDeclaredConstructor().newInstance() as ApolloRegistrar
    }
    fun loadClass(name: String): Class<*> = result.classLoader.loadClass(name)
}
internal fun compileModule(source: String, options: Map<String, String> = emptyMap()): CompiledModule {
    val compilation = KotlinCompilation().apply {
        sources = listOf(SourceFile.kotlin("Sources.kt", source))
        inheritClassPath = true
        verbose = false
        compilerPluginRegistrars = mutableListOf(SerializationComponentRegistrar())
        configureKsp(useKsp2 = true) { symbolProcessorProviders += ApolloProcessorProvider(); processorOptions.putAll(options); withCompilation = true }
    }
    return CompiledModule(compilation, compilation.compile())
}
