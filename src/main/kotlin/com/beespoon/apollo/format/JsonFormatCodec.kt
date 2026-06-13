package com.beespoon.apollo.format

import kotlinx.serialization.json.*

public class JsonFormatCodec(private val json: Json) : FormatCodec {
    override fun parse(text: String): JsonElement = json.parseToJsonElement(text)
    override fun write(element: JsonElement): String = json.encodeToString(JsonElement.serializer(), element)
}
