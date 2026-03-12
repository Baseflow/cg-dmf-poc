// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.config

import org.koin.dsl.module

/**
 * Koin dependency injection module
 * Defines all services and their dependencies
 */
val appModule = module {
    // Configuration objects (singletons)
    single { ApplicationConfig }
    single { OpenZaakConfig.fromEnv() }
}
