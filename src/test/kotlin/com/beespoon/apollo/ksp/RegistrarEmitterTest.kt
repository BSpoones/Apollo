package com.beespoon.apollo.ksp
import kotlin.test.*
class RegistrarEmitterTest {
    @Test fun escapesMetacharactersInQuotedValues() {
        val model = RegistrarModel(namespace = null, configs = listOf(RegistrarModel.Config("com.example.Weird", name = "we\"ird\$na\\me", format = "json", directories = emptyList())), polymorphicBases = emptyList(), polymorphicSubtypes = emptyMap())
        val source = RegistrarEmitter.emit(model, "Registrar")
        assertTrue(source.contains("name = \"we\\\"ird\${'\$'}na\\\\me\""), source)
    }
    @Test fun classNameSanitisesNamespaceAndStaysCollisionFree() {
        val model = RegistrarModel(namespace = "my-plugin", configs = listOf(RegistrarModel.Config("com.example.A", "a", "json", emptyList())), polymorphicBases = emptyList(), polymorphicSubtypes = emptyMap())
        val name = RegistrarEmitter.className(model)
        assertTrue(name.startsWith("ApolloRegistrar_my_plugin_"), name)
        assertEquals(name, RegistrarEmitter.className(model))
        assertNotEquals(name, RegistrarEmitter.className(model.copy(configs = listOf(RegistrarModel.Config("com.example.B", "b", "json", emptyList())))))
    }
}
