package me.kafuuneko.rpclient.libs.llm.catalog.adapter

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogInvalidResponseException

internal fun parseCatalogJsonObject(raw: String): JsonObject {
    return runCatching { JsonParser.parseString(raw).asJsonObject }
        .getOrElse { throw LLMModelCatalogInvalidResponseException() }
}

internal fun JsonObject.stringOrNull(name: String): String? {
    val element = get(name)?.takeUnless { it.isJsonNull } ?: return null
    return runCatching { element.asString.trim() }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}

internal fun JsonObject.positiveIntOrNull(name: String): Int? {
    val element = get(name)?.takeUnless { it.isJsonNull } ?: return null
    return runCatching { element.asInt }
        .getOrNull()
        ?.takeIf { it > 0 }
}

internal fun JsonObject.arrayOrNull(name: String): JsonArray? {
    val element = get(name)?.takeUnless { it.isJsonNull } ?: return null
    return element.takeIf { it.isJsonArray }?.asJsonArray
}

internal fun JsonObject.objectOrNull(name: String): JsonObject? {
    val element = get(name)?.takeUnless { it.isJsonNull } ?: return null
    return element.takeIf { it.isJsonObject }?.asJsonObject
}

internal fun JsonArray.toStringSet(): Set<String> {
    return mapNotNullTo(linkedSetOf()) { element ->
        runCatching { element.asString.trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }
}

internal fun JsonArray.containsString(value: String): Boolean {
    return any { element ->
        runCatching { element.asString == value }.getOrDefault(false)
    }
}
