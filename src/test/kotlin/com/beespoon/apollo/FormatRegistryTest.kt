package com.beespoon.apollo
import kotlin.test.*
import kotlinx.serialization.json.jsonObject
import com.beespoon.apollo.format.*
class FormatRegistryTest {
    @Test fun containsRegisteredExtension() { assertTrue("json" in REG); assertFalse("nope" in REG) }
    @Test fun missingCodecThrows() { assertFailsWith<IllegalStateException> { FormatRegistry()["nope"] } }
    @Test fun codecRoundTrips() {
        val codec = JsonFormatCodec(J)
        val element = codec.parse("""{"a":1}""")
        assertTrue("a" in element.jsonObject)
        assertTrue(codec.write(element).contains("\"a\""))
    }
}
