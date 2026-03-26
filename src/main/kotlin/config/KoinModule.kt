// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.config

import com.baseflow.services.AzureStorageService
import com.baseflow.services.IStorageService
import com.baseflow.services.S3StorageService
import org.koin.dsl.module

/**
 * Koin dependency injection module
 * Defines all services and their dependencies
 */
val appModule = module {
    // Configuration objects (singletons)
    single { ApplicationConfig }
    single { OpenZaakConfig.fromEnv() }

    // Storage backend — selected via STORAGE_BACKEND env var (default: s3)
    single<IStorageService> {
        when (StorageConfig.backend) {
            StorageConfig.Backend.AZURE -> AzureStorageService()
            StorageConfig.Backend.S3 -> S3StorageService(get())
        }
    }
}
