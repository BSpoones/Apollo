package com.beespoon.apollo
import java.io.File
import kotlin.reflect.typeOf
import kotlin.test.*
import com.beespoon.apollo.api.ConfigException
class ConfigExceptionTest {
    @Test fun parseErrorCarriesFile() {
        val f = File("/tmp/test.json")
        val e = ConfigException.ParseError(f, RuntimeException("bad"))
        assertEquals(f, e.file); assertIs<RuntimeException>(e)
    }
    @Test fun deserialisationErrorCarriesTypeAndFile() {
        val f = File("/tmp/test.json")
        val e = ConfigException.DeserialisationError(typeOf<String>(), f, RuntimeException("wrong"))
        assertEquals(typeOf<String>(), e.type); assertEquals(f, e.file)
    }
    @Test fun initialisationErrorCarriesType() {
        val e = ConfigException.InitialisationError(typeOf<String>(), RuntimeException("disk"))
        assertEquals(typeOf<String>(), e.type)
    }
}
