// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.middleware.AuditContext
import com.baseflow.api.models.getResourceSegment
import com.baseflow.config.JwtTokenProvider
import com.baseflow.config.NotificationConfig
import com.baseflow.config.RequestScope
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Payload for creating a kanaal in the Open Notificaties API.
 *
 * @property naam The name of the notification channel.
 * @property documentatieLink URL to the documentation for this channel.
 * @property filters List of filter attribute names supported by this channel.
 */
@Serializable
data class KanaalPayload(val naam: String, val documentatieLink: String = "", val filters: List<String> = emptyList())

/**
 * Notification action types that map to HTTP methods.
 * These are the standard actions as defined by the Documenten API.
 */
enum class NotificationAction(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    PARTIAL_UPDATE("partial_update"),
    DESTROY("destroy"),
}

/**
 * Maps HTTP methods to notification actions.
 * GET/HEAD/LIST operations don't trigger notifications.
 */
private val httpMethodToNotificationAction = mapOf(
    HttpMethod.Post to NotificationAction.CREATE,
    HttpMethod.Put to NotificationAction.UPDATE,
    HttpMethod.Patch to NotificationAction.PARTIAL_UPDATE,
    HttpMethod.Delete to NotificationAction.DESTROY,
)

/**
 * The notification message payload as defined by the Open Notificaties API.
 *
 * @property kanaal The name of the channel (KANAAL.naam) where the message should be published.
 * @property source The identifier of the origin of the notification.
 * @property hoofdObject URL reference to the main object of the publishing API related to the resource.
 * @property resource The resource name that the notification is about.
 * @property resourceUrl URL reference to the resource of the publishing API.
 * @property actie The action performed by the publishing API.
 * @property aanmaakdatum Date and time when the action took place.
 * @property kenmerken Map of characteristics (key/value) of the notification.
 */
@Serializable
data class NotificationMessage(
    val kanaal: String,
    val source: String,
    val hoofdObject: String,
    val resource: String,
    val resourceUrl: String,
    val actie: String,
    val aanmaakdatum: String,
    val kenmerken: Map<String, String>? = null,
)

/**
 * Service responsible for sending notifications to the Open Notificaties API.
 * This service is request-scoped and works similarly to AuditTrailService.
 *
 * Notifications are sent asynchronously after a successful mutation (create, update, delete)
 * to avoid blocking the response to the client.
 */
@OptIn(ExperimentalTime::class)
@Scope(RequestScope::class)
@Scoped
class NotificationService(private val context: AuditContext) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NotificationService::class.java)

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        private val httpClient = HttpClient(CIO) {
            expectSuccess = false
        }

        /**
         * Ensures that the notification kanaal exists in the Open Notificaties API.
         * If the kanaal doesn't exist, it will be created.
         * This should be called during application startup.
         *
         * @return true if the kanaal exists or was created successfully, false otherwise.
         */
        suspend fun ensureKanaalExists(): Boolean {
            if (!NotificationConfig.isEnabled) {
                logger.debug("Notifications are disabled, skipping kanaal check")
                return false
            }

            val url = NotificationConfig.url ?: return false
            val clientId = NotificationConfig.clientId ?: return false
            val clientSecret = NotificationConfig.clientSecret ?: return false
            val token = JwtTokenProvider.generate(clientId, clientSecret)
            val kanaalName = NotificationConfig.kanaal

            try {
                // First, check if the kanaal already exists
                val checkResponse = httpClient.get("$url/kanaal") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(token)
                    parameter("naam", kanaalName)
                }

                if (checkResponse.status.isSuccess()) {
                    val responseBody = checkResponse.bodyAsText()
                    try {
                        val kanaalList = json.decodeFromString<List<Kanaal>>(responseBody)
                        val kanaalExists = kanaalList.any { it.naam == kanaalName }
                        if (kanaalExists) {
                            logger.info("Kanaal '{}' already exists", kanaalName)
                            return true
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to parse kanaal response: {}", e.message)
                    }
                }

                // Kanaal doesn't exist, create it
                logger.info("Creating kanaal '{}'", kanaalName)

                val payload = KanaalPayload(
                    naam = kanaalName,
                    filters = listOf("bronorganisatie", "informatieobjecttype", "vertrouwelijkheidaanduiding"),
                )

                val createResponse = httpClient.post("$url/kanaal") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(token)
                    setBody(json.encodeToString(payload))
                }

                return if (createResponse.status.isSuccess()) {
                    logger.info("Kanaal '{}' created successfully", kanaalName)
                    true
                } else {
                    val errorBody = createResponse.bodyAsText()
                    logger.warn(
                        "Failed to create kanaal '{}': status={}, body={}",
                        kanaalName,
                        createResponse.status,
                        errorBody,
                    )
                    false
                }
            } catch (e: Exception) {
                logger.error("Error ensuring kanaal '{}' exists: {}", kanaalName, e.message)
                return false
            }
        }
    }

    /**
     * Sends a notification for the current request context.
     * This method checks if notifications are enabled and if the request
     * resulted in a mutation that should trigger a notification.
     *
     * @param call The current pipeline call containing request information.
     */
    fun send(call: PipelineCall) {
        if (!NotificationConfig.isEnabled) {
            logger.debug("Notifications are disabled, skipping notification")
            return
        }

        val method = call.request.httpMethod
        val action = httpMethodToNotificationAction[method]

        // Only send notifications for mutation operations
        if (action == null) {
            logger.debug("No notification action for HTTP method: {}", method)
            return
        }

        val entity = context.newValue ?: context.oldValue
        if (entity == null) {
            logger.debug("No entity captured in context, skipping notification")
            return
        }

        val resourceSegment = entity.getResourceSegment().value
        val resourceUrl = ApiUrlBuilder.absolute(resourceSegment, entity.id.toString())
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val request = context.sourceRequest

        val message = NotificationMessage(
            kanaal = NotificationConfig.kanaal,
            source = NotificationConfig.source,
            hoofdObject = resourceUrl,
            resource = resourceSegment,
            resourceUrl = resourceUrl,
            actie = action.value,
            aanmaakdatum = now.toString(),
            kenmerken = mapOf(
                "bronorganisatie" to request?.bronOrganisatie.orEmpty(),
                "informatieobjecttype" to request?.informatieobject_type.orEmpty(),
                "vertrouwelijkheidaanduiding" to request?.vertrouwlijkheidsAanduiding.orEmpty(),
            ),
        )

        // Send notification asynchronously to not block the response
        call.application.launch(Dispatchers.IO) {
            sendNotification(message)
        }
    }

    /**
     * Actually sends the notification to the Open Notificaties API.
     */
    private suspend fun sendNotification(message: NotificationMessage) {
        val url = NotificationConfig.url ?: return
        val clientId = NotificationConfig.clientId ?: return
        val clientSecret = NotificationConfig.clientSecret ?: return
        val token = JwtTokenProvider.generate(clientId, clientSecret)

        try {
            logger.info(
                "Sending notification: kanaal={}, resource={}, actie={}, resourceUrl={}",
                message.kanaal,
                message.resource,
                message.actie,
                message.resourceUrl,
            )

            val response = httpClient.post("$url/notificaties") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                setBody(json.encodeToString(message))
            }

            if (response.status.isSuccess()) {
                logger.info("Notification sent successfully: {}", response.status)
            } else {
                // Get message from response
                val errorBody = response.bodyAsText()
                logger.warn(
                    "Failed to send notification: status={}, resourceUrl={}, body={}",
                    response.status,
                    message.resourceUrl,
                    errorBody,
                )
            }
        } catch (e: Exception) {
            logger.error("Error sending notification for resourceUrl={}: {}", message.resourceUrl, e.message)
        }
    }
}

@Serializable
data class Kanaal(val url: String, val naam: String, val documentatieLink: String? = null, val filters: List<String>? = null)
