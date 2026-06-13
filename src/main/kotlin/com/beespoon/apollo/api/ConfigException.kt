package com.beespoon.apollo.api

import java.io.File
import kotlin.reflect.KType

public sealed class ConfigException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    public class ParseError(public val file: File, cause: Throwable) :
        ConfigException("Failed to parse config file: ${file.absolutePath}", cause)

    public class DeserialisationError(public val type: KType, public val file: File, cause: Throwable) :
        ConfigException("Failed to deserialise ${type.classifier} from ${file.absolutePath}", cause)

    public class InitialisationError(public val type: KType, cause: Throwable) :
        ConfigException("Failed to initialise ${type.classifier}: ${cause.message}", cause)
}
