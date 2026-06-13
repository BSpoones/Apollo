package com.beespoon.apollo.ksp

internal data class ProcessorOptions(
    val namespaceAnnotation: String?,
    val namespaceParameter: String,
    val namespace: String?,
    val defaultNamespace: String?,
    val requireNamespace: Boolean
) {
    companion object {
        const val FILE_CONFIG = "com.beespoon.apollo.annotation.FileConfig"
        const val DIRECTORY_CONFIG = "com.beespoon.apollo.annotation.DirectoryConfig"
        const val POLYMORPHIC = "kotlinx.serialization.Polymorphic"
        fun from(options: Map<String, String>): ProcessorOptions {
            val namespaceAnnotation = options["apollo.namespaceAnnotation"]?.takeIf(String::isNotBlank)
            return ProcessorOptions(
                namespaceAnnotation = namespaceAnnotation?.substringBefore('#'),
                namespaceParameter = namespaceAnnotation?.substringAfter('#', "id")?.ifBlank { "id" } ?: "id",
                namespace = options["apollo.namespace"]?.takeIf(String::isNotBlank),
                defaultNamespace = options["apollo.defaultNamespace"]?.takeIf(String::isNotBlank),
                requireNamespace = options["apollo.requireNamespace"].toBoolean())
        }
    }
}
