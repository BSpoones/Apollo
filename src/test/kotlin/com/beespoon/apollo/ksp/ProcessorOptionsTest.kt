package com.beespoon.apollo.ksp
import kotlin.test.*
class ProcessorOptionsTest {
    @Test fun defaultsLeaveNamespaceUnset() {
        val options = ProcessorOptions.from(emptyMap())
        assertNull(options.namespaceAnnotation)
        assertEquals("id", options.namespaceParameter)
        assertNull(options.namespace)
        assertNull(options.defaultNamespace)
        assertFalse(options.requireNamespace)
    }
    @Test fun hashSyntaxSelectsNamespaceParameter() {
        val options = ProcessorOptions.from(mapOf("apollo.namespaceAnnotation" to "com.example.PluginMetadata#identifier", "apollo.namespace" to "forced", "apollo.defaultNamespace" to "shared", "apollo.requireNamespace" to "true"))
        assertEquals("com.example.PluginMetadata", options.namespaceAnnotation)
        assertEquals("identifier", options.namespaceParameter)
        assertEquals("forced", options.namespace)
        assertEquals("shared", options.defaultNamespace)
        assertTrue(options.requireNamespace)
    }
    @Test fun trailingHashFallsBackToIdParameter() {
        val options = ProcessorOptions.from(mapOf("apollo.namespaceAnnotation" to "com.example.PluginMetadata#", "apollo.namespace" to " "))
        assertEquals("com.example.PluginMetadata", options.namespaceAnnotation)
        assertEquals("id", options.namespaceParameter)
        assertNull(options.namespace)
    }
}
