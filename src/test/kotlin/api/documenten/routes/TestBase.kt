// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.documenten.routes

import com.baseflow.api.apiJsonConfig
import com.baseflow.api.documenten.documentenApiModule
import com.baseflow.api.middleware.AuditContext
import com.baseflow.api.settings.settingsModule
import com.baseflow.api.wopi.services.WopiDocumentService
import com.baseflow.config.ApplicationConfig
import com.baseflow.config.BestandsDeelConfig
import com.baseflow.config.OpenZaakConfig
import com.baseflow.services.AuditTrailService
import com.baseflow.services.BestandsDeelService
import com.baseflow.services.CatalogusService
import com.baseflow.services.EnkelvoudigInformatieObjectService
import com.baseflow.services.NotificationService
import com.baseflow.services.ObjectInformatieObjectService
import com.baseflow.services.StorageService
import com.baseflow.services.WopiSlatService
import com.baseflow.tooling.AllTables
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.*
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.module.requestScope
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.BeforeTest

open class TestBase(dbNamePrefix: String) {
    val dbName = "${dbNamePrefix}_${UUID.randomUUID()}"

    /** Exposed so individual tests can assert on upload/download calls. */
    lateinit var mockStorageService: StorageService
        protected set

    /**
     * Small-chunk config used for route tests so that bestandsdelen behaviour
     * can be triggered with small file sizes instead of relying on the default 4 GB threshold.
     * The trigger size is chosen to be above the default test document size (630 bytes) so that
     * regular create/unlock tests are not affected, while still allowing targeted tests
     * to trigger chunking with sizes just above the trigger.
     */
    val testBestandsDeelConfig = object : BestandsDeelConfig() {
        override val triggerSizeBytes: Long = 1024L
        override val chunkSizeBytes: Long = 512L
    }

    fun connectDb() {
        Database.connect(
            "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = "",
        )
    }

    @BeforeTest
    open fun beforeTest() {
        connectDb()
        transaction {
            AllTables.createMissing()
        }
    }

    fun Application.setup() {
        connectDb()

        mockStorageService = mockk<StorageService>(relaxed = true).also {
            every { it.uploadFile(any<String>(), any<ByteArray>(), anyNullable()) } answers { secondArg<ByteArray>().size.toLong() }
            every { it.uploadFile(any<String>(), any<java.io.InputStream>(), any<Long>(), anyNullable()) } answers {
                secondArg<java.io.InputStream>().copyTo(java.io.OutputStream.nullOutputStream())
                thirdArg<Long>()
            }
            every { it.downloadFileTo(any(), any(), anyNullable()) } returns CompletableFuture.completedFuture(null)
        }

        install(Koin) {
            allowOverride(true)
            // Override the real StorageService (which requires S3) with a no-op mock
            // so route tests never attempt to connect to S3.
            modules(
                module {
                    // Configuration objects (singletons)
                    single { ApplicationConfig }
                    single { CatalogusService(get()) }
                    single { OpenZaakConfig.fromEnv() }
                    single<StorageService> { mockStorageService }

                    requestScope {
                        scoped { AuditContext() }
                        scoped { AuditTrailService(get()) }
                        scoped { BestandsDeelService(testBestandsDeelConfig) }
                        scoped { EnkelvoudigInformatieObjectService(get(), get(), get(), get(), get(), get()) }
                        scoped { NotificationService(get()) }
                        scoped { params -> ObjectInformatieObjectService(params.get(), get(), get()) }
                        scoped { WopiDocumentService(get(), get()) }
                        scoped { params -> WopiSlatService(params.get(), params.get()) }
                    }
                },
            )
        }
        install(ContentNegotiation) {
            json(apiJsonConfig())
        }
        documentenApiModule(useAuthentication = false)
        settingsModule(useAuthentication = false)
    }
}
