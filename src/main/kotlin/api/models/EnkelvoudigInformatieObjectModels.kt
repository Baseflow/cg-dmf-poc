// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api.models
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * EnkelvoudigInformatieObject request model.
 * Dit model kan voor create, patch en put gebruikt worden.
 * Bij patch en put zijn bronorganisatie, titel, auteur, taal creatiedatum en informatieobjecttype,
 * verplicht en * dient de `controleerVerplichteVelden` methode te worden aangeroepen om te
 * controleren of deze velden wel zijn opgegeven.
 */
@Serializable
data class EnkelvoudigInformatieObjectRequest(
    val identificatie: String? = null,
    val bronorganisatie: String? = null,
    val creatiedatum: LocalDate? = null,
    val titel: String? = null,
    val vertrouwelijkheidaanduiding: Vertrouwelijkheidaanduiding? = null,
    val auteur: String? = null,
    val status: EnkelvoudigInformatieObjectStatus? = null,
    val formaat: String? = null,
    val taal: String? = null,
    val bestandsnaam: String? = null,
    val inhoud: String? = null,
    val bestandsomvang: Long? = null,
    val link: String? = null,
    val beschrijving: String? = null,
    val indicatieGebruiksrecht: Boolean? = null,
    val verschijningsvorm: String? = null,
    val ondertekening: Ondertekening? = null,
    val integriteit: Integriteit? = null,
    val informatieobjecttype: String? = null,
    val trefwoorden: List<String>? = null,
    val inhoudIsVervallen: Boolean? = null,
) : ApiRequest {
    init {

        // Length checks
        require(identificatie.isNullOrBlank() || identificatie.length <= 40) {
            "Identificatie mag maximaal 40 karakters lang zijn"
        }
        require(bestandsnaam.isNullOrBlank() || bestandsnaam.length <= 255) {
            "Bestandsnaam mag maximaal 255 karakters lang zijn"
        }
        require(titel.orEmpty().length <= 200) { "Titel mag maximaal 200 karakters lang zijn" }
        require(auteur.orEmpty().length <= 200) { "Auteur mag maximaal 200 karakters lang zijn" }
        require(beschrijving.isNullOrBlank() || beschrijving.length <= 1000) {
            "Beschrijving mag maximaal 1000 karakters lang zijn"
        }
        require(formaat.isNullOrEmpty() || formaat.length <= 255) { "Formaat mag maximaal 255 karakters lang zijn" }
        require(link.isNullOrEmpty() || link.length <= 200) { "Link mag maximaal 200 karakters lang zijn" }
        require(informatieobjecttype.isNullOrBlank() || informatieobjecttype.length <= 200) {
            "Informatieobjecttype mag maximaal 200 karakters lang zijn"
        }
        require(
            trefwoorden.isNullOrEmpty() ||
                trefwoorden.all {
                    it.length <= 100
                },
        ) { "Elk trefwoord mag maximaal 100 karakters lang zijn" }

        // format checks
        require(taal.isNullOrBlank() || taal.matches(Regex("^[a-z]{3}$"))) { "Taal moet conform ISO 639-2/B code zijn" }

        // complex requirements
        require(
            (
                status != EnkelvoudigInformatieObjectStatus.IN_BEWERKING &&
                    status != EnkelvoudigInformatieObjectStatus.TER_VASTSTELLING
                ) ||
                ondertekening == null,
        ) {
            "Ondertekening mag niet worden opgegeven voor status 'in bewerking' of 'ter vaststelling'"
        }

        if (inhoud != null) {
            require(bestandsnaam != null) { "Bestandsnaam moet worden opgegeven als inhoud is opgegeven" }
        }
    }

    /*
     * Controleer of alle verplichte velden zijn opgegeven.
     */
    fun controleerVerplichteVelden() {
        // Required fields
        require(!bronorganisatie.isNullOrBlank()) { "Bronorganisatie mag niet leeg zijn" }
        require(!titel.isNullOrBlank()) { "Titel mag niet leeg zijn" }
        require(!auteur.isNullOrBlank()) { "Auteur mag niet leeg zijn" }
        require(!taal.isNullOrBlank()) { "Taal mag niet leeg zijn" }
        require(!informatieobjecttype.isNullOrBlank()) { "Informatieobjecttype mag niet leeg zijn" }
        require(creatiedatum != null) { "Creatiedatum mag niet leeg zijn" }
    }

    fun isFileEmpty(): Boolean = inhoud.isNullOrEmpty() || bestandsnaam == null
}

@Serializable
data class Ondertekening(val soort: OndertekeningSoort, val datum: LocalDate)

@Serializable
data class Integriteit(val algoritme: IntegriteitAlgoritme, val waarde: String, val datum: LocalDate) {
    init {
        require(waarde.isNotEmpty()) { "Waarde mag niet leeg zijn" }
    }
}

@Serializable
data class BestandsDeelResponse(val url: String, val volgnummer: Int, val omvang: Long, val voltooid: Boolean, val lock: Boolean)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EnkelvoudigInformatieObjectResponse(
    override val id: String,
    override val url: String? = null,
    val identificatie: String? = null,
    val bronorganisatie: String,
    val creatiedatum: LocalDate,
    val titel: String,
    val versie: Int,
    val vertrouwelijkheidaanduiding: Vertrouwelijkheidaanduiding? = null,
    val auteur: String,
    val status: EnkelvoudigInformatieObjectStatus? = null,
    val formaat: String? = null,
    val taal: String,
    val bestandsnaam: String? = null,
    val inhoud: String? = null,
    val bestandsomvang: Long? = null,
    val link: String? = null,
    val beschrijving: String? = null,
    val beginRegistratie: String,
    val indicatieGebruiksrecht: Boolean? = null,
    val verschijningsvorm: String? = null,
    val ondertekening: Ondertekening? = null,
    val integriteit: Integriteit? = null,
    val informatieobjecttype: String,
    val trefwoorden: List<String> = emptyList(),
    val inhoudIsVervallen: Boolean? = null,
    @kotlinx.serialization.EncodeDefault
    val bestandsdelen: List<BestandsDeelResponse> = emptyList(),
    val lock: String,
    @kotlinx.serialization.EncodeDefault
    val locked: Boolean,
) : ApiEntityResponse

@Serializable
enum class EnkelvoudigInformatieObjectStatus {
    @SerialName("concept")
    CONCEPT,

    @SerialName("in_bewerking")
    IN_BEWERKING,

    @SerialName("definitief")
    DEFINITIEF,

    @SerialName("ter_vaststelling")
    TER_VASTSTELLING,

    @SerialName("vastgesteld")
    VASTGESTELD,

    @SerialName("gearchiveerd")
    GEARCHIVEERD,
}

@Serializable
enum class Vertrouwelijkheidaanduiding {
    // TODO this is BlankEnum from the spec.
    // if we have more of these, we should probably factor this out
    @SerialName("")
    BLANK,

    @SerialName("openbaar")
    OPENBAAR,

    @SerialName("beperkt_openbaar")
    BEPERKT_OPENBAAR,

    @SerialName("intern")
    INTERN,

    @SerialName("zaakvertrouwelijk")
    ZAAKVERTROUWELIJK,

    @SerialName("vertrouwelijk")
    VERTROUWELIJK,

    @SerialName("confidentieel")
    CONFIDENTIEEL,

    @SerialName("geheim")
    GEHEIM,

    @SerialName("zeer_geheim")
    ZEER_GEHEIM,
}

@Serializable
enum class IntegriteitAlgoritme {
    @SerialName("crc_16")
    CRC_16,

    @SerialName("crc_32")
    CRC_32,

    @SerialName("crc_64")
    CRC_64,

    @SerialName("fletcher_4")
    FLETCHER_4,

    @SerialName("fletcher_8")
    FLETCHER_8,

    @SerialName("fletcher_16")
    FLETCHER_16,

    @SerialName("fletcher_32")
    FLETCHER_32,

    @SerialName("hmac")
    HMAC,

    @SerialName("md5")
    MD5,

    @SerialName("sha_1")
    SHA_1,

    @SerialName("sha_256")
    SHA_256,
}

@Serializable
enum class OndertekeningSoort {
    @SerialName("analoog")
    ANALOOG,

    @SerialName("digitaal")
    DIGITAAL,

    @SerialName("pki")
    PKI,
}

@Serializable
data class UnlockEIORequest(val lock: String) : ApiRequest

@Serializable
data class EIOZoekRequest(
    @SerialName("uuid_In")
    val uuidIn: List<String>,
    val expand: String? = null,
) : ApiRequest
