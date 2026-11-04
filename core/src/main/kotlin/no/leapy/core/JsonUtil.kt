package no.leapy.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal val LenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun parseJson(s: String): JsonElement = LenientJson.parseToJsonElement(s)

val JsonElement?.obj: JsonObject? get() = this as? JsonObject
val JsonElement?.arr: JsonArray? get() = this as? JsonArray
val JsonElement?.str: String? get() = (this as? JsonPrimitive)?.takeIf { this !is JsonNull }?.content
val JsonElement?.dbl: Double? get() = str?.trim()?.toDoubleOrNull()
val JsonElement?.int: Int? get() = dbl?.let { if (it.isNaN()) null else it.toInt() }
val JsonElement?.long: Long? get() = str?.trim()?.toLongOrNull() ?: dbl?.toLong()
