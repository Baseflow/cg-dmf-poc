// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.config

import com.baseflow.services.AuditTrailService
import com.baseflow.services.ObjectInformatieObjectService
import com.baseflow.services.OpenZaakService
import com.baseflow.services.StorageService
import org.koin.dsl.module

/**
 * Koin dependency injection module
 * Defines all services and their dependencies
 */
val appModule = module {
    // Configuration objects (singletons)
    single { ApplicationConfig }
    single { OpenZaakConfig.fromEnv() }

    // Services (singletons)
    single { StorageService() }
    single { OpenZaakService(get()) }
    single { AuditTrailService() }

    // ObjectInformatieObjectService - factory with resourceSegment parameter
    factory { (resourceSegment: String) ->
        ObjectInformatieObjectService(resourceSegment)
    }

    // Default instance for objectinformatieobjecten
    single(qualifier = org.koin.core.qualifier.named("objectinformatieobjecten")) {
        ObjectInformatieObjectService("objectinformatieobjecten")
    }

//    factory { (call: ApplicationCall) ->
//        EnkelvoudigInformatieObjectService(
//            storageService = get(),
//            applicationConfig = get(),
//            openZaakService = get(),
//            auditTrailService = get(),
//            auditContext = get { parametersOf(call) }
//        )
//    }
}
