// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.wopi.web

import com.baseflow.shared.api.WOPI_API_BASE_PATH
import com.baseflow.shared.api.WOPI_WEB_BASE_PATH
import com.baseflow.shared.api.middleware.UnauthorizedException
import com.baseflow.shared.services.models.SlatPayload
import com.baseflow.wopi.shared.middleware.WopiFileIdPlugin
import com.baseflow.wopi.shared.middleware.WopiSlatAuthPlugin
import com.baseflow.wopi.shared.middleware.WopiSlatPayloadKey
import com.baseflow.wopi.shared.tooling.getAccessToken
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.origin
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.host
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi
import java.net.URI
import java.net.URISyntaxException
import java.time.Instant

@OptIn(ExperimentalKtorApi::class)
fun Route.wopiWebRoutes() {
    route("$WOPI_WEB_BASE_PATH/files") {
        install(WopiFileIdPlugin)
        install(WopiSlatAuthPlugin)

        get("/{file_id}") {
            val token: String =
                call.getAccessToken() ?: throw UnauthorizedException("Missing access_token query parameter or Authorization header.")
            val slatPayload: SlatPayload = call.attributes[WopiSlatPayloadKey]
            val accessTokenTtl: Long = Instant.now().toEpochMilli() + (slatPayload.expiresAt * 1000)
            val wopiClientUrl: String = call.request.queryParameters["wopi_client"] ?: throw BadRequestException("Missing the \"wopi_client\" query parameter")
            val wopiClientUri: URI
            try {
                wopiClientUri = URI(wopiClientUrl)
            } catch (e: URISyntaxException) {
                throw BadRequestException("Invalid wopi_client URL: $wopiClientUrl", e)
            }

            val wopiSrcUrl: URI = buildWopiSrcUrl(call.request, slatPayload)

            call.respondHtml {
                wopiHostPage(wopiClientUri, wopiSrcUrl, token, accessTokenTtl)
            }
        }
    }
}

private fun buildWopiSrcUrl(call: ApplicationRequest, slatPayload: SlatPayload): URI {
    val scheme: String = call.origin.scheme
    val host: String = call.origin.serverHost
    val port: Int = call.origin.serverPort

    return URI("$scheme://$host:$port/$WOPI_API_BASE_PATH/files/${slatPayload.fileId}")
}
