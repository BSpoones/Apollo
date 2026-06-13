package com.beespoon.apollo.ksp

internal data class RegistrarModel(
    val namespace: String?,
    val configs: List<Config>,
    val polymorphicBases: List<String>,
    val polymorphicSubtypes: Map<String, List<String>>
) {
    data class Config(val className: String, val name: String, val format: String, val directories: List<Directory>)
    data class Directory(val propertyName: String, val name: String)

    val isEmpty: Boolean get() = configs.isEmpty() && polymorphicBases.isEmpty() && polymorphicSubtypes.isEmpty()
}
