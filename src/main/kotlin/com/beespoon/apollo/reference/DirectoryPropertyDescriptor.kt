package com.beespoon.apollo.reference

import java.io.File
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaField
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.JsonNamingStrategy
import com.beespoon.apollo.annotation.DirectoryConfig

internal data class DirectoryPropertyDescriptor(
    val propertyName: String,
    val serialName: String,
    val dirName: String,
    val mode: Mode,
    val elementType: KClass<*>,
    val children: List<DirectoryPropertyDescriptor>
) {
    enum class Mode { COLLECTION, MAP }

    fun entryFile(dir: File, extension: String): File = File(dir, "${dir.name}.$extension")
    fun entryFile(dir: File, name: String, extension: String): File = File(dir, "$name.$extension")

    companion object {
        fun discover(
            clazz: KClass<*>,
            namingStrategy: JsonNamingStrategy?,
            visited: MutableSet<KClass<*>> = mutableSetOf()
        ): List<DirectoryPropertyDescriptor> {
            if (!visited.add(clazz)) error("Cycle detected in @DirectoryConfig hierarchy: ${clazz.qualifiedName}")
            return clazz.memberProperties.mapNotNull { property ->
                val annotations = property.annotations + property.javaField?.annotations?.toList().orEmpty()
                val marker = annotations.filterIsInstance<DirectoryConfig>().firstOrNull() ?: return@mapNotNull null
                fromProperty(property, clazz, marker.name, namingStrategy, visited)
            }.also { visited.remove(clazz) }
        }

        fun declare(
            property: KProperty1<*, *>,
            owner: KClass<*>,
            dirName: String,
            namingStrategy: JsonNamingStrategy?
        ): DirectoryPropertyDescriptor = fromProperty(property, owner, dirName, namingStrategy, mutableSetOf(owner))

        private fun fromProperty(
            property: KProperty1<*, *>,
            owner: KClass<*>,
            rawName: String,
            namingStrategy: JsonNamingStrategy?,
            visited: MutableSet<KClass<*>>
        ): DirectoryPropertyDescriptor {
            val propertyType = property.returnType.classifier as? KClass<*>
            val mode = when {
                propertyType != null && propertyType.isSubclassOf(Collection::class) -> Mode.COLLECTION
                propertyType != null && propertyType.isSubclassOf(Map::class) -> Mode.MAP
                else -> error("@DirectoryConfig on '${property.name}' in ${owner.qualifiedName} must be a Collection<*> or Map<String, *>")
            }

            val elementArgument =
                if (mode == Mode.COLLECTION) property.returnType.arguments.firstOrNull() else property.returnType.arguments.getOrNull(
                    1
                )

            val elementType = elementArgument?.type?.classifier as? KClass<*>
                ?: error("Cannot resolve element type for property '${property.name}' in ${owner.qualifiedName}")
            return DirectoryPropertyDescriptor(
                property.name,
                property.findAnnotation<SerialName>()?.value ?: serialName(property.name, namingStrategy),
                rawName.ifEmpty { property.name.lowercase() },
                mode,
                elementType,
                discover(elementType, namingStrategy, visited)
            )
        }

        private fun serialName(name: String, strategy: JsonNamingStrategy?): String {
            strategy ?: return name
            val descriptor = buildClassSerialDescriptor("Apollo") {
                element(
                    name,
                    PrimitiveSerialDescriptor("Apollo", PrimitiveKind.STRING)
                )
            }
            return strategy.serialNameForJson(descriptor, 0, name)
        }
    }
}
