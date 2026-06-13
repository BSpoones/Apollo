package com.beespoon.apollo

import java.io.File
import java.util.ServiceLoader
import kotlin.reflect.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.JsonBuilder
import kotlinx.serialization.modules.SerializersModule
import com.beespoon.apollo.format.*
import com.beespoon.apollo.reload.ChangeDetection
import org.slf4j.LoggerFactory

public class ApolloBuilder {
    public var basePath: File? = null
    public var classLoader: ClassLoader? = null
    internal val extraClassLoaders = mutableListOf<ClassLoader>()
    public var pollingRate: Duration = 1.seconds
    public var changeDetection: ChangeDetection = ChangeDetection.MODIFICATION_TIME
    internal val registry = FormatRegistry()
    internal var jsonConfig: (JsonBuilder.() -> Unit)? = null
    internal val scanPackages = mutableListOf<String>()
    internal val registrations = mutableListOf<Registration>()
    internal val serializerModules = mutableListOf<SerializersModule>()
    internal var namespaceResolver: ((KClass<*>, ClassLoader?) -> String?)? = null
    private var discoveryRequested = false
    internal val polymorphicBases = mutableListOf<KClass<*>>()
    internal val polymorphicSubtypes = mutableListOf<Pair<KClass<*>, List<KClass<*>>>>()

    internal class Registration(
        val clazz: KClass<*>,
        val name: String = "",
        val format: String = "",
        val namespace: String = "",
        val directories: List<DirectorySpec> = emptyList()
    )

    internal class DirectorySpec(val property: KProperty1<*, *>, val name: String)

    public fun packages(vararg names: String) {
        scanPackages.addAll(names)
    }

    public fun classLoaders(vararg loaders: ClassLoader) {
        extraClassLoaders.addAll(loaders)
    }

    public fun namespace(resolver: (KClass<*>, ClassLoader?) -> String?) {
        namespaceResolver = resolver
    }

    public fun <T : Any> register(
        clazz: KClass<T>,
        name: String = "",
        format: String = "",
        namespace: String = "",
        spec: (FileConfigBuilder<T>.() -> Unit)? = null
    ) {
        val directories = FileConfigBuilder<T>().apply { spec?.invoke(this) }.directories
        registrations += Registration(clazz, name, format, namespace, directories)
    }

    public fun polymorphic(base: KClass<*>) {
        polymorphicBases += base
    }

    public fun polymorphicSubtypes(base: KClass<*>, vararg subtypes: KClass<*>) {
        polymorphicSubtypes += base to subtypes.toList()
    }

    public fun registrar(registrar: ApolloRegistrar): Unit = registrar.register(this)
    public fun discoverRegistrars() {
        discoveryRequested = true
    }

    internal fun applyDiscoveredRegistrars() {
        if (!discoveryRequested) return
        discoveryRequested = false
        val explicit = registrations.toList()
        registrations.clear()
        val logger = LoggerFactory.getLogger("Apollo")
        val loaders =
            (listOfNotNull(classLoader) + extraClassLoaders).ifEmpty { listOf(ApolloRegistrar::class.java.classLoader) }
        val seen = mutableSetOf<String>()
        for (loader in loaders) {
            for (registrar in ServiceLoader.load(ApolloRegistrar::class.java, loader)) {
                if (!seen.add(registrar.javaClass.name)) continue
                runCatching { registrar.register(this) }.onFailure {
                    logger.error(
                        "Registrar ${registrar.javaClass.name} failed: ${it.message}",
                        it
                    )
                }
            }
        }
        registrations.addAll(explicit)
    }

    public fun json(block: JsonBuilder.() -> Unit) {
        jsonConfig = block
    }

    public fun serializers(module: SerializersModule) {
        serializerModules.add(module)
    }

    public fun codec(extension: String, codec: FormatCodec): Unit = registry.register(extension, codec)
}

public class FileConfigBuilder<T : Any> internal constructor() {
    internal val directories = mutableListOf<ApolloBuilder.DirectorySpec>()
    public fun directory(property: KProperty1<T, *>, name: String = "") {
        directories += ApolloBuilder.DirectorySpec(property, name)
    }
}

public inline fun <reified T : Any> ApolloBuilder.register(
    name: String = "",
    format: String = "",
    namespace: String = "",
    noinline spec: (FileConfigBuilder<T>.() -> Unit)? = null
): Unit = register(T::class, name, format, namespace, spec)

public inline fun <reified T : Any> ApolloBuilder.polymorphic(): Unit = polymorphic(T::class)
public inline fun <reified T : Any> ApolloBuilder.polymorphicSubtypes(vararg subtypes: KClass<*>): Unit =
    polymorphicSubtypes(T::class, *subtypes)
public fun codeSourceOf(clazz: KClass<*>): String? = clazz.java.protectionDomain?.codeSource?.location?.toString()
