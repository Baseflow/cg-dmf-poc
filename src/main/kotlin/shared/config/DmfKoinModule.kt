// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.config

import com.baseflow.shared.api.middleware.AuditContext
import com.baseflow.shared.services.AuditTrailService
import com.baseflow.shared.services.BestandsDeelService
import com.baseflow.shared.services.CatalogusService
import com.baseflow.shared.services.EnkelvoudigInformatieObjectService
import com.baseflow.shared.services.HealthCheckService
import com.baseflow.shared.services.NotificationService
import com.baseflow.shared.services.ObjectInformatieObjectService
import com.baseflow.shared.services.StorageService
import com.baseflow.shared.services.WopiSlatService
import com.baseflow.wopi.services.WopiDocumentService
import org.koin.dsl.module
import org.koin.module.requestScope

/**
 * Koin dependency injection module
 * Defines all services and their dependencies
 */
val dmfKoinModule = module {
    // Configuration objects (singletons)
    single { ApplicationConfig }
    single { CatalogusService() }
    single { HealthCheckService() }
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
