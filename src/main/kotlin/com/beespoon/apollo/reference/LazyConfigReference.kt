package com.beespoon.apollo.reference

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import com.beespoon.apollo.Apollo
import com.beespoon.apollo.api.*

internal class LazyConfigReference<T : Any> private constructor(private val cls: KClass<T>) : ConfigReference<T> {
    @Volatile
    private var engine: ConfigReference<T>? = null
    private val buffered = mutableListOf<BufferedSubscription>()
    private fun engine(): ConfigReference<T> = engine ?: run {
        Apollo.instance
        error("No config registered for ${cls.qualifiedName}. " + "Annotate it with @FileConfig in a scanned package, or register it on an Apollo instance.")
    }

    override val state: ConfigState<T> get() = engine?.state ?: ConfigState.Loading
    override val value: T get() = engine().value
    override fun poll(): T = engine().poll()

    @Synchronized
    override fun listen(listener: ConfigListener<T>): ConfigSubscription {
        engine?.let { return it.listen(listener) }
        return BufferedSubscription(listener).also { buffered.add(it) }
    }

    @Synchronized
    private fun bind(engine: ConfigReference<T>) {
        this.engine = engine
        buffered.forEach { it.migrate(engine) }
        buffered.clear()
    }

    @Synchronized
    private fun unbind() {
        engine = null
        buffered.clear()
    }

    @Synchronized
    private fun hasBufferedListeners(): Boolean = engine == null && buffered.isNotEmpty()
    private inner class BufferedSubscription(private val listener: ConfigListener<T>) : ConfigSubscription {
        @Volatile
        private var migrated: ConfigSubscription? = null
        @Volatile
        private var cancelled = false
        fun migrate(engine: ConfigReference<T>) {
            if (!cancelled) migrated = engine.listen(listener)
        }

        override fun cancel() {
            cancelled = true
            synchronized(this@LazyConfigReference) { buffered.remove(this) }
            migrated?.cancel()
        }
    }

    companion object {
        private val registry = ConcurrentHashMap<KClass<*>, LazyConfigReference<*>>()

        @Suppress("UNCHECKED_CAST")
        internal fun <T : Any> forType(cls: KClass<T>): LazyConfigReference<T> =
            registry.getOrPut(cls) { LazyConfigReference(cls) } as LazyConfigReference<T>

        @Suppress("UNCHECKED_CAST")
        internal fun bind(cls: KClass<*>, engine: ConfigReference<*>) {
            (forType(cls) as LazyConfigReference<Any>).bind(engine as ConfigReference<Any>)
        }

        internal fun unbindAll() {
            registry.values.forEach { it.unbind() }
        }

        internal fun unboundListenerClasses(): List<String> =
            registry.entries.filter { it.value.hasBufferedListeners() }
                .map { it.key.qualifiedName ?: it.key.toString() }
    }
}
