// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.documenten.api.routes

import com.baseflow.shared.api.ApiUrlBuilder
import com.baseflow.shared.api.DOCUMENTEN_API_VERSION
import com.baseflow.shared.api.middleware.ApiVersionHeader
import com.baseflow.shared.api.models.*
import com.baseflow.shared.entities.EIORecordEntity
import com.baseflow.shared.entities.latestVersion
import com.baseflow.shared.services.EnkelvoudigInformatieObjectService
import com.baseflow.shared.services.models.DeleteResult
import com.baseflow.shared.services.models.EIOOrdering
import com.baseflow.shared.services.models.LockPayload
import com.baseflow.shared.services.models.LockResult
import com.baseflow.shared.services.models.QueryEnkelvoudigeInformatieObjectenFilter
import com.baseflow.shared.services.models.UnlockResult
import io.ktor.http.*
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.ktor.plugin.scope
import java.util.*
import kotlin.time.Instant

/**
 * Routes for EnkelvoudigInformatieObjecten (Single Information Objects).
 */

val RESOURCE_SEGMENT = ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN.value

@OptIn(ExperimentalKtorApi::class)
fun Route.enkelvoudigInformatieObjectenRoutes() {
    // Ensure API-version header is added for all responses under this subtree,
    // including tests that don't install the plugin at the parent route.
    install(ApiVersionHeader) { version = DOCUMENTEN_API_VERSION }

    /**
     * Alle (ENKELVOUDIGe) INFORMATIEOBJECTen opvragen.
     *
     * Deze lijst kan gefilterd worden met query-string parameters.
     * De objecten bevatten metadata over de documenten en de downloadlink (`inhoud`) naar de binary data.
     * Alleen de laatste versie van elk ENKELVOUDIGINFORMATIEOBJECT wordt getoond.
     *
     * Query parameters:
     *   - `bronorganisatie`: Filter op RSIN van de bronorganisatie.
     *   - `identificatie`: Filter op identificatie.
     *   - `trefwoorden`: Filter op trefwoorden (alle opgegeven trefwoorden moeten aanwezig zijn).
     *   - `expand`: Velden om te expanderen.
     *   - `page`: Paginanummer.
     *   - `pageSize`: Aantal resultaten per pagina.
     *   - `informatieobjecttype`: EXPERIMENTEEL: Filter op URL-referentie naar het informatieobjecttype.
     *   - `vertrouwelijkheidaanduiding`: EXPERIMENTEEL: Filter op vertrouwelijkheidaanduiding.
     *   - `titel`: EXPERIMENTEEL: Filter op titel (hoofdletterongevoelig, bevat).
     *   - `auteur`: EXPERIMENTEEL: Filter op auteur (hoofdletterongevoelig, bevat).
     *   - `status`: EXPERIMENTEEL: Filter op status.
     *   - `beschrijving`: EXPERIMENTEEL: Filter op beschrijving (hoofdletterongevoelig, bevat).
     *   - `trefwoorden__overlap`: EXPERIMENTEEL: Filter op trefwoorden (overlap).
     *   - `locked`: EXPERIMENTEEL: Filter op vergrendeld/ontgrendeld.
     *   - `creatiedatum__gte`: EXPERIMENTEEL: Filter op creatiedatum (groter of gelijk, date).
     *   - `creatiedatum__lte`: EXPERIMENTEEL: Filter op creatiedatum (kleiner of gelijk, date).
     *   - `registratiedatum__gte`: EXPERIMENTEEL: Filter op beginRegistratie (groter of gelijk, date-time).
     *   - `registratiedatum__lte`: EXPERIMENTEEL: Filter op beginRegistratie (kleiner of gelijk, date-time).
     *   - `ordering`: EXPERIMENTEEL: Sortering.
     *   - `objectinformatieobjecten__object`: EXPERIMENTEEL: Filter op URL-referentie naar het gerelateerde object.
     *   - `objectinformatieobjecten__objectType`: EXPERIMENTEEL: Filter op objecttype van het gerelateerde object.
     *
     * Responses:
     *   - 200 Lijst van (ENKELVOUDIGe) INFORMATIEOBJECTen.
     *   - 400 Bad request.
     *   - 401 Unauthorized.
     *   - 403 Forbidden.
     *   - 406 Not acceptable.
     *   - 409 Conflict.
     *   - 410 Gone.
     *   - 415 Unsupported media type.
     *   - 429 Too many requests.
     *   - 500 Internal server error.
     *
     * @tag EnkelvoudigInformatieObjecten
     */
    get { list() }
        .describe {
            operationId = "enkelvoudiginformatieobjecten_list"
            tag("enkelvoudiginformatieobjecten")
            summary = "Alle (enkelvoudige) INFORMATIEOBJECTen opvragen."
            description =
                "Geeft een gepagineerde lijst van ENKELVOUDIGINFORMATIEOBJECTen. " +
                "Alleen de laatste versie van elk INFORMATIEOBJECT wordt getoond."
            parameters {
                query("bronorganisatie") {
                    description =
                        "Het RSIN van de Niet-natuurlijk persoon zijnde de organisatie die het INFORMATIEOBJECT " +
                        "heeft gecreëerd of heeft ontvangen en als eerste in een samenwerkingsketen heeft vastgelegd."
                }
                query("identificatie") {
                    description = "Een binnen een gegeven context ondubbelzinnige referentie naar het INFORMATIEOBJECT."
                }
                query("trefwoorden") {
                    description = "Een lijst van trefwoorden gescheiden door comma's."
                }
                query("expand") { description = "Sluit de gespecifieerde gerelateerde resources in het antwoord in." }
                query("page") { description = "Een pagina binnen de gepagineerde set resultaten." }
                query("pageSize") {
                    description = "Het aantal resultaten terug te geven per pagina. (default: 100, maximum: 500)."
                }
                // Experimental filter features
                query("informatieobjecttype") {
                    description =
                        "**EXPERIMENTEEL** URL-referentie naar de gerelateerde INFORMATIEOBJECTTYPE " +
                        "(in deze of een andere API)."
                }
                query("vertrouwelijkheidaanduiding") {
                    description = "**EXPERIMENTEEL** De vertrouwelijkheidaanduiding van het INFORMATIEOBJECT. " +
                        "Komma-gescheiden lijst van waarden: openbaar, beperkt_openbaar, intern, " +
                        "zaakvertrouwelijk, vertrouwelijk, confidentieel, geheim, zeer_geheim."
                }
                query("titel") {
                    description =
                        "**EXPERIMENTEEL** De titel van het INFORMATIEOBJECT " +
                        "(bevat de gegeven waarde, hoofdletterongevoelig)."
                }
                query("auteur") {
                    description =
                        "**EXPERIMENTEEL** De persoon of organisatie die dit INFORMATIEOBJECT heeft aangemaakt " +
                        "(bevat de gegeven waarde, hoofdletterongevoelig)."
                }
                query("status") {
                    description =
                        "**EXPERIMENTEEL** Filter op de status van het INFORMATIEOBJECT. " +
                        "Mogelijke waarden: in_bewerking, ter_vaststelling, definitief, gearchiveerd."
                }
                query("beschrijving") {
                    description =
                        "**EXPERIMENTEEL** De beschrijving van het INFORMATIEOBJECT " +
                        "(bevat de gegeven waarde, hoofdletterongevoelig)."
                }
                query("trefwoorden__overlap") {
                    description =
                        "**EXPERIMENTEEL** Een lijst van trefwoorden gescheiden door komma's, " +
                        "geeft alle EnkelvoudigInformatieObjecten terug die ten minste een van de opgegeven trefwoorden hebben."
                }
                query("locked") {
                    description = "**EXPERIMENTEEL** Filter op vergrendeld (true) of ontgrendeld (false)."
                }
                query("creatiedatum__gte") {
                    description =
                        "**EXPERIMENTEEL** De aanmakingsdatum van het INFORMATIEOBJECT " +
                        "(groter of gelijk aan de gegeven datum, formaat: YYYY-MM-DD)."
                }
                query("creatiedatum__lte") {
                    description =
                        "**EXPERIMENTEEL** De aanmakingsdatum van het INFORMATIEOBJECT " +
                        "(kleiner of gelijk aan de gegeven datum, formaat: YYYY-MM-DD)."
                }
                query("registratiedatum__gte") {
                    description =
                        "**EXPERIMENTEEL** De registratiedatum (`beginRegistratie`) van het INFORMATIEOBJECT " +
                        "(groter of gelijk aan de gegeven datum/tijd, formaat: date-time, bijv. 2025-01-01T00:00:00)."
                }
                query("registratiedatum__lte") {
                    description =
                        "**EXPERIMENTEEL** De registratiedatum (`beginRegistratie`) van het INFORMATIEOBJECT " +
                        "(kleiner of gelijk aan de gegeven datum/tijd, formaat: date-time, bijv. 2025-01-01T00:00:00)."
                }
                query("ordering") {
                    description =
                        "**EXPERIMENTEEL** Sorteer op één of meer velden (komma-gescheiden). " +
                        "Gebruik een `-` prefix voor aflopende volgorde. " +
                        "Mogelijke waarden: auteur, bestandsomvang, creatiedatum, formaat, status, titel, " +
                        "vertrouwelijkheidaanduiding (en hun `-`-varianten)."
                }
                query("objectinformatieobjecten__object") {
                    description =
                        "**EXPERIMENTEEL** URL-referentie naar het gerelateerde object (in deze of een andere API)."
                }
                query("objectinformatieobjecten__objectType") {
                    description =
                        "**EXPERIMENTEEL** Het type van het gerelateerde object. Mogelijke waarden: zaak, besluit, etc."
                }
            }
            responses {
                response(200) {
                    description = "Lijst van ENKELVOUDIGINFORMATIEOBJECTen."
                    ContentType.Application.Json { schema = jsonSchema<PaginatedResponse<EnkelvoudigInformatieObjectResponse>>() }
                }
                response(400) { description = "Bad request." }
                response(401) { description = "Unauthorized." }
                response(403) { description = "Forbidden." }
            }
        }

    /**
     * Maak een ENKELVOUDIGINFORMATIEOBJECT aan.
     *
     * **Er wordt gevalideerd op**
     * - geldigheid `informatieobjecttype` URL - de resource moet opgevraagd kunnen worden uit de catalogi API
     *   en de vorm van een INFORMATIEOBJECTTYPE hebben.
     * - publicatie `informatieobjecttype` - `concept` INFORMATIEOBJECTTYPE-en mogen niet gebruikt worden.
     *
     * Responses:
     *   - 201 Created.
     *   - 400 Bad request.
     *   - 401 Unauthorized.
     *   - 403 Forbidden.
     *   - 406 Not acceptable.
     *   - 409 Conflict.
     *   - 410 Gone.
     *   - 415 Unsupported media type.
     *   - 429 Too many requests.
     *   - 500 Internal server error.
     *
     * @tag EnkelvoudigInformatieObjecten
     */
    post { create() }
        .describe {
            operationId = "enkelvoudiginformatieobjecten_create"
            tag("enkelvoudiginformatieobjecten")
            summary = "Maak een ENKELVOUDIGINFORMATIEOBJECT aan."
            description = "Maak een ENKELVOUDIGINFORMATIEOBJECT aan."
            requestBody {
                required = true
                description = "Gegevens van het aan te maken INFORMATIEOBJECT."
                content {
                    schema = jsonSchema<EnkelvoudigInformatieObjectRequest>()
                }
            }
            responses {
                response(201) {
                    description = "Aangemaakt."
                    ContentType.Application.Json { schema = jsonSchema<EnkelvoudigInformatieObjectResponse>() }
                    headers {
                        header("Location") { description = "URL van het aangemaakte INFORMATIEOBJECT." }
                        header("API-version") { description = "Geeft de specifieke API-versie aan." }
                    }
                }
                response(400) { description = "Bad request." }
                response(401) { description = "Unauthorized." }
                response(403) { description = "Forbidden." }
            }
        }

    /**
     * Voer een zoekopdracht uit op (ENKELVOUDIG) INFORMATIEOBJECTen.
     *
     * Zoeken/filteren gaat normaal via de `list` operatie, deze is echter niet geschikt
     * voor zoekopdrachten met UUIDs.
     *
     * Responses:
     *   - 200 Lijst van (ENKELVOUDIGe) INFORMATIEOBJECTen.
     *   - 400 Bad request.
     *   - 401 Unauthorized.
     *   - 403 Forbidden.
     *   - 406 Not acceptable.
     *   - 409 Conflict.
     *   - 410 Gone.
     *   - 415 Unsupported media type.
     *   - 429 Too many requests.
     *   - 500 Internal server error.
     *
     * @tag EnkelvoudigInformatieObjecten
     */
    post("/_zoek") { zoek() }
        .describe {
            operationId = "enkelvoudiginformatieobjecten_zoek"
            tag("enkelvoudiginformatieobjecten")
            summary = "Voer een zoekopdracht uit op ENKELVOUDIGINFORMATIEOBJECTen."
            description =
                "Zoeken/filteren op UUID of andere velden. Gebruik dit endpoint voor zoekopdrachten met UUIDs."
            requestBody {
                required = true
                description = "Zoekcriteria."
                content {
                    schema = jsonSchema<EIOZoekRequest>()
                }
            }
            responses {
                response(200) {
                    description = "Lijst van gevonden ENKELVOUDIGINFORMATIEOBJECTen."
                    ContentType.Application.Json { schema = jsonSchema<PaginatedResponse<EnkelvoudigInformatieObjectResponse>>() }
                }
                response(400) { description = "Bad request." }
                response(401) { description = "Unauthorized." }
                response(403) { description = "Forbidden." }
            }
        }

    // Single document operations
    route("/{uuid}") {
        /**
         * De headers voor een specifiek(e) ENKELVOUDIGINFORMATIEOBJECT opvragen.
         *
         * Vraag de headers op die je bij een GET request zou krijgen.
         *
         * Responses:
         *   - 200 OK.
         *   - 400 missing/invalid UUID-parameter.
         *   - 404 Not found.
         *
         * @tag EnkelvoudigInformatieObjecten
         */
        head { head() }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_headers"
                tag("enkelvoudiginformatieobjecten")
                summary = "De headers voor een specifiek ENKELVOUDIGINFORMATIEOBJECT opvragen."
                parameters {
                    path("uuid") { description = "Unieke resource identifier (UUID4)." }
                }
                responses {
                    response(200) { description = "OK." }
                    response(400) { description = "Bad request: ontbrekende of ongeldige UUID." }
                    response(404) { description = "Not found." }
                }
            }

        /**
         * Een specifiek ENKELVOUDIGINFORMATIEOBJECT opvragen.
         *
         * Het object bevat metadata over het document en de downloadlink (`inhoud`) naar de binary data.
         * Dit geeft standaard de laatste versie van het ENKELVOUDIGINFORMATIEOBJECT.
         * Specifieke versies kunnen worden opgevraagd via de `versie` query parameter.
         *
         * Responses:
         *   - 200 OK.
         *   - 401 Unauthorized.
         *   - 403 Forbidden.
         *   - 404 Not found.
         *   - 406 Not acceptable.
         *   - 409 Conflict.
         *   - 410 Gone.
         *   - 415 Unsupported media type.
         *   - 429 Too many requests.
         *   - 500 Internal server error.
         *
         * @tag EnkelvoudigInformatieObjecten
         */
        get { get() }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_read"
                tag("enkelvoudiginformatieobjecten")
                summary = "Een specifiek ENKELVOUDIGINFORMATIEOBJECT opvragen."
                description = "Geeft het INFORMATIEOBJECT terug. Standaard de laatste versie."
                parameters {
                    path("uuid") { description = "Unieke resource identifier (UUID4)." }
                    query("versie") { description = "Specifieke versie van het INFORMATIEOBJECT." }
                    query("registratieOp") { description = "Filtert op de registratiedatum." }
                    query("expand") {
                        description = "Sluit de gespecifieerde gerelateerde resources in het antwoord in."
                    }
                    header("If-None-Match") {
                        description =
                            "Conditioneel GET: geef de ETag-waarde van de eerder ontvangen response mee. " +
                            "De server antwoordt met 304 Not Modified als de resource niet gewijzigd is."
                        required = false
                    }
                }
                responses {
                    response(200) {
                        description = "OK."
                        ContentType.Application.Json { schema = jsonSchema<EnkelvoudigInformatieObjectResponse>() }
                    }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                    response(404) { description = "Not found." }
                }
            }

        /**
         * Werk een ENKELVOUDIGINFORMATIEOBJECT in zijn geheel bij.
         *
         * Dit creëert altijd een nieuwe versie van het ENKELVOUDIGINFORMATIEOBJECT.
         *
         * Responses:
         *   - 200 OK.
         *   - 400 Bad request.
         *   - 401 Unauthorized.
         *   - 403 Forbidden.
         *   - 404 Not found.
         *   - 406 Not acceptable.
         *   - 409 Conflict.
         *   - 410 Gone.
         *   - 415 Unsupported media type.
         *   - 429 Too many requests.
         *   - 500 Internal server error.
         *
         * @tag EnkelvoudigInformatieObjecten
         */
        put { put() }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_update"
                tag("enkelvoudiginformatieobjecten")
                summary = "Werk een ENKELVOUDIGINFORMATIEOBJECT in zijn geheel bij."
                description = "Dit creëert altijd een nieuwe versie van het INFORMATIEOBJECT."
                parameters {
                    path("uuid") { description = "Unieke resource identifier (UUID4)." }
                }
                requestBody {
                    required = true
                    description = "Bijgewerkte gegevens van het INFORMATIEOBJECT."
                    content {
                        schema = jsonSchema<EnkelvoudigInformatieObjectRequest>()
                    }
                }
                responses {
                    response(200) {
                        description = "OK."
                        ContentType.Application.Json { schema = jsonSchema<EnkelvoudigInformatieObjectResponse>() }
                    }
                    response(400) { description = "Bad request." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                    response(404) { description = "Not found." }
                }
            }

        /**
         * Werk een ENKELVOUDIGINFORMATIEOBJECT deels bij.
         *
         * Dit creëert altijd een nieuwe versie van het ENKELVOUDIGINFORMATIEOBJECT.
         *
         * Responses:
         *   - 200 OK.
         *   - 400 Bad request.
         *   - 401 Unauthorized.
         *   - 403 Forbidden.
         *   - 404 Not found.
         *   - 406 Not acceptable.
         *   - 409 Conflict.
         *   - 410 Gone.
         *   - 415 Unsupported media type.
         *   - 429 Too many requests.
         *   - 500 Internal server error.
         *
         * @tag EnkelvoudigInformatieObjecten
         */
        patch { patch() }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_partial_update"
                tag("enkelvoudiginformatieobjecten")
                summary = "Werk een ENKELVOUDIGINFORMATIEOBJECT deels bij."
                description = "Dit creëert altijd een nieuwe versie van het INFORMATIEOBJECT."
                parameters {
                    path("uuid") { description = "Unieke resource identifier (UUID4)." }
                }
                requestBody {
                    required = false
                    description = "Gedeeltelijk bijgewerkte gegevens van het INFORMATIEOBJECT."
                    content {
                        schema = jsonSchema<EnkelvoudigInformatieObjectRequest>()
                    }
                }
                responses {
                    response(200) {
                        description = "OK."
                        ContentType.Application.Json { schema = jsonSchema<EnkelvoudigInformatieObjectResponse>() }
                    }
                    response(400) { description = "Bad request." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                    response(404) { description = "Not found." }
                }
            }

        /**
         * Verwijder een ENKELVOUDIGINFORMATIEOBJECT.
         *
         * Verwijder een ENKELVOUDIGINFORMATIEOBJECT en alle bijbehorende versies, samen met alle
         * gerelateerde resources binnen deze API. Dit is alleen mogelijk als er geen
         * OBJECTINFORMATIEOBJECTen gerelateerd zijn aan het ENKELVOUDIGINFORMATIEOBJECT.
         *
         * Responses:
         *   - 204 No content.
         *   - 401 Unauthorized.
         *   - 403 Forbidden.
         *   - 404 Not found.
         *   - 406 Not acceptable.
         *   - 409 Conflict.
         *   - 410 Gone.
         *   - 415 Unsupported media type.
         *   - 429 Too many requests.
         *   - 500 Internal server error.
         *
         * @tag EnkelvoudigInformatieObjecten
         */
        delete { delete() }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_delete"
                tag("enkelvoudiginformatieobjecten")
                summary = "Verwijder een ENKELVOUDIGINFORMATIEOBJECT."
                description =
                    "Verwijdert het INFORMATIEOBJECT en alle bijbehorende versies. " +
                    "Alleen mogelijk als er geen OBJECTINFORMATIEOBJECTen aan gerelateerd zijn."
                parameters {
                    path("uuid") { description = "Unieke resource identifier (UUID4)." }
                }
                responses {
                    response(204) { description = "No content." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                    response(404) { description = "Not found." }
                    response(409) { description = "Conflict: INFORMATIEOBJECT is vergrendeld." }
                }
            }

        /**
         * Download de binaire data van het ENKELVOUDIGINFORMATIEOBJECT.
         *
         * Responses:
         *   - 200 OK (binary stream).
         *   - 401 Unauthorized.
         *   - 403 Forbidden.
         *   - 404 Not found.
         *   - 406 Not acceptable.
         *   - 410 Gone.
         *   - 415 Unsupported media type.
         *   - 429 Too many requests.
         *   - 500 Internal server error.
         *
         * @tag EnkelvoudigInformatieObjecten
         */
        get("/download") { download() }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_download"
                tag("enkelvoudiginformatieobjecten")
                summary = "Download de binaire data van het INFORMATIEOBJECT."
                parameters {
                    path("uuid") { description = "Unieke resource identifier (UUID4)." }
                }
                responses {
                    response(200) {
                        description = "OK — binaire bestandsinhoud."
                        headers {
                            header("Content-Disposition") { description = "Bestandsnaam voor de download." }
                            header("Content-Type") { description = "MIME-type van het bestand." }
                        }
                    }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                    response(404) { description = "Not found." }
                }
            }

        /**
         * Vergrendel een ENKELVOUDIGINFORMATIEOBJECT.
         *
         * Voert een 'checkout' uit waardoor het ENKELVOUDIGINFORMATIEOBJECT vergrendeld wordt
         * met een `lock` waarde. Alleen met deze waarde kan het ENKELVOUDIGINFORMATIEOBJECT
         * bijgewerkt (`PUT`, `PATCH`) en ontgrendeld worden.
         *
         * Responses:
         *   - 200 OK (lock value returned).
         *   - 400 Bad request.
         *   - 401 Unauthorized.
         *   - 403 Forbidden.
         *   - 404 Not found.
         *   - 406 Not acceptable.
         *   - 410 Gone.
         *   - 415 Unsupported media type.
         *   - 429 Too many requests.
         *   - 500 Internal server error.
         *
         * @tag EnkelvoudigInformatieObjecten
         */
        post("/lock") { lock() }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_lock"
                tag("enkelvoudiginformatieobjecten")
                summary = "Vergrendel een ENKELVOUDIGINFORMATIEOBJECT."
                description =
                    "Voert een checkout uit waardoor het INFORMATIEOBJECT vergrendeld wordt met een lock-waarde."
                parameters {
                    path("uuid") { description = "Unieke resource identifier (UUID4)." }
                }
                responses {
                    response(200) {
                        description = "OK — lock-waarde teruggegeven."
                        ContentType.Application.Json { schema = jsonSchema<LockPayload>() }
                    }
                    response(400) { description = "Bad request." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                    response(404) { description = "Not found." }
                    response(409) { description = "Conflict: INFORMATIEOBJECT is al vergrendeld." }
                }
            }

        /**
         * Ontgrendel een ENKELVOUDIGINFORMATIEOBJECT.
         *
         * Heft de 'checkout' op waardoor het ENKELVOUDIGINFORMATIEOBJECT ontgrendeld wordt.
         *
         * Responses:
         *   - 204 No content.
         *   - 400 Bad request.
         *   - 401 Unauthorized.
         *   - 403 Forbidden.
         *   - 404 Not found.
         *   - 406 Not acceptable.
         *   - 410 Gone.
         *   - 415 Unsupported media type.
         *   - 429 Too many requests.
         *   - 500 Internal server error.
         *
         * @tag EnkelvoudigInformatieObjecten
         */
        post("/unlock") { unlock() }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_unlock"
                tag("enkelvoudiginformatieobjecten")
                summary = "Ontgrendel een ENKELVOUDIGINFORMATIEOBJECT."
                description = "Heft de checkout op waardoor het INFORMATIEOBJECT ontgrendeld wordt."
                parameters {
                    path("uuid") { description = "Unieke resource identifier (UUID4)." }
                }
                responses {
                    response(204) { description = "No content." }
                    response(400) { description = "Bad request." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                    response(404) { description = "Not found." }
                    response(409) { description = "Conflict: ongeldige lock-waarde of niet vergrendeld." }
                }
            }
    }
}

/**
 * Extension property to get the request-scoped EnkelvoudigInformatieObjectService.
 */
private val RoutingContext.service: EnkelvoudigInformatieObjectService
    get() = call.scope.get<EnkelvoudigInformatieObjectService>()

private suspend fun RoutingContext.list() {
    val (page, pageSize, filter) = getFilters()
    val (items, totalCount) = service.getAll(filter)
    call.respond(PaginatedResponse.from(call, RESOURCE_SEGMENT, items, totalCount, page, pageSize))
}

private suspend fun RoutingContext.create() {
    val request = call.receive<EnkelvoudigInformatieObjectRequest>()
    try {
        val response = service.create(request)
        // Location header with the URL of the created resource
        val locationUrl = ApiUrlBuilder.absolute(RESOURCE_SEGMENT, response.id)
        call.response.headers.append(HttpHeaders.Location, locationUrl)

        call.respond(HttpStatusCode.Created, response)
    } catch (e: IllegalArgumentException) {
        call.respondProblem(

            HttpStatusCode.BadRequest,

            badRequest(e.message ?: "Validation failed", call.request.path()),
        )
        return
    }
}

private suspend fun RoutingContext.zoek() {
    val request = call.receive<EIOZoekRequest>()
    val (page, pageSize, filter) = getFilters(request.uuidIn, request.expand)

    val (items, totalCount) = service.getAll(filter)
    val response = PaginatedResponse.from(call, RESOURCE_SEGMENT, items, totalCount, page, pageSize)

    call.respond(response)
}

private fun RoutingContext.getFilters(
    uuidIn: List<String> = emptyList(),
    expandStr: String? = null,
): Triple<Int, Int, QueryEnkelvoudigeInformatieObjectenFilter> {
    val params = call.request.queryParameters
    val expand = splitOnComma(expandStr ?: params["expand"])
    val bronOrganisatie = params["bronorganisatie"]
    val trefwoorden = splitOnComma(params["trefwoorden"])
    val trefwoordenOverlap = splitOnComma(params["trefwoorden__overlap"])
    val identificatie = params["identificatie"]
    val page = params["page"]?.toIntOrNull() ?: 1
    // Default pageSize 100 aligns with Open Zaak. Not in Documenten API 1.5.0 spec.
    val pageSize = params["pageSize"]?.toIntOrNull() ?: 100

    // EXPERIMENTEEL filters
    val objectUrl = params["objectinformatieobjecten__object"]
    val objectType = params["objectinformatieobjecten__objectType"]
    val informatieobjecttype = params["informatieobjecttype"]
    val vertrouwelijkheidaanduiding = splitOnComma(params["vertrouwelijkheidaanduiding"])
    val titel = params["titel"]
    val auteur = params["auteur"]
    val status = params["status"]
    val beschrijving = params["beschrijving"]
    val creatiedatumLte = params["creatiedatum__lte"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val creatiedatumGte = params["creatiedatum__gte"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val registratiedatumLte = params["registratiedatum__lte"]?.let { parseRegistratiedatum(it) }
    val registratiedatumGte = params["registratiedatum__gte"]?.let { parseRegistratiedatum(it) }
    val locked = params["locked"]?.let { value ->
        when (value.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
    // NOTE: The `ordering` query parameter (and related extended filters such as date ranges and `locked`)
    // are experimental extensions to the standard Documenten API and are not yet reflected in
    // docs/documenten-1.5.0.yaml. Update the OpenAPI spec when these filters are considered stable.
    val ordering = splitOnComma(params["ordering"]).mapNotNull { EIOOrdering.fromValue(it) }

    val filter = QueryEnkelvoudigeInformatieObjectenFilter(
        uuids = uuidIn,
        bronOrganisatie = bronOrganisatie,
        trefwoorden = trefwoorden,
        trefwoordenOverlap = trefwoordenOverlap,
        identificatie = identificatie,
        page = page,
        pageSize = pageSize,
        objectUrl = objectUrl,
        objectType = objectType,
        informatieobjecttype = informatieobjecttype,
        vertrouwelijkheidaanduiding = vertrouwelijkheidaanduiding,
        titel = titel,
        auteur = auteur,
        status = status,
        beschrijving = beschrijving,
        creatiedatumLte = creatiedatumLte,
        creatiedatumGte = creatiedatumGte,
        registratiedatumLte = registratiedatumLte,
        registratiedatumGte = registratiedatumGte,
        locked = locked,
        ordering = ordering,
        expand = expand,
    )
    return Triple(page, pageSize, filter)
}

// Accepts RFC3339 with timezone designator (e.g. Z, +02:00) and timezone-less ISO 8601.
private fun parseRegistratiedatum(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()
    ?: runCatching { LocalDateTime.Formats.ISO.parse(value).toInstant(TimeZone.UTC) }.getOrNull()

private fun splitOnComma(params: String?): List<String> = (
    params?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?: emptyList()
    )

private suspend fun RoutingContext.head() {
    val uuidString = call.parameters["uuid"]
    if (uuidString == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(uuidString)
        if (service.exists(uuid)) {
            call.respond(HttpStatusCode.OK)
        } else {
            call.respondProblem(

                HttpStatusCode.NotFound,

                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )
        }
    } catch (_: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
    }
}

private suspend fun RoutingContext.get() {
    // TODO add version and registratieOp query parameters support
    val uuidString = call.parameters["uuid"]
    if (uuidString == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    val expand = splitOnComma(call.parameters["expand"])

    try {
        val uuid = UUID.fromString(uuidString)
        val result = service.getById(uuid, expand)

        if (result == null) {
            call.respondProblem(

                HttpStatusCode.NotFound,

                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )
        } else {
            call.respond(HttpStatusCode.OK, result)
        }
    } catch (_: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
    }
}

private suspend fun RoutingContext.put() {
    val uuidString = call.parameters["uuid"]
    if (uuidString == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(uuidString)
        val request = call.receive<EnkelvoudigInformatieObjectRequest>()
        val response = service.update(uuid, request)
        if (response == null) {
            call.respondProblem(

                HttpStatusCode.NotFound,

                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )
            return
        }
        call.respond(HttpStatusCode.OK, response)
    } catch (e: IllegalArgumentException) {
        call.respondProblem(

            HttpStatusCode.BadRequest,

            badRequest(e.message ?: "Invalid UUID format", call.request.path()),
        )
        return
    }
}

private suspend fun RoutingContext.patch() {
    val uuidString = call.parameters["uuid"]
    if (uuidString == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }
    try {
        val uuid = UUID.fromString(uuidString)
        val request = call.receive<EnkelvoudigInformatieObjectRequest>()
        val response = service.update(uuid, request, true)
        if (response == null) {
            call.respondProblem(

                HttpStatusCode.NotFound,

                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )
            return
        }
        call.respond(HttpStatusCode.OK, response)
    } catch (e: IllegalArgumentException) {
        call.respondProblem(

            HttpStatusCode.BadRequest,

            badRequest(e.message ?: "Invalid UUID format", call.request.path()),
        )
    }
}

private suspend fun RoutingContext.delete() {
    val uuidString = call.parameters["uuid"]
    if (uuidString == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(uuidString)
        when (service.delete(uuid)) {
            is DeleteResult.Success -> {
                call.respond(HttpStatusCode.NoContent)
            }

            is DeleteResult.NotFound -> call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )

            is DeleteResult.Locked -> call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("EnkelvoudigInformatieObject is locked", call.request.path()),
            )

            is DeleteResult.HasReferences -> call.respondProblem(
                HttpStatusCode.BadRequest,
                badRequest(
                    "EnkelvoudigInformatieObject cannot be deleted because it has related resources",
                    call.request.path(),
                ),
            )
        }
    } catch (_: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
    }
}

private suspend fun RoutingContext.download() {
    // TODO add version and registratieOp query parameters support
    val uuidString = call.parameters["uuid"]
    if (uuidString == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(uuidString)

        val eio = transaction {
            val record = EIORecordEntity.findById(uuid) ?: return@transaction null
            record.latestVersion()
        }

        if (eio == null) {
            call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )
            return
        }

        // Ensure we have a stored object key to stream
        val objectKey = eio.bestandsLocatie
        if (objectKey.isBlank()) {
            call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("Document content not available for download", call.request.path()),
            )
            return
        }

        // Derive filename and content type when possible;
        val fileName = objectKey.ifBlank { "document-${eio.id}" }
        val contentType = try {
            // eio.formaat is expected to be a MIME type; if not, fallback below
            eio.formaat?.let { ContentType.parse(it) }
        } catch (_: Exception) {
            ContentType.Application.OctetStream
        }

        // Set headers before starting the stream
        call.response.headers.append(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(ContentDisposition.Parameters.FileName, fileName)
                .toString(),
        )
        call.response.headers.append(HttpHeaders.ContentType, contentType.toString())
        // TODO: support Range requests, ETag, Last-Modified when metadata is available

        // Stream the object from storage directly to the HTTP response
        call.respondOutputStream {
            service.streamByBestandsnaam(bestandsnaam = objectKey, output = this, repoName = eio.bestandsRepository)
        }
    } catch (_: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
    }
}

private suspend fun RoutingContext.lock() {
    val uuidString = call.parameters["uuid"]
    if (uuidString == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(uuidString)
        when (val result = service.lock(uuid)) {
            null -> call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )

            is LockResult.Success -> call.respond(result.payload)
            is LockResult.AlreadyLocked -> call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("EnkelvoudigInformatieObject is already locked", call.request.path()),
            )
        }
    } catch (_: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
    }
}

private suspend fun RoutingContext.unlock() {
    val uuidString = call.parameters["uuid"]
    if (uuidString == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(uuidString)
        val body = call.receive<UnlockEIORequest>()
        when (service.unlock(uuid, body.lock)) {
            is UnlockResult.Success -> call.respond(HttpStatusCode.NoContent)
            is UnlockResult.InvalidLock -> call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("Invalid lock token for unlock", call.request.path()),
            )

            is UnlockResult.NotLocked -> call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("EnkelvoudigInformatieObject is not locked", call.request.path()),
            )

            null -> call.respondProblem(

                HttpStatusCode.NotFound,

                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )
        }
    } catch (_: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
    }
}
