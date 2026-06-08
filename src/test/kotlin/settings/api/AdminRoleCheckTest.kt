// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.settings.api

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.baseflow.documenten.api.routes.TestBase
import com.baseflow.shared.api.apiJsonConfig
import com.baseflow.shared.api.middleware.AuditContext
import com.baseflow.shared.api.middleware.configureStatusPages
import com.baseflow.shared.config.ApplicationConfig
import com.baseflow.shared.config.BestandsDeelConfig
import com.baseflow.shared.config.OpenZaakConfig
import com.baseflow.shared.services.AuditTrailService
import com.baseflow.shared.services.BestandsDeelService
import com.baseflow.shared.services.BlobStorageRegistrar
import com.baseflow.shared.services.CatalogusService
import com.baseflow.shared.services.EnkelvoudigInformatieObjectService
import com.baseflow.shared.services.NotificationService
import com.baseflow.shared.services.ObjectInformatieObjectService
import com.baseflow.shared.services.StorageService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.module.requestScope
import java.util.concurrent.CompletableFuture
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests the settings role-check plugin in `SettingsRoutes.kt`.
 *
 * The role-check runs on the [AuthenticationChecked] hook and verifies that the
 * authenticated principal carries the required role (`dmf-admin` by default).
 * The role can appear in either:
 * - `realm_access.roles` (Keycloak JWT shape), or
 * - `resource_access.<client_id>.roles` (Keycloak client-role shape), or
 * - a top-level `roles` claim (ZGW JWT shape).
 */
class AdminRoleCheckTest : TestBase("admin_role_check") {

    companion object {
        private const val ADMIN_ROLE = "dmf-admin"
        private const val OTHER_ROLE = "some-other-role"
        private const val JWT_SECRET = "test-secret-key-for-testing-only"
        private const val JWT_ISSUER = "test-issuer"
        private const val OIDC_CLIENT_ID = "dmf-api"
    }

    @BeforeTest
    override fun beforeTest() {
        super.beforeTest()
        BlobStorageRegistrar.resetForTesting()
    }

    @AfterTest
    fun afterTest() {
        BlobStorageRegistrar.resetForTesting()
    }

    /** Builds a JWT with the given roles in `realm_access.roles` (Keycloak shape). */
    private fun tokenWithKeycloakRoles(vararg roles: String): String = JWT.create()
        .withIssuer(JWT_ISSUER)
        .withSubject("testuser")
        .withClaim("preferred_username", "testuser")
        .withClaim("realm_access", mapOf("roles" to roles.toList()))
        .sign(Algorithm.HMAC256(JWT_SECRET))

    /** Builds a JWT with the given roles in the top-level `roles` claim (ZGW shape). */
    private fun tokenWithZgwRoles(vararg roles: String): String = JWT.create()
        .withIssuer(JWT_ISSUER)
        .withSubject("testuser")
        .withClaim("client_id", "gzac")
        .withClaim("username", "testuser")
        .withArrayClaim("roles", roles)
        .sign(Algorithm.HMAC256(JWT_SECRET))

    /** Builds a Keycloak-shaped JWT that wrongly places roles in top-level `roles`. */
    private fun oidcTokenWithTopLevelRoles(vararg roles: String): String = JWT.create()
        .withIssuer(JWT_ISSUER)
        .withSubject("testuser")
        .withClaim("preferred_username", "testuser")
        .withArrayClaim("roles", roles)
        .sign(Algorithm.HMAC256(JWT_SECRET))

    /**
     * Builds a JWT with roles in `resource_access.<client_id>.roles` (Keycloak client-role shape).
     */
    private fun tokenWithResourceAccessRoles(vararg roles: String, clientId: String = OIDC_CLIENT_ID): String = JWT.create()
        .withIssuer(JWT_ISSUER)
        .withSubject("testuser")
        .withClaim("preferred_username", "testuser")
        .withClaim("azp", clientId)
        .withClaim("resource_access", mapOf(clientId to mapOf("roles" to roles.toList())))
        .sign(Algorithm.HMAC256(JWT_SECRET))

    /** Builds a valid JWT with no role claims at all. */
    private fun tokenWithNoRoles(): String = JWT.create()
        .withIssuer(JWT_ISSUER)
        .withSubject("testuser")
        .withClaim("preferred_username", "testuser")
        .sign(Algorithm.HMAC256(JWT_SECRET))

    private fun Application.setupWithAuth() {
        connectDb()

        val mockStorageService = mockk<StorageService>(relaxed = true).also {
            every { it.uploadFile(any<String>(), any<ByteArray>(), anyNullable()) } returns 0L
            every { it.downloadFileTo(any(), any(), anyNullable()) } returns CompletableFuture.completedFuture(null)
        }

        install(Koin) {
            allowOverride(true)
            modules(
                module {
                    single { ApplicationConfig }
                    single { CatalogusService(get()) }
                    single { OpenZaakConfig.fromEnv() }
                    single<StorageService> { mockStorageService }

                    requestScope {
                        scoped { AuditContext() }
                        scoped { AuditTrailService(get()) }
                        scoped { BestandsDeelService(BestandsDeelConfig.Default) }
                        scoped { EnkelvoudigInformatieObjectService(get(), get(), get(), get(), get(), get()) }
                        scoped { NotificationService(get()) }
                        scoped { params -> ObjectInformatieObjectService(params.get(), get(), get()) }
                    }
                },
            )
        }
        install(ContentNegotiation) { json(apiJsonConfig()) }
        configureStatusPages()

        install(Authentication) {
            jwt("auth-jwt") {
                verifier(JWT.require(Algorithm.HMAC256(JWT_SECRET)).withIssuer(JWT_ISSUER).build())
                validate { credential ->
                    if (!credential.payload.getClaim("preferred_username").asString().isNullOrBlank() ||
                        !credential.payload.getClaim("user_id").asString().isNullOrBlank()
                    ) {
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                }
                challenge { _, _ -> call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized) }
            }
            jwt("auth-zgw") {
                verifier(JWT.require(Algorithm.HMAC256(JWT_SECRET)).withIssuer(JWT_ISSUER).build())
                validate { credential ->
                    if (!credential.payload.getClaim("client_id").asString().isNullOrBlank()) {
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                }
                challenge { _, _ -> call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized) }
            }
        }

        settingsModule(useAuthentication = true)
    }

    // ── No / invalid auth ─────────────────────────────────────────────────────

    @Test
    fun `request without Authorization header returns 401`() = testApplication {
        application { setupWithAuth() }

        val response = client.get("/settings/storage-repositories")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `request with an invalid token returns 401`() = testApplication {
        application { setupWithAuth() }

        val response = client.get("/settings/storage-repositories") {
            header(HttpHeaders.Authorization, "Bearer this.is.not.valid")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ── Missing role ──────────────────────────────────────────────────────────

    @Test
    fun `valid token with no role claims returns 403`() = testApplication {
        application { setupWithAuth() }

        val response = client.get("/settings/storage-repositories") {
            header(HttpHeaders.Authorization, "Bearer ${tokenWithNoRoles()}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `valid token with a different realm_access role returns 403`() = testApplication {
        application { setupWithAuth() }

        val response = client.get("/settings/storage-repositories") {
            header(HttpHeaders.Authorization, "Bearer ${tokenWithKeycloakRoles(OTHER_ROLE)}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `valid token with a different top-level role returns 403`() = testApplication {
        application { setupWithAuth() }

        val response = client.get("/settings/storage-repositories") {
            header(HttpHeaders.Authorization, "Bearer ${tokenWithZgwRoles(OTHER_ROLE)}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `oidc token with admin role only in top-level roles returns 403`() = testApplication {
        application { setupWithAuth() }

        val response = client.get("/settings/storage-repositories") {
            header(HttpHeaders.Authorization, "Bearer ${oidcTokenWithTopLevelRoles(ADMIN_ROLE)}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `valid token with a different resource_access role returns 403`() = testApplication {
        application { setupWithAuth() }

        val response = client.get("/settings/storage-repositories") {
            header(HttpHeaders.Authorization, "Bearer ${tokenWithResourceAccessRoles(OTHER_ROLE)}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `403 response body is a problem detail with status 403`() = testApplication {
        application { setupWithAuth() }

        val response = client.get("/settings/storage-repositories") {
            header(HttpHeaders.Authorization, "Bearer ${tokenWithNoRoles()}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(403, json["status"]?.jsonPrimitive?.content?.toInt())
    }

    // ── Authorised: Keycloak realm_access.roles ───────────────────────────────

    @Test
    fun `token with admin role in realm_access returns 200`() = testApplication {
        application { setupWithAuth() }

        val response = client.get("/settings/storage-repositories") {
            header(HttpHeaders.Authorization, "Bearer ${tokenWithKeycloakRoles(ADMIN_ROLE)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `token with admin role alongside other roles in realm_access returns 200`() = testApplication {
        application { setupWithAuth() }

        val response = client.get("/settings/storage-repositories") {
            header(HttpHeaders.Authorization, "Bearer ${tokenWithKeycloakRoles(OTHER_ROLE, ADMIN_ROLE, "yet-another")}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ── Authorised: ZGW top-level roles claim ─────────────────────────────────

    @Test
    fun `token with admin role in top-level roles claim returns 200`() = testApplication {
        application { setupWithAuth() }

        val response = client.get("/settings/storage-repositories") {
            header(HttpHeaders.Authorization, "Bearer ${tokenWithZgwRoles(ADMIN_ROLE)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `token with admin role alongside other roles in top-level roles returns 200`() = testApplication {
        application { setupWithAuth() }

        val response = client.get("/settings/storage-repositories") {
            header(HttpHeaders.Authorization, "Bearer ${tokenWithZgwRoles(OTHER_ROLE, ADMIN_ROLE)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ── Authorised: Keycloak resource_access client roles ──────────────────────

    @Test
    fun `token with admin role in resource_access returns 200`() = testApplication {
        application { setupWithAuth() }

        val response = client.get("/settings/storage-repositories") {
            header(HttpHeaders.Authorization, "Bearer ${tokenWithResourceAccessRoles(ADMIN_ROLE)}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `token with admin role alongside other roles in resource_access returns 200`() = testApplication {
        application { setupWithAuth() }

        val response = client.get("/settings/storage-repositories") {
            header(
                HttpHeaders.Authorization,
                "Bearer ${tokenWithResourceAccessRoles(OTHER_ROLE, ADMIN_ROLE, "yet-another")}",
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ── Role check applies to all sub-routes ─────────────────────────────────

    @Test
    fun `role check applies to POST sub-route`() = testApplication {
        application { setupWithAuth() }

        val response = client.post("/settings/storage-repositories") {
            header(HttpHeaders.Authorization, "Bearer ${tokenWithNoRoles()}")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        // 403 from role check fires before the route handler validates the body
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `role check applies to nested PUT sub-route`() = testApplication {
        application { setupWithAuth() }

        val response = client.put("/settings/storage-repositories/default") {
            header(HttpHeaders.Authorization, "Bearer ${tokenWithNoRoles()}")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
