package com.beespoon.apollo

import io.github.classgraph.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.*
import kotlin.reflect.full.starProjectedType
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*
import com.beespoon.apollo.annotation.FileConfig
import com.beespoon.apollo.api.ConfigReference
import com.beespoon.apollo.format.JsonFormatCodec
import com.beespoon.apollo.reference.*
import com.beespoon.apollo.reload.PollingScheduler
import com.beespoon.apollo.serialization.PolymorphicModule
import org.slf4j.*

public class Apollo internal constructor(builder: ApolloBuilder) : AutoCloseable {
    internal val logger: Logger = LoggerFactory.getLogger("Apollo")
    private val basePath: File = builder.basePath ?: error("Apollo: basePath must be set")
    private val classLoaders = listOfNotNull(builder.classLoader) + builder.extraClassLoaders
    private val scanPackages = builder.scanPackages.toList()
    private val pollingRateMs = builder.pollingRate.inWholeMilliseconds
    private val changeDetection = builder.changeDetection
    private val serializerModules = builder.serializerModules.toList()
    private val namespaceResolver = builder.namespaceResolver
    private val polymorphicBases = builder.polymorphicBases.toList()
    private val polymorphicSubtypes = builder.polymorphicSubtypes.toList()
    private val registry = builder.registry
    private val configs: ConcurrentHashMap<KType, ConfigReference<*>>
    private val errored = ConcurrentHashMap.newKeySet<KType>()
    @Volatile
    private var scheduler: PollingScheduler? = null
    private val json: Json

    init {
        val scan = scanClasspath()
        try {
            json = buildJson(builder.jsonConfig, buildSerializersModule(scan))
            registry.register(JSON_FORMAT, JsonFormatCodec(json))
            configs = registerConfigs(scan)
        } finally {
            scan?.close()
        }
    }

    public companion object {
        internal const val JSON_FORMAT: String = "json"

        internal object State {
            val built: MutableList<Apollo> = mutableListOf();
            val lock: Any = Any()
        }

        public val instance: Apollo
            get() = synchronized(State.lock) { State.built.lastOrNull() }
                ?: error("Apollo not initialised. Call Apollo.build { ... } first.")

        public fun build(block: ApolloBuilder.() -> Unit): Apollo = synchronized(State.lock) {
            val builder = ApolloBuilder().apply(block)
            builder.applyDiscoveredRegistrars()
            Apollo(builder).also { State.built.add(it); it.start(builder.registrations) }
        }

        public fun reset(): Unit = synchronized(State.lock) {
            State.built.forEach { it.close() }
            State.built.clear()
            LazyConfigReference.unbindAll()
        }
    }

    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> config(clazz: KClass<T>): ConfigReference<T> =
        configs[clazz.starProjectedType] as ConfigReference<T>?
            ?: error("No config registered for ${clazz.qualifiedName} on this Apollo instance. " + "Annotate it with @FileConfig in a scanned package, or register it.")

    public fun register(clazz: KClass<*>) {
        registerClass(clazz, configs); pollTracked(clazz.starProjectedType, "Initial load failed")
    }

    public override fun close() {
        scheduler?.close(); scheduler = null
    }

    private fun buildSerializersModule(scan: ScanResult?): SerializersModule = serializerModules.fold(
        PolymorphicModule.discover(
            scan,
            logger,
            polymorphicBases,
            polymorphicSubtypes
        )
    ) { acc, module -> acc + module }

    private fun buildJson(config: (JsonBuilder.() -> Unit)?, module: SerializersModule): Json = Json {
        serializersModule = module; ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults =
        true; namingStrategy = JsonNamingStrategy.KebabCase; config?.invoke(this)
    }

    private fun scanClasspath(): ScanResult? {
        if (scanPackages.isEmpty()) return null
        return ClassGraph().enableClassInfo().enableAnnotationInfo().acceptPackages(*scanPackages.toTypedArray())
            .also { graph -> if (classLoaders.isNotEmpty()) graph.overrideClassLoaders(*classLoaders.toTypedArray()) }
            .scan()
    }

    private fun registerConfigs(scan: ScanResult?): ConcurrentHashMap<KType, ConfigReference<*>> {
        val registered = ConcurrentHashMap<KType, ConfigReference<*>>()
        logger.info("Initialising — base path: ${basePath.absolutePath}")
        scan?.getClassesWithAnnotation(FileConfig::class.java.name)?.forEach { classInfo ->
            runCatching {
                registerClass(
                    classInfo.loadClass().kotlin,
                    registered
                )
            }.onFailure { logger.error("Failed to register config ${classInfo.name}: ${it.message}", it) }
        }
        logger.info("Initialised: ${registered.size} configs registered")
        return registered
    }

    private fun registerClass(
        clazz: KClass<*>,
        target: ConcurrentHashMap<KType, ConfigReference<*>>,
        registration: ApolloBuilder.Registration? = null
    ) {
        val type = clazz.starProjectedType
        val strategy = json.configuration.namingStrategy
        val fileConfig = clazz.annotations.filterIsInstance<FileConfig>().firstOrNull()
        val rawName = registration?.name?.ifEmpty { null } ?: fileConfig?.name
        val format = registration?.format?.ifEmpty { null } ?: fileConfig?.format?.ifEmpty { null } ?: JSON_FORMAT
        val declared = registration?.directories.orEmpty().map {
            DirectoryPropertyDescriptor.declare(
                it.property,
                clazz,
                it.name,
                strategy
            )
        }
        val discovered = DirectoryPropertyDescriptor.discover(clazz, strategy)
            .filter { found -> declared.none { it.propertyName == found.propertyName } }
        val directoryProps = declared + discovered
        val engine = FileConfigReference<Any>(
            file = configFile(
                clazz,
                rawName,
                format,
                nested = directoryProps.isNotEmpty(),
                explicitNamespace = registration?.namespace
            ),
            type = type,
            directoryProps = directoryProps,
            json = json,
            codec = registry[format],
            logger = logger,
            detection = changeDetection
        )
        target[type] = engine
        LazyConfigReference.bind(clazz, engine)
    }

    private fun configFile(
        clazz: KClass<*>,
        rawName: String?,
        format: String,
        nested: Boolean,
        explicitNamespace: String?
    ): File {
        val name = rawName?.ifEmpty { null } ?: clazz.simpleName!!.lowercase().removeSuffix("config")
        val namespace =
            explicitNamespace?.takeIf(String::isNotEmpty) ?: namespaceResolver?.invoke(clazz, clazz.java.classLoader)
                ?.takeIf(String::isNotEmpty)
        val root = namespace?.let { File(basePath, it) } ?: basePath
        return if (nested) File(root, "$name/$name.$format") else File(root, "$name.$format")
    }

    internal fun start(registrations: List<ApolloBuilder.Registration>) {
        for (registration in registrations) registerClass(registration.clazz, configs, registration)
        configs.keys.forEach { pollTracked(it, "Initial load failed") }
        startPolling()
        warnUnboundListeners()
    }

    private fun warnUnboundListeners() {
        for (name in LazyConfigReference.unboundListenerClasses()) logger.warn("config<$name>() has listeners but $name is not registered — " + "annotate it with @FileConfig in a scanned package, or register it on an Apollo instance.")
    }

    private fun pollTracked(type: KType, failureLabel: String) {
        configs[type]?.let { reference ->
            runCatching { reference.poll(); errored.remove(type) }.onFailure {
                if (errored.add(
                        type
                    )
                ) logger.error("$failureLabel: ${it.message}", it)
            }
        }
    }

    private fun startPolling() {
        if (configs.isEmpty()) return
        scheduler = PollingScheduler(pollingRateMs) { configs.keys.forEach { pollTracked(it, "Error polling config") } }
    }
}

public inline fun <reified T : Any> Apollo.config(): ConfigReference<T> = config(T::class)
public inline fun <reified T : Any> Apollo.register(): Unit = register(T::class)
public fun <T : Any> config(clazz: KClass<T>): ConfigReference<T> = LazyConfigReference.forType(clazz)
public inline fun <reified T : Any> config(): ConfigReference<T> = config(T::class)
