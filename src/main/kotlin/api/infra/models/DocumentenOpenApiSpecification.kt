package com.baseflow.api.infra.models

import com.baseflow.api.DOCUMENTEN_API_BASE_PATH
import com.baseflow.api.DOCUMENTEN_API_VERSION
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.Tag

internal class DocumentenOpenApiSpecification : OpenApiSpecification {
    override val name: String get() = "Documenten"

    override val basePath: String get() = DOCUMENTEN_API_BASE_PATH

    override val apiInfo: OpenApiInfo get() = OpenApiInfo(
        title = "Documenten API",
        version = DOCUMENTEN_API_VERSION,
        description = """
        Een API om een documentregistratiecomponent (DRC) te benaderen.

        In een documentregistratiecomponent worden INFORMATIEOBJECTen opgeslagen. Een
        INFORMATIEOBJECT is een digitaal document voorzien van meta-gegevens.
        INFORMATIEOBJECTen kunnen aan andere objecten zoals zaken en besluiten worden
        gerelateerd (maar dat hoeft niet) en kunnen GEBRUIKSRECHTen hebben.

        **Uploaden van bestanden**

        Bestanden kunnen groter zijn dan de minimum die door providers ondersteund moet worden.
        Voor kleine bestanden kan de inhoud base64-encoded meegestuurd worden in de JSON.
        Voor grote bestanden (>4GB) moet de chunked upload workflow gebruikt worden via BESTANDSDELen.

        **Afhankelijkheden**

        Deze API is afhankelijk van:
        * Catalogi API
        * Notificaties API
        * Autorisaties API *(optioneel)*
        * Zaken API *(optioneel)*

        **Autorisatie**

        Deze API vereist autorisatie via JWT tokens.
    """.trimIndent(),
        contact = OpenApiInfo.Contact(
            email = "standaarden.ondersteuning@vng.nl",
            url = "https://vng-realisatie.github.io/gemma-zaken",
        ),
        license = OpenApiInfo.License(
            name = "EUPL 1.2",
            url = "https://opensource.org/licenses/EUPL-1.2",
        ),
    )

    override val tags: List<Tag> get() = listOf(
        Tag("enkelvoudiginformatieobjecten", "Beheer van document registraties, bestanden en hun metadata"),
        Tag("objectinformatieobjecten", "Koppelen van documenten aan objecten"),
        Tag("subjectinformatieobjecten", "Uitbreiding voor niet-Zaken objecten"),
        Tag("bestandsdelen", "Chunked upload voor grote bestanden"),
        Tag("audittrail", "Audit log regels per INFORMATIEOBJECT"),
        Tag("admin", "Interne beheerfuncties voor opslagconfiguratie (niet onderdeel van de publieke API)"),
    )
}
