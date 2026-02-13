// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.config

import com.baseflow.api.middleware.AuditContext
import com.baseflow.services.*
import io.ktor.server.application.*
import org.koin.core.parameter.parametersOf
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
    single { AuditTrailRetrievalService() }

    // ObjectInformatieObjectService - factory with resourceSegment parameter
    factory { (resourceSegment: String) ->
        ObjectInformatieObjectService(resourceSegment)
    }

    // Default instance for objectinformatieobjecten
    single(qualifier = org.koin.core.qualifier.named("objectinformatieobjecten")) {
        ObjectInformatieObjectService("objectinformatieobjecten")
    }

    // Request-scoped services (factory - new instance per request)
    factory { (call: ApplicationCall) ->
        AuditContext(call)
    }

    factory { (call: ApplicationCall) ->
        EnkelvoudigInformatieObjectService(
            storageService = get(),
            applicationConfig = get(),
            openZaakService = get(),
            auditContext = get { parametersOf(call) }
        )
    }
}
