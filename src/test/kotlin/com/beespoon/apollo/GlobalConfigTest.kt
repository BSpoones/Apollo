package com.beespoon.apollo
import java.io.File
import kotlin.test.*
import com.beespoon.apollo.api.ConfigState
import com.beespoon.apollo.testdata.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
class GlobalConfigTest {
    @AfterEach fun tearDown() = Apollo.reset()
    @Test fun forTypeReturnsSameHandle() { assertSame(config<SimpleConfig>(), config<SimpleConfig>()) }
    @Test fun stateLoadingWhenUnbound() { Apollo.reset(); assertIs<ConfigState.Loading>(config<Standalone>().state) }
    @Test fun readBeforeBuildThrows() {
        Apollo.reset()
        val cfg: SimpleConfig by config<SimpleConfig>()
        assertFailsWith<IllegalStateException> { cfg.name }
    }
    @Test fun readingUnregisteredTypeAfterBuildThrows(@TempDir dir: File) {
        Apollo.build { basePath = dir }
        assertFailsWith<IllegalStateException> { config<Unregistered>().poll() }
    }
    @Test fun valueThroughGlobalHandle(@TempDir dir: File) {
        Apollo.build { basePath = dir }.register<SimpleConfig>()
        assertEquals("default", config<SimpleConfig>().value.name)
    }
    @Test fun bufferedListenerFiresOnRegistration(@TempDir dir: File) {
        Apollo.reset()
        val received = mutableListOf<SimpleConfig>()
        config<SimpleConfig>().listen { received.add(it) }
        Apollo.build { basePath = dir }.register<SimpleConfig>()
        assertEquals(1, received.size); assertEquals("default", received[0].name)
    }
    @Test fun listenReplaysOnInstanceHandle(@TempDir dir: File) {
        val apollo = Apollo.build { basePath = dir }
        apollo.register<SimpleConfig>()
        val received = mutableListOf<SimpleConfig>()
        apollo.config<SimpleConfig>().listen { received.add(it) }
        assertEquals(1, received.size)
    }
    @Test fun bufferedSubscriptionCancelledBeforeBuildNeverFires(@TempDir dir: File) {
        Apollo.reset()
        val received = mutableListOf<SimpleConfig>()
        val sub = config<SimpleConfig>().listen { received.add(it) }
        sub.cancel()
        Apollo.build { basePath = dir }.register<SimpleConfig>()
        assertTrue(received.isEmpty())
    }
    @Test fun bufferedSubscriptionMigratesThenCancels(@TempDir dir: File) {
        Apollo.reset()
        val received = mutableListOf<SimpleConfig>()
        val sub = config<SimpleConfig>().listen { received.add(it) }
        Apollo.build { basePath = dir }.register<SimpleConfig>()
        assertEquals(1, received.size)
        sub.cancel()
        val f = File(dir, "simple.json")
        f.writeText(f.readText().replace("\"default\"", "\"changed\""))
        f.bumpMTime()
        config<SimpleConfig>().poll()
        assertEquals(1, received.size)
    }
    @Test fun mapResolvesAfterBuild(@TempDir dir: File) {
        Apollo.reset()
        val count: Int by config<SimpleConfig>().map { it.count }
        Apollo.build { basePath = dir }.register<SimpleConfig>()
        assertEquals(42, count)
    }
}
