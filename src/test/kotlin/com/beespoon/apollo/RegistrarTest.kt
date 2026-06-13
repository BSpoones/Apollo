package com.beespoon.apollo
import java.io.File
import kotlin.test.*
import com.beespoon.apollo.testdata.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
class RegistrarTest {
    @AfterEach fun tearDown() { Apollo.reset(); ServiceRegistrar.invocations = 0 }
    @Test fun manualRegistrarRegistersConfigs(@TempDir dir: File) {
        val apollo = Apollo.build { basePath = dir; registrar { builder -> builder.register(SimpleConfig::class, namespace = "manual") } }
        assertTrue(File(dir, "manual/simple.json").exists())
        assertEquals("default", apollo.config<SimpleConfig>().value.name)
    }
    @Test fun discoveredRegistrarsApplyOnBuild(@TempDir dir: File) {
        val apollo = Apollo.build { basePath = dir; discoverRegistrars() }
        assertTrue(File(dir, "svc/from-registrar.json").exists()); assertTrue(File(dir, "svc2/standalone.json").exists())
        assertEquals(42, apollo.config<SimpleConfig>().value.count)
    }
    @Test fun explicitRegistrationWinsOverDiscovered(@TempDir dir: File) {
        Apollo.build { basePath = dir; discoverRegistrars(); register<SimpleConfig>(name = "explicit") }
        assertTrue(File(dir, "explicit.json").exists()); assertFalse(File(dir, "svc/from-registrar.json").exists())
    }
    @Test fun registrarVisibleThroughSeveralLoadersAppliesOnce(@TempDir dir: File) {
        Apollo.build { basePath = dir; classLoader = ServiceRegistrar::class.java.classLoader; classLoaders(ServiceRegistrar::class.java.classLoader); discoverRegistrars() }
        assertEquals(1, ServiceRegistrar.invocations)
    }
}
