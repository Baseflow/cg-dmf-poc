// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.api.serialization

import com.baseflow.api.ApiUrlBuilder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonTransformingSerializer

/**
 * Generic transforming serializer that injects a computed `url` field
 * into the serialized JSON based on the presence of an `id` field and
 * the configured resource segment.
 */
open class UrlAugmentingSerializer<T : Any>(
    private val base: KSerializer<T>,
    private val resourceSegment: String,
    private val absolute: Boolean = false,
    private val idFieldName: String = "id",
    private val urlFieldName: String = "url",
) : JsonTransformingSerializer<T>(base) {

    override fun transformSerialize(element: JsonElement): JsonElement {
        val obj = element as? JsonObject ?: return element
        val id = obj[idFieldName]?.jsonPrimitive?.contentOrNull ?: return element
        val url = if (absolute) ApiUrlBuilder.absolute(resourceSegment, id)
        else ApiUrlBuilder.path(resourceSegment, id)
        val map = obj.toMutableMap()
        map[urlFieldName] = JsonPrimitive(url)
        return JsonObject(map)
    }
}
