// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.api.middleware

/**
 * Annotation to mark routes that require specific JWT scopes.
 *
 * Usage:
 * ```
 * @RequireScope("documenten:read")
 * get { ... }
 * ```
 *
 * @param scopes One or more scopes that are required. If multiple scopes are provided,
 *               the user must have ALL of them (AND logic).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireScope(vararg val scopes: String)
