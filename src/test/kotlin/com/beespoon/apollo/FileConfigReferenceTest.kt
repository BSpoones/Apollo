package com.beespoon.apollo
import java.io.File
import kotlin.test.*
import com.beespoon.apollo.api.ConfigException
import com.beespoon.apollo.testdata.SimpleConfig
import org.junit.jupiter.api.io.TempDir
class FileConfigReferenceTest {
    @Test fun writesAndReadsDefaults(@TempDir dir: File) {
        val c = ref<SimpleConfig>(dir).poll()
        assertTrue(File(dir, "config.json").exists())
        assertEquals("default", c.name)
        assertEquals(42, c.count)
        assertTrue(c.enabled)
    }
    @Test fun unchangedPollReturnsSameInstance(@TempDir dir: File) {
        val r = ref<SimpleConfig>(dir)
        assertSame(r.poll(), r.poll())
    }
    @Test fun valueLoadsOnFirstAccess(@TempDir dir: File) {
        val r = ref<SimpleConfig>(dir)
        assertEquals("default", r.value.name)
        assertSame(r.value, r.value)
    }
    @Test fun reloadsOnModification(@TempDir dir: File) {
        val r = ref<SimpleConfig>(dir)
        val first = r.poll()
        val f = File(dir, "config.json")
        f.writeText(f.readText().replace("\"default\"", "\"modified\""))
        f.bumpMTime()
        val second = r.poll()
        assertNotSame(first, second)
        assertEquals("modified", second.name)
    }
    @Test fun delegatesCurrentValue(@TempDir dir: File) {
        val r = ref<SimpleConfig>(dir)
        val c: SimpleConfig by r
        assertEquals("default", c.name)
    }
    @Test fun ignoresUnknownKeys(@TempDir dir: File) {
        File(dir, "config.json").writeText("""{"name":"ok","count":1,"enabled":false,"extra":"x"}""")
        assertEquals("ok", ref<SimpleConfig>(dir).poll().name)
    }
    @Test fun throwsParseErrorOnMalformed(@TempDir dir: File) {
        File(dir, "config.json").writeText("{ not json }")
        assertFailsWith<ConfigException.ParseError> { ref<SimpleConfig>(dir).poll() }
    }
    @Test fun throwsParseErrorOnEmpty(@TempDir dir: File) {
        File(dir, "config.json").writeText("")
        assertFailsWith<ConfigException.ParseError> { ref<SimpleConfig>(dir).poll() }
    }
    @Test fun throwsDeserialisationErrorOnWrongType(@TempDir dir: File) {
        File(dir, "config.json").writeText("""{"name":"ok","count":"nan","enabled":true}""")
        assertFailsWith<ConfigException.DeserialisationError> { ref<SimpleConfig>(dir).poll() }
    }
    @Test fun valueThrowsWhenDefaultsCannotWrite(@TempDir dir: File) {
        File(dir, "blocker").writeText("i am a file")
        val r = ref<SimpleConfig>(File(dir, "blocker"))
        val c: SimpleConfig by r
        assertFailsWith<ConfigException.InitialisationError> { c.name }
    }
}
