package com.beespoon.apollo
import java.io.File
import kotlin.test.*
import com.beespoon.apollo.testdata.SimpleConfig
import org.junit.jupiter.api.io.TempDir
class ListenerTest {
    @Test fun firesOnEachChange(@TempDir dir: File) {
        val r = ref<SimpleConfig>(dir)
        val received = mutableListOf<SimpleConfig>()
        r.listen { received.add(it) }
        r.poll()
        val f = File(dir, "config.json")
        f.writeText(f.readText().replace("\"default\"", "\"changed\""))
        f.bumpMTime()
        r.poll()
        assertEquals(listOf("default", "changed"), received.map { it.name })
    }
    @Test fun everyListenerFires(@TempDir dir: File) {
        val r = ref<SimpleConfig>(dir)
        var a = 0
        var b = 0
        r.listen { a++ }; r.listen { b++ }
        r.poll()
        assertEquals(1, a); assertEquals(1, b)
    }
    @Test fun lateListenerReplaysCurrentValueOnce(@TempDir dir: File) {
        val r = ref<SimpleConfig>(dir)
        r.poll()
        var fires = 0
        r.listen { fires++ }
        assertEquals(1, fires)
        r.poll()
        assertEquals(1, fires)
    }
    @Test fun cancelledListenerStopsReceiving(@TempDir dir: File) {
        val r = ref<SimpleConfig>(dir)
        r.poll()
        val received = mutableListOf<SimpleConfig>()
        val sub = r.listen { received.add(it) }
        sub.cancel()
        val f = File(dir, "config.json")
        f.writeText(f.readText().replace("\"default\"", "\"changed\""))
        f.bumpMTime()
        r.poll()
        assertEquals(1, received.size)
    }
    @Test fun throwingListenerDoesNotBlockOthers(@TempDir dir: File) {
        val r = ref<SimpleConfig>(dir)
        var reached = false
        r.listen { throw RuntimeException("boom") }
        r.listen { reached = true }
        r.poll()
        assertTrue(reached)
    }
}
