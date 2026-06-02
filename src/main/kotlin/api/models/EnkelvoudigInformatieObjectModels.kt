// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api.models

import io.ktor.openapi.JsonSchema
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * EnkelvoudigInformatieObject request model.
 * Dit model kan voor create, patch en put gebruikt worden.
 * Bij patch en put zijn bronorganisatie, titel, auteur, taal creatiedatum en informatieobjecttype,
 * verplicht en * dient de `controleerVerplichteVelden` methode te worden aangeroepen om te
 * controleren of deze velden wel zijn opgegeven.
 */
@JsonSchema.Title("EnkelvoudigInformatieObjectRequest")
@JsonSchema.Description("Request-model voor het aanmaken of bijwerken van een ENKELVOUDIGINFORMATIEOBJECT (document met metadata).")
@JsonSchema.Required("bronorganisatie", "creatiedatum", "titel", "auteur", "taal", "informatieobjecttype")
@JsonSchema.Example(
    """{
  "bronorganisatie": "123456782",
  "creatiedatum": "2024-01-15",
  "titel": "Besluit vergunning omgevingsrecht",
  "auteur": "Gemeente Utrecht",
  "taal": "nld",
  "informatieobjecttype": "https://catalogi.example.com/api/v1/informatieobjecttypen/1c35fb6a-a07e-4643-b522-4a68b7deb21d",
  "status": "definitief",
  "formaat": "application/pdf",
  "bestandsnaam": "besluit-2024-001.pdf",
  "bestandsomvang": 12345,
  "vertrouwelijkheidaanduiding": "openbaar",
  "beschrijving": "Besluit omgevingsvergunning bouwen woning Dorpsstraat 1",
  "trefwoorden": ["omgevingsrecht", "vergunning"]
}""",
)
@Serializable
data class EnkelvoudigInformatieObjectRequest(
    @JsonSchema.Description("Een binnen een gegeven context ondubbelzinnige referentie naar het INFORMATIEOBJECT. Maximaal 40 tekens.")
    @JsonSchema.MaxLength(40)
    val identificatie: String? = null,

    @JsonSchema.Description(
        "Het RSIN van de Niet-natuurlijk persoon zijnde de organisatie die het INFORMATIEOBJECT heeft gecreëerd " +
            "of heeft ontvangen en als eerste in een samenwerkingsketen heeft vastgelegd. 9 cijfers.",
    )
    @JsonSchema.MaxLength(9)
    @JsonSchema.MinLength(9)
    @JsonSchema.Pattern("^[0-9]{9}$")
    @JsonSchema.Example("\"123456782\"")
    val bronorganisatie: String? = null,

    @JsonSchema.Description(
        "Een datum of een gebeurtenis in de levenscyclus van het INFORMATIEOBJECT (ISO 8601, formaat YYYY-MM-DD). " +
            "Dit hoeft niet de aanmaakdatum van het document te zijn.",
    )
    @JsonSchema.Format("date")
    @JsonSchema.Example("\"2024-01-15\"")
    val creatiedatum: LocalDate? = null,

    @JsonSchema.Description("De naam waaronder het INFORMATIEOBJECT formeel bekend is. Maximaal 200 tekens.")
    @JsonSchema.MaxLength(200)
    @JsonSchema.Example("\"Besluit vergunning omgevingsrecht\"")
    val titel: String? = null,

    @JsonSchema.Description(
        "Aanduiding van de mate waarin het INFORMATIEOBJECT voor de openbaarheid bestemd is. " +
            "Mogelijke waarden: openbaar, beperkt_openbaar, intern, zaakvertrouwelijk, vertrouwelijk, " +
            "confidentieel, geheim, zeer_geheim.",
    )
    val vertrouwelijkheidaanduiding: Vertrouwelijkheidaanduiding? = null,

    @JsonSchema.Description(
        "De persoon of organisatie die in de eerste plaats verantwoordelijk is voor het creëren van de inhoud " +
            "van het INFORMATIEOBJECT. Maximaal 200 tekens.",
    )
    @JsonSchema.MaxLength(200)
    @JsonSchema.Example("\"Gemeente Utrecht\"")
    val auteur: String? = null,

    @JsonSchema.Description(
        "Aanduiding van de stand van zaken van een INFORMATIEOBJECT. " +
            "De waarden 'in_bewerking' en 'ter_vaststelling' komen niet voor als het document ontvangen is (ontvangstdatum). " +
            "Wijziging van 'definitief' naar een eerdere status is niet mogelijk. " +
            "Mogelijke waarden: concept, in_bewerking, ter_vaststelling, definitief, vastgesteld, gearchiveerd.",
    )
    val status: EnkelvoudigInformatieObjectStatus? = null,

    @JsonSchema.Description(
        "Het \"Media Type\" (voorheen \"MIME type\") voor de wijze waarop de inhoud van het INFORMATIEOBJECT is " +
            "vastgelegd in een computerbestand. Voorbeeld: `application/pdf`. " +
            "Zie: https://www.iana.org/assignments/media-types/media-types.xhtml. Maximaal 255 tekens.",
    )
    @JsonSchema.MaxLength(255)
    @JsonSchema.Example("\"application/pdf\"")
    val formaat: String? = null,

    @JsonSchema.Description(
        "Een ISO 639-2/B taalcode waarin de inhoud van het INFORMATIEOBJECT is vastgelegd (3-letterige code). " +
            "Voorbeeld: `nld` (Nederlands), `eng` (Engels). " +
            "Zie: https://www.iso.org/standard/4767.html",
    )
    @JsonSchema.MaxLength(3)
    @JsonSchema.MinLength(3)
    @JsonSchema.Pattern("^[a-z]{3}$")
    @JsonSchema.Example("\"nld\"")
    val taal: String? = null,

    @JsonSchema.Description(
        "De naam van het fysieke bestand waarin de inhoud van het INFORMATIEOBJECT is vastgelegd, inclusief extensie. Maximaal 255 tekens.",
    )
    @JsonSchema.MaxLength(255)
    @JsonSchema.Example("\"besluit-2024-001.pdf\"")
    val bestandsnaam: String? = null,

    @JsonSchema.Description(
        "Base64-gecodeerde bestandsinhoud. Gebruik dit veld alleen voor kleine bestanden (<4 GB); stuur anders via BESTANDSDELen.",
    )
    @JsonSchema.Format("byte")
    val inhoud: String? = null,

    @JsonSchema.Description("Aantal bytes dat de inhoud van het INFORMATIEOBJECT in beslag neemt.")
    @JsonSchema.Format("int64")
    @JsonSchema.Example("12345")
    val bestandsomvang: Long? = null,

    @JsonSchema.Description(
        "De URL waarmee de inhoud van het INFORMATIEOBJECT op te vragen is (alternatief voor inhoud-veld). Maximaal 200 tekens.",
    )
    @JsonSchema.Format("uri")
    @JsonSchema.MaxLength(200)
    val link: String? = null,

    @JsonSchema.Description("Een generieke beschrijving van de inhoud van het INFORMATIEOBJECT. Maximaal 1000 tekens.")
    @JsonSchema.MaxLength(1000)
    @JsonSchema.Example("\"Besluit omgevingsvergunning bouwen woning Dorpsstraat 1\"")
    val beschrijving: String? = null,

    @JsonSchema.Description(
        "Indicatie of er beperkingen gelden aangaande het gebruik van het informatieobject anders dan raadpleging. " +
            "Dit veld mag `null` zijn om aan te geven dat de indicatie nog niet bekend is. " +
            "Als de indicatie gezet is op `true`, moet er ook een Gebruiksrecht aangemaakt zijn.",
    )
    val indicatieGebruiksrecht: Boolean? = null,

    @JsonSchema.Description("De essentiële opmaakaspecten van een INFORMATIEOBJECT, vrij tekstveld (bijv. HTML, formulier, Word-document).")
    val verschijningsvorm: String? = null,

    @JsonSchema.Description(
        "Aanduiding van de rechtskracht van een INFORMATIEOBJECT. " +
            "Mag niet van een waarde zijn voorzien als de `status` de waarde 'in_bewerking' of 'ter_vaststelling' heeft.",
    )
    val ondertekening: Ondertekening? = null,

    @JsonSchema.Description(
        "Uitdrukking van mate van volledigheid en onbeschadigd zijn van digitaal bestand. Bevat het gebruikte algoritme, de berekende waarde en de datum.",
    )
    val integriteit: Integriteit? = null,

    @JsonSchema.Description("URL-referentie naar het INFORMATIEOBJECTTYPE (in de Catalogi API). Maximaal 200 tekens.")
    @JsonSchema.Format("uri")
    @JsonSchema.MaxLength(200)
    @JsonSchema.Example("\"https://catalogi.example.com/api/v1/informatieobjecttypen/1c35fb6a-a07e-4643-b522-4a68b7deb21d\"")
    val informatieobjecttype: String? = null,

    @JsonSchema.Description("Een lijst van trefwoorden gescheiden door comma's. Elk trefwoord maximaal 100 tekens.")
    @JsonSchema.Example("[\"omgevingsrecht\", \"vergunning\"]")
    val trefwoorden: List<String>? = null,

    @JsonSchema.Description(
        "Geeft aan of de inhoud van het INFORMATIEOBJECT al dan niet vervallen, dus niet langer geldig is. " +
            "`true` = De inhoud is vervallen. `false` = De inhoud is niet vervallen. Niet opgegeven = veld ontbreekt of is `null`.",
    )
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

@JsonSchema.Title("Ondertekening")
@JsonSchema.Description("Ondertekeningsinformatie van een INFORMATIEOBJECT.")
@JsonSchema.Example("""{"soort": "digitaal", "datum": "2024-01-15"}""")
@Serializable
data class Ondertekening(
    @JsonSchema.Description("Het type ondertekening: analoog, digitaal of pki.")
    val soort: OndertekeningSoort,
    @JsonSchema.Description("De datum waarop ondertekend is (ISO 8601, formaat YYYY-MM-DD).")
    @JsonSchema.Format("date")
    val datum: LocalDate,
)

@JsonSchema.Title("Integriteit")
@JsonSchema.Description("Integriteitscontrole-informatie (hash/checksum) van een INFORMATIEOBJECT.")
@JsonSchema.Example("""{"algoritme": "md5", "waarde": "d41d8cd98f00b204e9800998ecf8427e", "datum": "2024-01-15"}""")
@Serializable
data class Integriteit(
    @JsonSchema.Description("Het hash-algoritme waarmee de integriteitswaarde is berekend.")
    val algoritme: IntegriteitAlgoritme = IntegriteitAlgoritme.SHA_256,
    @JsonSchema.Description("De berekende hash-waarde (checksum) van het bestand.")
    @JsonSchema.Example("\"d41d8cd98f00b204e9800998ecf8427e\"")
    val waarde: String,
    @JsonSchema.Description("De datum waarop de integriteitswaarde is bepaald (ISO 8601, formaat YYYY-MM-DD).")
    @JsonSchema.Format("date")
    val datum: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date,
) {
    init {
        require(waarde.isNotEmpty()) { "Waarde mag niet leeg zijn" }
    }
}

@JsonSchema.Title("BestandsDeel")
@JsonSchema.Description("Een BESTANDSDEEL voor chunked upload van grote bestanden.")
@JsonSchema.Example(
    """{
  "url": "https://drc.example.com/api/v1/bestandsdelen/3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "volgnummer": 1,
  "omvang": 4194304,
  "voltooid": false,
  "lock": "c7d72de0-2ba1-4e73-8a4a-9b6de2f1d3e0"
}""",
)
@Serializable
data class BestandsDeelResponse(
    @JsonSchema.Description("De URL van dit BESTANDSDEEL.")
    @JsonSchema.Format("uri")
    @JsonSchema.ReadOnly
    val url: String,
    @JsonSchema.Description("Het volgnummer van dit BESTANDSDEEL (begint bij 1).")
    @JsonSchema.ReadOnly
    val volgnummer: Int,
    @JsonSchema.Description("De grootte van dit BESTANDSDEEL in bytes.")
    @JsonSchema.ReadOnly
    val omvang: Long,
    @JsonSchema.Description("Geeft aan of dit BESTANDSDEEL reeds geüpload is.")
    @JsonSchema.ReadOnly
    val voltooid: Boolean,
    @JsonSchema.Description("Het vergrendel-token (lock) dat vereist is om dit BESTANDSDEEL te uploaden.")
    @JsonSchema.ReadOnly
    val lock: String,
    @JsonSchema.Description("De inhoud van dit BESTANDSDEEL (alleen aanwezig bij voltooid=true, base64-gecodeerd).")
    @JsonSchema.Format("byte")
    val inhoud: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@JsonSchema.Title("EnkelvoudigInformatieObject")
@JsonSchema.Description(
    "Een enkelvoudig informatieobject (document met metadata) zoals opgeslagen in het DRC. " +
        "Bevat alle metadata inclusief downloadlink naar de binaire bestandsinhoud.",
)
@JsonSchema.Example(
    """{
  "url": "https://drc.example.com/api/v1/enkelvoudiginformatieobjecten/550e8400-e29b-41d4-a716-446655440000",
  "identificatie": "DOC-2024-00001",
  "bronorganisatie": "123456782",
  "creatiedatum": "2024-01-15",
  "titel": "Besluit vergunning omgevingsrecht",
  "versie": 1,
  "vertrouwelijkheidaanduiding": "openbaar",
  "auteur": "Gemeente Utrecht",
  "status": "definitief",
  "formaat": "application/pdf",
  "taal": "nld",
  "bestandsnaam": "besluit-2024-001.pdf",
  "inhoud": "https://drc.example.com/api/v1/enkelvoudiginformatieobjecten/550e8400-e29b-41d4-a716-446655440000/download",
  "bestandsomvang": 12345,
  "beschrijving": "Besluit omgevingsvergunning bouwen woning Dorpsstraat 1",
  "beginRegistratie": "2024-01-15T10:30:00",
  "indicatieGebruiksrecht": false,
  "informatieobjecttype": "https://catalogi.example.com/api/v1/informatieobjecttypen/1c35fb6a-a07e-4643-b522-4a68b7deb21d",
  "trefwoorden": ["omgevingsrecht", "vergunning"],
  "inhoudIsVervallen": false,
  "bestandsdelen": [],
  "lock": "",
  "locked": false
}""",
)
@Serializable
@ResourceSegment(ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN)
data class EnkelvoudigInformatieObjectResponse(
    @JsonSchema.Description("De UUID van het INFORMATIEOBJECT.")
    @JsonSchema.Format("uuid")
    @JsonSchema.ReadOnly
    override val id: String,

    @JsonSchema.Description("De URL van dit INFORMATIEOBJECT.")
    @JsonSchema.Format("uri")
    @JsonSchema.ReadOnly
    override val url: String? = null,

    @JsonSchema.Description("Een binnen de organisatie unieke referentie naar het INFORMATIEOBJECT. Maximaal 40 tekens.")
    @JsonSchema.MaxLength(40)
    val identificatie: String? = null,

    @JsonSchema.Description(
        "Het RSIN van de Niet-natuurlijk persoon zijnde de organisatie die het INFORMATIEOBJECT heeft gecreëerd " +
            "of heeft ontvangen en als eerste in een samenwerkingsketen heeft vastgelegd.",
    )
    @JsonSchema.MaxLength(9)
    @JsonSchema.MinLength(9)
    @JsonSchema.Example("\"123456782\"")
    val bronorganisatie: String,

    @JsonSchema.Description(
        "Een datum of een gebeurtenis in de levenscyclus van het INFORMATIEOBJECT (ISO 8601, formaat YYYY-MM-DD).",
    )
    @JsonSchema.Format("date")
    @JsonSchema.Example("\"2024-01-15\"")
    val creatiedatum: LocalDate,

    @JsonSchema.Description("De naam waaronder het INFORMATIEOBJECT formeel bekend is. Maximaal 200 tekens.")
    @JsonSchema.MaxLength(200)
    @JsonSchema.Example("\"Besluit vergunning omgevingsrecht\"")
    val titel: String,

    @JsonSchema.Description(
        "Het (automatische) versienummer van het INFORMATIEOBJECT. Begint bij 1. Elke PUT/PATCH creëert een nieuwe versie.",
    )
    @JsonSchema.ReadOnly
    val versie: Int,

    @JsonSchema.Description(
        "Aanduiding van de mate waarin het INFORMATIEOBJECT voor de openbaarheid bestemd is. " +
            "Mogelijke waarden: openbaar, beperkt_openbaar, intern, zaakvertrouwelijk, vertrouwelijk, " +
            "confidentieel, geheim, zeer_geheim.",
    )
    val vertrouwelijkheidaanduiding: Vertrouwelijkheidaanduiding? = null,

    @JsonSchema.Description(
        "De persoon of organisatie die in de eerste plaats verantwoordelijk is voor het creëren van de inhoud " +
            "van het INFORMATIEOBJECT. Maximaal 200 tekens.",
    )
    @JsonSchema.MaxLength(200)
    @JsonSchema.Example("\"Gemeente Utrecht\"")
    val auteur: String,

    @JsonSchema.Description(
        "Aanduiding van de stand van zaken van een INFORMATIEOBJECT. " +
            "De waarden 'in_bewerking' en 'ter_vaststelling' komen niet voor als het document ontvangen is. " +
            "Wijziging van 'definitief' naar een eerdere status is niet mogelijk.",
    )
    val status: EnkelvoudigInformatieObjectStatus? = null,

    @JsonSchema.Description(
        "Het \"Media Type\" (voorheen \"MIME type\") voor de wijze waarop de inhoud van het INFORMATIEOBJECT is " +
            "vastgelegd in een computerbestand. Voorbeeld: `application/pdf`. " +
            "Zie: https://www.iana.org/assignments/media-types/media-types.xhtml. Maximaal 255 tekens.",
    )
    @JsonSchema.MaxLength(255)
    @JsonSchema.Example("\"application/pdf\"")
    val formaat: String? = null,

    @JsonSchema.Description(
        "Een ISO 639-2/B taalcode waarin de inhoud van het INFORMATIEOBJECT is vastgelegd (3-letterige code). " +
            "Voorbeeld: `nld` (Nederlands), `eng` (Engels). " +
            "Zie: https://www.iso.org/standard/4767.html",
    )
    @JsonSchema.MaxLength(3)
    @JsonSchema.MinLength(3)
    @JsonSchema.Pattern("^[a-z]{3}$")
    @JsonSchema.Example("\"nld\"")
    val taal: String,

    @JsonSchema.Description(
        "De naam van het fysieke bestand waarin de inhoud van het INFORMATIEOBJECT is vastgelegd, inclusief extensie. Maximaal 255 tekens.",
    )
    @JsonSchema.MaxLength(255)
    @JsonSchema.Example("\"besluit-2024-001.pdf\"")
    val bestandsnaam: String? = null,

    @JsonSchema.Description("Download URL van de binaire inhoud van het INFORMATIEOBJECT.")
    @JsonSchema.Format("uri")
    val inhoud: String? = null,

    @JsonSchema.Description("Aantal bytes dat de inhoud van het INFORMATIEOBJECT in beslag neemt.")
    @JsonSchema.Format("int64")
    @JsonSchema.Example("12345")
    val bestandsomvang: Long? = null,

    @JsonSchema.Description("De URL waarmee de inhoud van het INFORMATIEOBJECT op te vragen is. Maximaal 200 tekens.")
    @JsonSchema.Format("uri")
    @JsonSchema.MaxLength(200)
    val link: String? = null,

    @JsonSchema.Description("Een generieke beschrijving van de inhoud van het INFORMATIEOBJECT. Maximaal 1000 tekens.")
    @JsonSchema.MaxLength(1000)
    @JsonSchema.Example("\"Besluit omgevingsvergunning bouwen woning Dorpsstraat 1\"")
    val beschrijving: String? = null,

    @JsonSchema.Description("Een datumtijd in ISO 8601 formaat waarop deze versie van het INFORMATIEOBJECT is aangemaakt of gewijzigd.")
    @JsonSchema.Format("date-time")
    @JsonSchema.ReadOnly
    val beginRegistratie: String,

    @JsonSchema.Description(
        "Indicatie of er beperkingen gelden aangaande het gebruik van het INFORMATIEOBJECT anders dan raadpleging. " +
            "Dit veld mag `null` zijn om aan te geven dat de indicatie nog niet bekend is. " +
            "Als de indicatie gezet is op `true`, moet er ook een Gebruiksrecht aangemaakt zijn.",
    )
    val indicatieGebruiksrecht: Boolean? = null,

    @JsonSchema.Description("De essentiële opmaakaspecten van een INFORMATIEOBJECT, vrij tekstveld (bijv. HTML, formulier, Word-document).")
    val verschijningsvorm: String? = null,

    @JsonSchema.Description(
        "Aanduiding van de rechtskracht van een INFORMATIEOBJECT. " +
            "Mag niet van een waarde zijn voorzien als de `status` de waarde 'in_bewerking' of 'ter_vaststelling' heeft.",
    )
    val ondertekening: Ondertekening? = null,

    @JsonSchema.Description(
        "Uitdrukking van mate van volledigheid en onbeschadigd zijn van digitaal bestand. Bevat het gebruikte algoritme, de berekende waarde en de datum.",
    )
    val integriteit: Integriteit? = null,

    @JsonSchema.Description("URL-referentie naar het INFORMATIEOBJECTTYPE (in de Catalogi API). Maximaal 200 tekens.")
    @JsonSchema.Format("uri")
    @JsonSchema.MaxLength(200)
    @JsonSchema.Example("\"https://catalogi.example.com/api/v1/informatieobjecttypen/1c35fb6a-a07e-4643-b522-4a68b7deb21d\"")
    val informatieobjecttype: String,

    @JsonSchema.Description("Een lijst van trefwoorden gescheiden door comma's.")
    @JsonSchema.Example("[\"omgevingsrecht\", \"vergunning\"]")
    val trefwoorden: List<String> = emptyList(),

    @JsonSchema.Description(
        "Geeft aan of de inhoud van het INFORMATIEOBJECT al dan niet vervallen, dus niet langer geldig is. " +
            "`true` = De inhoud is vervallen. `false` = De inhoud is niet vervallen. Niet opgegeven = veld ontbreekt of is `null`.",
    )
    val inhoudIsVervallen: Boolean? = null,

    @JsonSchema.Description("Lijst van BESTANDSDELen voor chunked upload. Gevuld wanneer het bestand via BESTANDSDELen wordt geüpload.")
    @JsonSchema.ReadOnly
    @kotlinx.serialization.EncodeDefault
    val bestandsdelen: List<BestandsDeelResponse> = emptyList(),

    @JsonSchema.Description("Het huidige vergrendel-token (lock). Leeg wanneer het INFORMATIEOBJECT niet vergrendeld is.")
    @JsonSchema.ReadOnly
    val lock: String,

    @JsonSchema.Description(
        "Geeft aan of het document gelocked is. Alleen als een document gelocked is, mogen er aanpassingen gemaakt worden.",
    )
    @JsonSchema.ReadOnly
    @kotlinx.serialization.EncodeDefault
    val locked: Boolean,

    @SerialName("_expand")
    @JsonSchema.Description("Geëxpandeerde gerelateerde resources, bijv. INFORMATIEOBJECTTYPE of OBJECTINFORMATIEOBJECTen.")
    val expand: JsonObject? = null,
) : ApiEntityResponse

@JsonSchema.Description(
    "De status van het INFORMATIEOBJECT. " +
        "concept = nog in concept; " +
        "in_bewerking = wordt actief bewerkt (checkout); " +
        "ter_vaststelling = ter review/goedkeuring; " +
        "definitief = vastgesteld en onveranderbaar; " +
        "vastgesteld = formeel vastgesteld; " +
        "gearchiveerd = gearchiveerd.",
)
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

@JsonSchema.Description(
    "De vertrouwelijkheidaanduiding van het INFORMATIEOBJECT. " +
        "Van laag naar hoog: openbaar, beperkt_openbaar, intern, zaakvertrouwelijk, " +
        "vertrouwelijk, confidentieel, geheim, zeer_geheim.",
)
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

@JsonSchema.Description("Hash-algoritme voor integriteitscontrole van bestanden.")
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

@JsonSchema.Description("Het type ondertekening van een INFORMATIEOBJECT: analoog (handtekening), digitaal of pki (PKI-certificaat).")
@Serializable
enum class OndertekeningSoort {
    @SerialName("analoog")
    ANALOOG,

    @SerialName("digitaal")
    DIGITAAL,

    @SerialName("pki")
    PKI,
}

@JsonSchema.Title("UnlockRequest")
@JsonSchema.Description("Request-body voor het ontgrendelen (unlock/checkin) van een INFORMATIEOBJECT.")
@JsonSchema.Example("""{"lock": "c7d72de0-2ba1-4e73-8a4a-9b6de2f1d3e0"}""")
@Serializable
data class UnlockEIORequest(
    @JsonSchema.Description("Het vergrendel-token dat werd ontvangen bij het vergrendelen (lock) van het INFORMATIEOBJECT.")
    @JsonSchema.Format("uuid")
    val lock: String,
) : ApiRequest

@JsonSchema.Title("InformatieObjectZoekRequest")
@JsonSchema.Description("Request-body voor het zoeken van INFORMATIEOBJECTen op UUID.")
@JsonSchema.Example(
    """{
  "uuid_In": [
    "550e8400-e29b-41d4-a716-446655440000",
    "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
  ]
}""",
)
@Serializable
data class EIOZoekRequest(
    @JsonSchema.Description("Lijst van UUID's van INFORMATIEOBJECTen om op te zoeken.")
    @SerialName("uuid_In")
    val uuidIn: List<String>,

    @JsonSchema.Description("Komma-gescheiden lijst van gerelateerde resources om te expanderen in het antwoord.")
    @SerialName("expand")
    val expand: String? = null,
) : ApiRequest
