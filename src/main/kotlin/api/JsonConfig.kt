// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder

/**
 * Central JSON configuration for the API to ensure consistent serializers across app and tests.
 *
 * @param configure Lambda to register contextual serializers for models that need URL augmentation
 *                  or other custom serialization behavior.
 *
 * Example usage:
 * ```
 * apiJsonConfig {
 *     contextual(
 *         EnkelvoudigInformatieObjectResponse::class,
 *         UrlAugmentingSerializer(
 *             EnkelvoudigInformatieObjectResponse.serializer(),
 *             resourceSegment = "enkelvoudiginformatieobjecten",
 *             absolute = true
 *         )
 *     )
 * }
 * ```
 */
fun apiJsonConfig (
    configure: SerializersModuleBuilder.() -> Unit = {}
): Json = Json {
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
        configure()
    }
}
