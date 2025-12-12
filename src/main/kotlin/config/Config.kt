// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.config

/**
 * Base interface for configuration providers.
 * All configuration objects must expose a way to print their effective configuration
 * (with sensitive values masked where applicable).
 */
internal interface Config {
    fun printConfig()
}