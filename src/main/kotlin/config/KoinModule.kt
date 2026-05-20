// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.config

import com.baseflow.api.middleware.AuditContext
import com.baseflow.services.AuditTrailService
import com.baseflow.services.BestandsDeelService
import com.baseflow.services.CatalogusService
import com.baseflow.services.EnkelvoudigInformatieObjectService
import com.baseflow.services.NotificationService
import com.baseflow.services.ObjectInformatieObjectService
import com.baseflow.services.StorageService
import com.baseflow.services.WopiSlatService
import org.koin.dsl.module

/**
 * Koin dependency injection module
 * Defines all services and their dependencies
 */
val koinModule = module {
    // Configuration objects (singletons)
    single { ApplicationConfig }
    single { OpenZaakConfig.fromEnv() }
    single { StorageService(S3ClientFactory()) }
    factory { AuditContext() }
    factory { AuditTrailService(get()) }
    factory { BestandsDeelService(BestandsDeelConfig.Default) }
    factory { CatalogusService(get()) }
    factory { EnkelvoudigInformatieObjectService(get(), get(), get(), get(), get(), get()) }
    factory { NotificationService(get()) }
    factory { ObjectInformatieObjectService(get(), get(), get()) }
    factory { WopiSlatService(get(), get()) }
}
