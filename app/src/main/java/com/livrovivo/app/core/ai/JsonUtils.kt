package com.livrovivo.app.core.ai

import com.livrovivo.app.data.model.appJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

object JsonUtils {

    /**
     * Extrai o primeiro objeto JSON de uma resposta de modelo, tolerando cercas ```json,
     * texto antes/depois e aspas tipográficas fora das strings.
     */
    fun extractJsonObject(raw: String): JsonObject? {
        val text = raw.trim()
            .removePrefix("﻿")
            .replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```\\s*$"), "")
            .trim()

        parseObject(text)?.let { return it }

        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return parseObject(text.substring(start, i + 1))
                }
            }
        }
        return null
    }

    private fun parseObject(text: String): JsonObject? = try {
        appJson.parseToJsonElement(text) as? JsonObject
    } catch (_: Exception) {
        null
    }

    fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.contentOrNull != "null" }?.contentOrNull

    fun JsonObject.bool(key: String): Boolean? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.booleanOrNull ?: primitive.contentOrNull?.lowercase()?.toBooleanStrictOrNull()
    }

    fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

    fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    fun JsonElement.asObjectOrNull(): JsonObject? = try {
        jsonObject
    } catch (_: Exception) {
        null
    }

    fun JsonArray.strings(): List<String> = mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
}
