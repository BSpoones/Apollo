package com.beespoon.apollo
import kotlin.test.*
import kotlinx.serialization.json.JsonElement
import com.beespoon.apollo.format.FormatCodec
import com.beespoon.apollo.reload.ChangeDetection
import com.beespoon.apollo.testdata.*
class ApolloBuilderTest {
    @Test fun jsonBlockIsApplied() { assertNotNull(ApolloBuilder().apply { json { prettyPrint = false } }.jsonConfig) }
    @Test fun codecRegistersByExtension() { assertTrue("yaml" in ApolloBuilder().apply { codec("yaml", stubCodec()) }.registry) }
    @Test fun changeDetectionIsConfigurable() { assertEquals(ChangeDetection.CONTENT, ApolloBuilder().apply { changeDetection = ChangeDetection.CONTENT }.changeDetection) }
    @Test fun registerCollectsClasses() {
        val builder = ApolloBuilder().apply { register<SimpleConfig>(); register(Standalone::class) }
        assertEquals(listOf(SimpleConfig::class, Standalone::class), builder.registrations.map { it.clazz })
    }
    private fun stubCodec() = object : FormatCodec {
        override fun parse(text: String): JsonElement = throw NotImplementedError()
        override fun write(element: JsonElement): String = throw NotImplementedError()
    }
}
