package com.beespoon.apollo.format

import java.util.concurrent.ConcurrentHashMap

internal class FormatRegistry {
    private val codecs = ConcurrentHashMap<String, FormatCodec>()
    fun register(extension: String, codec: FormatCodec) {
        codecs[extension] = codec
    }

    operator fun get(extension: String): FormatCodec = codecs[extension]
        ?: error("No codec registered for format '$extension'. Register one with codec(\"$extension\", ...) in the Apollo builder.")

    operator fun contains(extension: String): Boolean = codecs.containsKey(extension)
}
