package com.beespoon.apollo.format

import kotlinx.serialization.json.JsonElement

public interface FormatCodec {
    public fun parse(text: String): JsonElement
    public fun write(element: JsonElement): String
}
