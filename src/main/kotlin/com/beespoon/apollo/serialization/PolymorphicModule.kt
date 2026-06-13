package com.beespoon.apollo.serialization

import io.github.classgraph.ScanResult
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.full.starProjectedType
import kotlinx.serialization.*
import kotlinx.serialization.modules.*
import org.slf4j.Logger

internal object PolymorphicModule {
    @Suppress("UNCHECKED_CAST")
    fun discover(
        scan: ScanResult?,
        logger: Logger,
        declaredBases: List<KClass<*>>,
        explicitSubtypes: List<Pair<KClass<*>, List<KClass<*>>>>
    ): SerializersModule {
        val explicitByBase = explicitSubtypes.groupBy({ it.first }, { it.second })
            .mapValues { (_, subtypeLists) -> subtypeLists.flatten() }
        val annotated = scan?.getClassesWithAnnotation(Polymorphic::class.java.name).orEmpty()
            .map { it.loadClass().kotlin to it.isInterface }
        val declared = (declaredBases + explicitByBase.keys).map { it to it.java.isInterface }
        val bases = (annotated + declared).distinctBy { (base, _) -> base.qualifiedName }.takeIf { it.isNotEmpty() }
            ?: return EmptySerializersModule()
        return SerializersModule {
            for ((baseClass, isInterface) in bases) {
                val base = baseClass as KClass<Any>
                val scanned =
                    (if (isInterface) scan?.getClassesImplementing(base.java.name) else scan?.getSubclasses(base.java.name)).orEmpty()
                        .filter { !it.isAbstract && !it.isInterface }.map { it.loadClass().kotlin as KClass<Any> }
                val explicit = explicitByBase[baseClass].orEmpty()
                    .filter { !it.java.isInterface && !Modifier.isAbstract(it.java.modifiers) }
                    .map { it as KClass<Any> }
                val implementations = (scanned + explicit).distinctBy { it.qualifiedName }
                polymorphic(base) { for (implementation in implementations) register(base, implementation, logger) }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun PolymorphicModuleBuilder<Any>.register(base: KClass<Any>, implementation: KClass<Any>, logger: Logger) {
        runCatching { subclass(implementation, serializer(implementation.starProjectedType) as KSerializer<Any>) }
            .onSuccess { logger.debug("Registered polymorphic subtype {}", implementation.qualifiedName) }
            .onFailure { logger.warn("Skipping subtype ${implementation.qualifiedName} of polymorphic base " + "${base.qualifiedName} — it has no @Serializable serialiser and will not (de)serialise (${it.message})") }
    }
}
