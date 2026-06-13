package com.beespoon.apollo
import java.io.File
import kotlin.test.*
import com.beespoon.apollo.api.*
import com.beespoon.apollo.testdata.SimpleConfig
import org.junit.jupiter.api.io.TempDir
class ConfigStateTest {
    @Test fun initiallyLoading(@TempDir dir: File) { assertIs<ConfigState.Loading>(ref<SimpleConfig>(dir).state) }
    @Test fun loadedAfterPoll(@TempDir dir: File) {
        val r = ref<SimpleConfig>(dir)
        r.poll()
        val st = r.state
        assertIs<ConfigState.Loaded<SimpleConfig>>(st)
        assertEquals("default", st.value.name)
    }
    @Test fun errorOnParseFailure(@TempDir dir: File) {
        File(dir, "config.json").writeText("not json")
        val r = ref<SimpleConfig>(dir)
        runCatching { r.poll() }
        val st = r.state
        assertIs<ConfigState.Error<*>>(st)
        assertIs<ConfigException.ParseError>(st.exception)
        assertNull(st.lastValue)
    }
    @Test fun errorCarriesLastGoodValue(@TempDir dir: File) {
        val r = ref<SimpleConfig>(dir)
        val first = r.poll()
        File(dir, "config.json").writeText("broken")
        File(dir, "config.json").bumpMTime()
        runCatching { r.poll() }
        val st = r.state
        assertIs<ConfigState.Error<*>>(st)
        assertEquals(first, st.lastValue)
        assertEquals(first, r.value)
    }
    @Test fun recoversAfterError(@TempDir dir: File) {
        File(dir, "config.json").writeText("broken")
        val r = ref<SimpleConfig>(dir)
        runCatching { r.poll() }
        assertIs<ConfigState.Error<*>>(r.state)
        File(dir, "config.json").writeText("""{"name":"fixed","count":1,"enabled":true}""")
        File(dir, "config.json").bumpMTime()
        r.poll()
        val st = r.state
        assertIs<ConfigState.Loaded<SimpleConfig>>(st)
        assertEquals("fixed", st.value.name)
    }
}
