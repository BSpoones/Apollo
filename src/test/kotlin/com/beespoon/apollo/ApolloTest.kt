package com.beespoon.apollo
import java.io.File
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import com.beespoon.apollo.api.ConfigState
import com.beespoon.apollo.testdata.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
class ApolloTest {
    @AfterEach fun tearDown() = Apollo.reset()
    @Test fun buildSetsGlobalInstance(@TempDir dir: File) {
        val apollo = Apollo.build { basePath = dir }
        assertSame(apollo, Apollo.instance)
    }
    @Test fun instanceBeforeBuildThrows() {
        Apollo.reset()
        assertFailsWith<IllegalStateException> { Apollo.instance }
    }
    @Test fun missingBasePathThrows() { assertFailsWith<IllegalStateException> { Apollo.build { } } }
    @Test fun registerWritesAndExposesConfig(@TempDir dir: File) {
        val apollo = Apollo.build { basePath = dir }
        apollo.register<SimpleConfig>()
        assertTrue(File(dir, "simple.json").exists())
        val cfg: SimpleConfig by apollo.config<SimpleConfig>()
        assertEquals("default", cfg.name)
        assertEquals("default", apollo.config<SimpleConfig>().value.name)
    }
    @Test fun builderRegisterLoadsOnBuild(@TempDir dir: File) {
        Apollo.build { basePath = dir; register<SimpleConfig>() }
        assertTrue(File(dir, "simple.json").exists())
    }
    @Test fun scanRegistersAnnotatedConfigs(@TempDir dir: File) {
        Apollo.build { basePath = dir; classLoader = ScannableConfig::class.java.classLoader; packages("com.beespoon.apollo") }
        assertTrue(File(dir, "scannable.json").exists())
    }
    @Test fun malformedScannedConfigDoesNotAbortRegistration(@TempDir dir: File) {
        val apollo = Apollo.build { basePath = dir; classLoader = ScannableConfig::class.java.classLoader; packages("com.beespoon.apollo") }
        assertTrue(File(dir, "scannable.json").exists())
        assertFailsWith<IllegalStateException> { apollo.config<BrokenScannableConfig>() }
    }
    @Test fun listenerFiresOnBuildAndReload(@TempDir dir: File) {
        val received = mutableListOf<SimpleConfig>()
        config<SimpleConfig>().listen { received.add(it) }
        Apollo.build { basePath = dir; pollingRate = 60.seconds; register<SimpleConfig>() }
        assertEquals(1, received.size)
        val f = File(dir, "simple.json")
        f.writeText(f.readText().replace("\"default\"", "\"changed\""))
        f.bumpMTime()
        config<SimpleConfig>().poll()
        assertEquals(listOf("default", "changed"), received.map { it.name })
    }
    @Test fun throwingListenerDoesNotBlockOthers(@TempDir dir: File) {
        val received = mutableListOf<SimpleConfig>()
        config<SimpleConfig>().listen { throw RuntimeException("boom") }
        config<SimpleConfig>().listen { received.add(it) }
        Apollo.build { basePath = dir; pollingRate = 60.seconds; register<SimpleConfig>() }
        assertEquals(1, received.size)
    }
    @Test fun backgroundPollingReloadsAndRecovers(@TempDir dir: File) {
        Apollo.build { basePath = dir; pollingRate = 15.milliseconds; register<SimpleConfig>() }
        val f = File(dir, "simple.json")
        f.writeText("not json")
        f.bumpMTime()
        awaitUntil { config<SimpleConfig>().state is ConfigState.Error }
        f.writeText("""{"name":"recovered","count":1,"enabled":true}""")
        f.bumpMTime()
        awaitUntil {
            val st = config<SimpleConfig>().state
            st is ConfigState.Loaded && st.value.name == "recovered"
        }
    }
    @Test fun buildWithNoConfigsStartsNoScheduler(@TempDir dir: File) {
        val apollo = Apollo.build { basePath = dir }
        assertSame(apollo, Apollo.instance)
    }
    @Test fun secondBuildKeepsFirstInstancePolling(@TempDir dirA: File, @TempDir dirB: File) {
        val a = Apollo.build { basePath = dirA; pollingRate = 15.milliseconds; register<SimpleConfig>() }
        val b = Apollo.build { basePath = dirB; register<Standalone>() }
        assertSame(b, Apollo.instance)
        val f = File(dirA, "simple.json")
        f.writeText(f.readText().replace("\"default\"", "\"still-polling\""))
        f.bumpMTime()
        awaitUntil { a.config<SimpleConfig>().value.name == "still-polling" }
    }
    @Test fun instanceConfigIsInstanceScoped(@TempDir dirA: File, @TempDir dirB: File) {
        val a = Apollo.build { basePath = dirA; register<SimpleConfig>() }
        val b = Apollo.build { basePath = dirB; register<Standalone>() }
        assertEquals("default", a.config<SimpleConfig>().value.name)
        assertFailsWith<IllegalStateException> { b.config<SimpleConfig>() }
    }
    @Test fun warnsAboutListenersOnUnregisteredTypes(@TempDir dir: File) {
        config<Unregistered>().listen { }
        Apollo.build { basePath = dir }
        assertIs<ConfigState.Loading>(config<Unregistered>().state)
    }
}
