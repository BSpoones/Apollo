package com.beespoon.apollo.reference

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.*
import com.beespoon.apollo.api.*
import com.beespoon.apollo.format.*
import com.beespoon.apollo.reload.ChangeDetection
import com.beespoon.apollo.util.*
import org.slf4j.Logger

internal class FileConfigReference<T : Any>(
    private val file: File,
    private val type: KType,
    private val directoryProps: List<DirectoryPropertyDescriptor> = emptyList(),
    private val json: Json,
    private val codec: FormatCodec,
    private val logger: Logger,
    private val detection: ChangeDetection = ChangeDetection.MODIFICATION_TIME
) : ConfigReference<T> {
    private val extension = file.extension
    @Suppress("UNCHECKED_CAST")
    private val serialiser by lazy { json.serializersModule.serializer(type) as KSerializer<T> }
    private val currentValue = AtomicReference<T?>()
    private val listeners: MutableList<Subscription> = CopyOnWriteArrayList()
    private val _state = AtomicReference<ConfigState<T>>(ConfigState.Loading)
    private var snapshots: Map<String, FileSnapshot> = emptyMap()
    private var backfilled = false
    override val state: ConfigState<T> get() = _state.get()
    override val value: T get() = currentValue.get() ?: poll()
    override fun listen(listener: ConfigListener<T>): ConfigSubscription {
        val subscription = Subscription(listener)
        synchronized(this) {
            listeners.add(subscription)
            currentValue.get()?.let { subscription.deliver(it) }
        }
        return ConfigSubscription { listeners.remove(subscription) }
    }

    override fun poll(): T {
        var reloaded: T? = null
        val result: T = synchronized(this) {
            if (!file.exists()) {
                try {
                    writeDefaults()
                } catch (e: Exception) {
                    val error = ConfigException.InitialisationError(type, e)
                    _state.set(ConfigState.Error(error, currentValue.get()))
                    return currentValue.get() ?: throw error
                }
            }
            val current = currentValue.get()
            touchTree(file, directoryProps)
            val newSnapshots = takeSnapshots(file, directoryProps)
            if (detection == ChangeDetection.MODIFICATION_TIME && current != null && newSnapshots == snapshots) return@synchronized current
            val data = try {
                read()
            } catch (e: ConfigException) {
                _state.set(ConfigState.Error(e, current))
                throw e
            }
            if (!backfilled) {
                backfilled = true
                runCatching { backfill(data) }.onFailure { logger.warn("Backfill failed: ${it.message}") }
                snapshots = takeSnapshots(file, directoryProps)
            } else {
                snapshots = newSnapshots
            }
            if (current != null && data == current) return@synchronized current
            currentValue.set(data)
            _state.set(ConfigState.Loaded(data))
            reloaded = data
            data
        }
        reloaded?.let { value -> listeners.forEach { it.deliver(value) } }
        return result
    }

    private inner class Subscription(private val listener: ConfigListener<T>) {
        private val lastDelivered = AtomicReference<Any?>(UNDELIVERED)
        fun deliver(value: T) {
            if (lastDelivered.getAndSet(value) === value) return
            runCatching { listener.onReload(value) }.onFailure {
                logger.error(
                    "Config listener failed: ${it.message}",
                    it
                )
            }
        }
    }

    private fun read(): T {
        val merged = readElement(file, file.parentFile, directoryProps, codec)
        return try {
            json.decodeFromJsonElement(serialiser, merged)
        } catch (e: Exception) {
            throw attribute(e)
        }
    }

    private fun attribute(cause: Exception): ConfigException {
        walkEntries(directoryProps, file.parentFile ?: file, extension, childrenFirst = true) { entryFile, child, entryDir ->
            val element = if (child.children.isEmpty()) parse(entryFile, codec) else readElement(
                entryFile,
                entryDir,
                child.children,
                codec
            )
            try {
                json.decodeFromJsonElement(serialiserFor(child.elementType), element)
            } catch (e: Exception) {
                throw ConfigException.DeserialisationError(child.elementType.starProjectedType, entryFile, e)
            }
        }
        return ConfigException.DeserialisationError(type, file, cause)
    }

    private fun readElement(
        target: File,
        container: File,
        children: List<DirectoryPropertyDescriptor>,
        activeCodec: FormatCodec
    ): JsonObject {
        val fields = parseObject(target, activeCodec).toMutableMap()
        for (child in children) fields[child.serialName] = foldChild(File(container, child.dirName), child)
        return JsonObject(fields)
    }

    private fun foldChild(childDir: File, child: DirectoryPropertyDescriptor): JsonElement {
        if (!childDir.exists()) return wrap(child.mode, emptyList())
        val entries: List<Pair<String, JsonElement>> = if (child.children.isNotEmpty()) {
            childDir.subdirectories().mapNotNull { subDir ->
                child.entryFile(subDir, extension).takeIf { it.exists() }
                    ?.let { subDir.name to readElement(it, subDir, child.children, codec) }
            }
        } else {
            childDir.filesWithExtension(extension).map { it.nameWithoutExtension to parse(it, codec) }
        }
        return wrap(child.mode, entries)
    }

    private fun wrap(mode: DirectoryPropertyDescriptor.Mode, entries: List<Pair<String, JsonElement>>): JsonElement =
        if (mode == DirectoryPropertyDescriptor.Mode.COLLECTION) JsonArray(entries.map { it.second }) else JsonObject(
            entries.toMap()
        )

    private fun parse(file: File, codec: FormatCodec): JsonElement = try {
        codec.parse(file.readText())
    } catch (e: ConfigException) {
        throw e
    } catch (e: Exception) {
        throw ConfigException.ParseError(file, e)
    }

    private fun parseObject(file: File, codec: FormatCodec): JsonObject =
        parse(file, codec) as? JsonObject ?: throw ConfigException.ParseError(
            file,
            IllegalArgumentException("Expected a JSON object")
        )

    private fun writeDefaults() {
        file.parentFile?.mkdirs()
        @Suppress("UNCHECKED_CAST")
        val defaults = (type.classifier as KClass<T>).createInstance()
        writeElement(
            json.encodeToJsonElement(serialiser, defaults).jsonObject,
            file,
            file.parentFile ?: file,
            directoryProps,
            codec
        )
    }

    private fun writeElement(
        obj: JsonObject,
        target: File,
        container: File,
        children: List<DirectoryPropertyDescriptor>,
        activeCodec: FormatCodec
    ) {
        val fields = obj.toMutableMap()
        for (child in children) {
            val element = fields.remove(child.serialName) ?: continue
            val childDir = File(container, child.dirName)
            if (childDir.exists()) continue
            childDir.mkdirs()
            if (child.mode == DirectoryPropertyDescriptor.Mode.COLLECTION) {
                element.jsonArray.forEachIndexed { index, item ->
                    writeChild(
                        item,
                        index.toString().padStart(COLLECTION_INDEX_WIDTH, '0'),
                        childDir,
                        child
                    )
                }
            } else {
                for ((key, value) in element.jsonObject) writeChild(value, key, childDir, child)
            }
        }
        target.parentFile?.mkdirs()
        target.writeTextAtomically(activeCodec.write(JsonObject(fields)))
    }

    private fun writeChild(
        element: JsonElement,
        name: String,
        childDir: File,
        child: DirectoryPropertyDescriptor
    ) {
        if (child.children.isNotEmpty()) {
            val entryDir = File(childDir, name)
            writeElement(element.jsonObject, child.entryFile(entryDir, name, extension), entryDir, child.children, codec)
        } else {
            child.entryFile(childDir, name, extension).writeTextAtomically(codec.write(element))
        }
    }

    private fun backfill(data: T) {
        backfillFile(
            file,
            json.encodeToJsonElement(serialiser, data).jsonObject,
            directoryProps,
            serialiser.descriptor,
            codec
        )
        backfillChildren(file.parentFile ?: file, directoryProps)
    }

    private fun backfillChildren(container: File, children: List<DirectoryPropertyDescriptor>) {
        walkEntries(children, container, extension) { entryFile, child, _ ->
            backfillSingle(
                entryFile,
                serialiserFor(child.elementType),
                child.children,
                codec
            )
        }
    }

    private fun backfillSingle(
        target: File,
        serialiser: KSerializer<Any?>,
        exclude: List<DirectoryPropertyDescriptor>,
        activeCodec: FormatCodec
    ) {
        val onDisk =
            runCatching { activeCodec.parse(target.readText()) }.onFailure { logger.warn("Backfill: failed to parse ${target.name}: ${it.message}") }
                .getOrNull() ?: return
        val decoded = runCatching {
            json.decodeFromJsonElement(
                serialiser,
                onDisk
            )
        }.onFailure { logger.warn("Backfill: failed to deserialise ${target.name}: ${it.message}") }.getOrNull()
            ?: return
        val reEncoded = json.encodeToJsonElement(serialiser, decoded) as? JsonObject ?: return
        backfillFile(target, reEncoded, exclude, serialiser.descriptor, activeCodec)
    }

    private fun backfillFile(
        target: File,
        reEncoded: JsonObject,
        exclude: List<DirectoryPropertyDescriptor>,
        descriptor: SerialDescriptor,
        activeCodec: FormatCodec
    ) {
        val onDisk = runCatching { activeCodec.parse(target.readText()).jsonObject }.getOrNull() ?: return
        val namingStrategy = json.configuration.namingStrategy
        val newKeys = mutableListOf<String>()
        for (i in 0 until descriptor.elementsCount) {
            if (descriptor.getElementDescriptor(i).isNullable) continue
            val elementName = descriptor.getElementName(i)
            if (exclude.any { it.propertyName == elementName || it.serialName == elementName }) continue
            val key = namingStrategy?.serialNameForJson(descriptor, i, elementName) ?: elementName
            if (key !in onDisk && key in reEncoded && reEncoded[key] !is JsonNull) newKeys += key
        }
        if (newKeys.isEmpty()) return
        val merged = onDisk.toMutableMap()
        for (key in newKeys) merged[key] = reEncoded.getValue(key)
        target.writeTextAtomically(activeCodec.write(JsonObject(merged)))
    }

    private fun serialiserFor(elementType: KClass<*>): KSerializer<Any?> =
        json.serializersModule.serializer(elementType.starProjectedType)

    private companion object {
        private val UNDELIVERED = Any()
        private const val COLLECTION_INDEX_WIDTH = 6
    }
}
