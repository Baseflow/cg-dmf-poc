// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.config

import com.baseflow.api.middleware.AuditContext
import com.baseflow.api.wopi.services.WopiDocumentService
import com.baseflow.services.AuditTrailService
import com.baseflow.services.BestandsDeelService
import com.baseflow.services.CatalogusService
import com.baseflow.services.EnkelvoudigInformatieObjectService
import com.baseflow.services.HealthCheckService
import com.baseflow.services.NotificationService
import com.baseflow.services.ObjectInformatieObjectService
import com.baseflow.services.StorageService
import com.baseflow.services.WopiSlatService
import org.koin.dsl.module
import org.koin.module.requestScope

/**
 * Koin dependency injection module
 * Defines all services and their dependencies
 */
val dmfKoinModule = module {
    // Configuration objects (singletons)
    single { ApplicationConfig }
    single { CatalogusService(get()) }
    single { HealthCheckService() }
    single { OpenZaakConfig.fromEnv() }
    single { StorageService() }
    single { WopiConfig.fromEnv() }

    requestScope {
        scoped { AuditContext() }
        scoped { AuditTrailService(get()) }
        scoped { BestandsDeelService(BestandsDeelConfig.Default) }
        scoped { EnkelvoudigInformatieObjectService(get(), get(), get(), get(), get(), get()) }
        scoped { NotificationService(get()) }
        scoped { params -> ObjectInformatieObjectService(params.get(), get(), get()) }
        scoped { WopiDocumentService(get(), get()) }
        scoped { params -> WopiSlatService(params.get(), params.get()) }
    }
}
