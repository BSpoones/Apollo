package com.beespoon.apollo
import java.io.File
import kotlin.test.*
import com.beespoon.apollo.api.*
import com.beespoon.apollo.testdata.SimpleConfig
import org.junit.jupiter.api.io.TempDir
private class MutableRef<T : Any>(initial: T) : ConfigReference<T> {
    private var current: T = initial
    private val listeners = mutableListOf<ConfigListener<T>>()
    override var state: ConfigState<T> = ConfigState.Loaded(initial)
        private set
    fun set(v: T) {
        current = v
        state = ConfigState.Loaded(v)
        listeners.forEach { it.onReload(v) }
    }
    fun fail(e: Throwable, last: T?) { state = ConfigState.Error(e, last) }
    override val value: T get() = current
    override fun listen(listener: ConfigListener<T>): ConfigSubscription {
        listeners.add(listener)
        (state as? ConfigState.Loaded)?.let { listener.onReload(it.value) }
        return ConfigSubscription { listeners.remove(listener) }
    }
    override fun poll() = current
}
class MapReferenceTest {
    @Test fun extractsInitialValue() {
        val name: String by MutableRef(SimpleConfig(name = "hello")).map { it.name }
        assertEquals("hello", name)
    }
    @Test fun tracksUpdates() {
        val r = MutableRef(SimpleConfig(name = "a"))
        val name: String by r.map { it.name }
        assertEquals("a", name)
        r.set(SimpleConfig(name = "b"))
        assertEquals("b", name)
    }
    @Test fun derivesProjection() {
        val r = MutableRef(SimpleConfig(name = "hello"))
        val length: Int by r.map { it.name.length }
        assertEquals(5, length)
        r.set(SimpleConfig(name = "hi"))
        assertEquals(2, length)
    }
    @Test fun transformRunsOnceUntilChange() {
        var runs = 0
        val r = MutableRef(SimpleConfig(count = 10))
        val count: Int by r.map { runs++; it.count }
        assertEquals(0, runs)
        assertEquals(10, count); assertEquals(10, count)
        assertEquals(1, runs)
    }
    @Test fun listenerFiresOnlyWhenProjectionChanges() {
        val r = MutableRef(SimpleConfig(name = "a", count = 1))
        val fires = mutableListOf<String>()
        r.map { it.name }.listen { fires.add(it) }
        assertEquals(listOf("a"), fires)
        r.set(SimpleConfig(name = "a", count = 2))
        assertEquals(listOf("a"), fires)
        r.set(SimpleConfig(name = "b", count = 2))
        assertEquals(listOf("a", "b"), fires)
    }
    @Test fun chains() {
        val r = MutableRef(SimpleConfig(name = "hello"))
        val length: Int by r.map { it.name }.map { it.length }
        assertEquals(5, length)
        r.set(SimpleConfig(name = "hi"))
        assertEquals(2, length)
    }
    @Test fun pollProjects() { assertEquals("hello", MutableRef(SimpleConfig(name = "hello")).map { it.name }.poll()) }
    @Test fun stateMapsLoaded() {
        val st = MutableRef(SimpleConfig(name = "hello")).map { it.name }.state
        assertIs<ConfigState.Loaded<String>>(st)
        assertEquals("hello", st.value)
    }
    @Test fun stateMapsErrorWithAndWithoutLastValue() {
        val r = MutableRef(SimpleConfig(name = "hello"))
        val mapped = r.map { it.name }
        r.fail(RuntimeException("boom"), SimpleConfig(name = "stale"))
        val withLast = mapped.state
        assertIs<ConfigState.Error<*>>(withLast)
        assertEquals("stale", withLast.lastValue)
        r.fail(RuntimeException("boom"), null)
        val withoutLast = mapped.state
        assertIs<ConfigState.Error<*>>(withoutLast)
        assertNull(withoutLast.lastValue)
    }
    @Test fun stateMapsLoading(@TempDir dir: File) { assertSame(ConfigState.Loading, ref<SimpleConfig>(dir).map { it.name }.state) }
    @Test fun projectsBeforeFirstLoad(@TempDir dir: File) {
        val name: String by ref<SimpleConfig>(dir).map { it.name }
        assertEquals("default", name)
    }
}
