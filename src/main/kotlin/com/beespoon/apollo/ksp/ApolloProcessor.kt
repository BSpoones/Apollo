package com.beespoon.apollo.ksp

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

internal class ApolloProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: ProcessorOptions
) : SymbolProcessor {

    private var invoked = false
    private var errored = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) return emptyList()
        invoked = true

        val origins = mutableSetOf<KSFile>()
        val configs = fileConfigs(resolver, origins)
        val (bases, subtypes) = polymorphics(resolver, origins)
        val namespace = namespace(resolver, origins, hasConfigs = configs.isNotEmpty())

        val model = RegistrarModel(namespace, configs, bases, subtypes)

        if (errored || model.isEmpty) return emptyList()
        write(model, origins)
        return emptyList()
    }

    private fun fileConfigs(resolver: Resolver, origins: MutableSet<KSFile>): List<RegistrarModel.Config> = resolver
        .getSymbolsWithAnnotation(ProcessorOptions.FILE_CONFIG)
        .filterIsInstance<KSClassDeclaration>()
        .distinctBy { it.qualifiedName?.asString() ?: it.simpleName.asString() }
        .mapNotNull { config(it, origins) }
        .toList()

    private fun config(clazz: KSClassDeclaration, origins: MutableSet<KSFile>): RegistrarModel.Config? {
        val className = clazz.qualifiedName?.asString()
        if (className == null || clazz.classKind != ClassKind.CLASS) {
            error("config annotations must target a top-level or nested class", clazz); return null
        }

        if (clazz.typeParameters.isNotEmpty()) {
            error("$className is generic — generic classes can't be registered as configs", clazz); return null
        }

        if (clazz.isAbstract() || Modifier.SEALED in clazz.modifiers) {
            error(
                "$className must be a concrete class — abstract and sealed classes can't be instantiated as a config",
                clazz
            ); return null
        }

        if (!clazz.isReferencable()) {
            error(
                "$className must be public or internal to be referenced from the generated registrar",
                clazz
            ); return null
        }

        if (!clazz.isSerializable()) {
            error("$className must be @Serializable to be a config", clazz); return null
        }

        val annotation = clazz.annotations.firstOrNull { fqcnOf(it) == ProcessorOptions.FILE_CONFIG } ?: return null
        val name = annotation.stringArg("name")
            .orEmpty().ifEmpty { clazz.simpleName.asString().lowercase().removeSuffix("config") }

        val format = annotation.stringArg("format").orEmpty().ifEmpty { "json" }
        val directories = clazz.getAllProperties().mapNotNull { directory(clazz, it) }.toList()

        clazz.containingFile?.let(origins::add)
        return RegistrarModel.Config(className, name, format, directories)
    }

    private fun directory(owner: KSClassDeclaration, property: KSPropertyDeclaration): RegistrarModel.Directory? {
        val annotations = property.annotations + constructorParameterAnnotations(owner, property)
        val annotation = annotations.firstOrNull { fqcnOf(it) == ProcessorOptions.DIRECTORY_CONFIG } ?: return null

        val propertyName = property.simpleName.asString()
        if (!property.isReferencable()) {
            error(
                "directory property '$propertyName' in ${owner.qualifiedName?.asString()} must be public or internal",
                property
            ); return null
        }

        if (!property.isCollectionOrMap()) {
            error(
                "directory property '$propertyName' in ${owner.qualifiedName?.asString()} must be a Collection<*> or Map<String, *>",
                property
            ); return null
        }

        if (property.isMap() && !property.mapKeyIsString()) {
            error(
                "directory property '$propertyName' in ${owner.qualifiedName?.asString()} must be a Map with String keys — keys become element file names",
                property
            ); return null
        }

        val name = annotation.stringArg("name").orEmpty().ifEmpty { propertyName.lowercase() }
        return RegistrarModel.Directory(propertyName, name)
    }

    private fun polymorphics(
        resolver: Resolver,
        origins: MutableSet<KSFile>
    ): Pair<List<String>, Map<String, List<String>>> {
        val bases = resolver.getSymbolsWithAnnotation(ProcessorOptions.POLYMORPHIC)
            .filterIsInstance<KSClassDeclaration>()
            .distinctBy { it.qualifiedName?.asString() ?: it.simpleName.asString() }
            .mapNotNull { base ->
                val name = base.qualifiedName?.asString()
                if (name == null || !base.isReferencable()) {
                    logger.warn(
                        "polymorphic base ${base.simpleName.asString()} isn't visible to generated code — register it at runtime instead",
                        base
                    )
                    return@mapNotNull null
                }
                base.containingFile?.let(origins::add)
                name
            }.toList()

        val subtypes = linkedMapOf<String, MutableList<String>>()
        for (file in resolver.getNewFiles()) {
            for (declaration in file.declarations) collectSubtypes(declaration, subtypes, origins)
        }

        return bases to subtypes
    }

    private fun collectSubtypes(
        declaration: KSDeclaration,
        subtypes: MutableMap<String, MutableList<String>>,
        origins: MutableSet<KSFile>
    ) {
        if (declaration !is KSClassDeclaration) return

        declaration.declarations.forEach { collectSubtypes(it, subtypes, origins) }
        if (!declaration.isConcreteKind() || declaration.isAbstract()) return

        val bases = declaration.getAllSuperTypes().map { it.declaration }
            .filter { supertype -> supertype.annotations.any { fqcnOf(it) == ProcessorOptions.POLYMORPHIC } }
            .mapNotNull { it.qualifiedName?.asString() }.toList()
        if (bases.isEmpty()) return

        val subtypeName = declaration.qualifiedName?.asString()
        if (subtypeName == null || !declaration.isReferencable()) {
            logger.warn(
                "polymorphic subtype ${declaration.simpleName.asString()} isn't visible to generated code — register it at runtime instead",
                declaration
            )
            return
        }

        if (!declaration.isSerializable()) {
            logger.warn(
                "polymorphic subtype $subtypeName has no @Serializable serialiser and will not (de)serialise",
                declaration
            )
            return
        }

        declaration.containingFile?.let(origins::add)
        for (base in bases) subtypes.getOrPut(base) { mutableListOf() }.add(subtypeName)
    }

    private fun namespace(resolver: Resolver, origins: MutableSet<KSFile>, hasConfigs: Boolean): String? {
        val resolved = options.namespace ?: annotatedNamespace(resolver, origins) ?: options.defaultNamespace
        if (resolved == null && options.requireNamespace && hasConfigs) {
            error(
                "apollo.requireNamespace is set but no namespace resolved — annotate the module's host class with " + "${options.namespaceAnnotation ?: "the metadata annotation"} or set apollo.namespace",
                null
            )
        }
        return resolved
    }

    private fun annotatedNamespace(resolver: Resolver, origins: MutableSet<KSFile>): String? {
        val fqcn = options.namespaceAnnotation ?: return null
        val holders = resolver.getSymbolsWithAnnotation(fqcn).filterIsInstance<KSClassDeclaration>().toList()
        if (holders.isEmpty()) return null
        if (holders.size > 1) {
            error(
                "ambiguous namespace: ${holders.joinToString { it.simpleName.asString() }} all carry $fqcn — keep one per module or set apollo.namespace",
                holders[1]
            )
            return null
        }

        val holder = holders.single()
        val annotation = holder.annotations.first { fqcnOf(it) == fqcn }
        val value = annotation.stringArg(options.namespaceParameter)
        if (value.isNullOrBlank()) {
            error(
                "$fqcn on ${holder.simpleName.asString()} carries no string parameter '${options.namespaceParameter}' to namespace by",
                holder
            )
            return null
        }

        holder.containingFile?.let(origins::add)
        return value
    }

    private fun write(model: RegistrarModel, origins: Set<KSFile>) {
        val className = RegistrarEmitter.className(model)
        val dependencies = Dependencies(aggregating = true, *origins.toTypedArray())

        codeGenerator.createNewFile(dependencies, RegistrarEmitter.PACKAGE, className)
            .use { it.write(RegistrarEmitter.emit(model, className).toByteArray()) }
        codeGenerator.createNewFileByPath(dependencies, RegistrarEmitter.SERVICE_FILE, extensionName = "")
            .use { it.write("${RegistrarEmitter.PACKAGE}.$className\n".toByteArray()) }
    }

    private fun constructorParameterAnnotations(
        owner: KSClassDeclaration,
        property: KSPropertyDeclaration
    ): Sequence<KSAnnotation> =
        owner.primaryConstructor?.parameters?.firstOrNull { it.name?.asString() == property.simpleName.asString() }?.annotations
            ?: emptySequence()

    private fun KSPropertyDeclaration.isCollectionOrMap(): Boolean {
        val declaration = type.resolve().declaration as? KSClassDeclaration ?: return false
        return (sequenceOf(declaration) + declaration.getAllSuperTypes()
            .map { it.declaration }).mapNotNull { it.qualifiedName?.asString() }
            .any { it == "kotlin.collections.Collection" || it == "kotlin.collections.Map" }
    }

    private fun KSPropertyDeclaration.isMap(): Boolean {
        val declaration = type.resolve().declaration as? KSClassDeclaration ?: return false
        return (sequenceOf(declaration) + declaration.getAllSuperTypes()
            .map { it.declaration }).mapNotNull { it.qualifiedName?.asString() }
            .any { it == "kotlin.collections.Map" }
    }

    // Unresolvable key type defaults to true — fail loud only on a key proven non-String.
    private fun KSPropertyDeclaration.mapKeyIsString(): Boolean {
        val keyType = type.resolve().arguments.firstOrNull()?.type?.resolve() ?: return true
        return keyType.declaration.qualifiedName?.asString() == "kotlin.String"
    }

    private fun KSClassDeclaration.isSerializable(): Boolean =
        annotations.any { fqcnOf(it) == "kotlinx.serialization.Serializable" }

    private fun KSDeclaration.isReferencable(): Boolean =
        getVisibility() == Visibility.PUBLIC || getVisibility() == Visibility.INTERNAL

    private fun KSClassDeclaration.isConcreteKind(): Boolean =
        classKind == ClassKind.CLASS || classKind == ClassKind.OBJECT || classKind == ClassKind.ENUM_CLASS

    private fun KSAnnotation.stringArg(name: String): String? =
        arguments.firstOrNull { it.name?.asString() == name }?.value as? String

    private fun fqcnOf(annotation: KSAnnotation): String? =
        annotation.annotationType.resolve().declaration.qualifiedName?.asString()

    private fun error(message: String, node: KSNode?) {
        errored = true; logger.error(message, node)
    }
}
