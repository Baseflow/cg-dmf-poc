// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.documenten.api.routes

import com.baseflow.documenten.api.documentenApiModule
import com.baseflow.settings.api.settingsModule
import com.baseflow.shared.api.apiJsonConfig
import com.baseflow.shared.api.middleware.AuditContext
import com.baseflow.shared.config.ApplicationConfig
import com.baseflow.shared.config.BestandsDeelConfig
import com.baseflow.shared.services.AuditTrailService
import com.baseflow.shared.services.BestandsDeelService
import com.baseflow.shared.services.CatalogusService
import com.baseflow.shared.services.EnkelvoudigInformatieObjectService
import com.baseflow.shared.services.NotificationService
import com.baseflow.shared.services.ObjectInformatieObjectService
import com.baseflow.shared.services.StorageService
import com.baseflow.shared.services.WopiSlatService
import com.baseflow.shared.tooling.AllTables
import com.baseflow.wopi.services.WopiDocumentService
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
import java.io.InputStream
import java.io.OutputStream
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
     * can be triggered with small file sizes instead of relying on the default 300 MB threshold.
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
            every { it.uploadFile(any<String>(), any<InputStream>(), any<Long>(), anyNullable()) } answers {
                secondArg<InputStream>().copyTo(OutputStream.nullOutputStream())
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
                    single { CatalogusService() }
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
