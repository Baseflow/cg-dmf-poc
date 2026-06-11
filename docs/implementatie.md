# Documenten API — Implementatiestatus

Implementatiestatus van de [VNG Documenten API 1.5.0](https://vng-realisatie.github.io/gemma-zaken/standaard/documenten/) endpoints.
De draaiende applicatie heeft op OpenAPI gebaseerd API-verkenner op `/docs` en/of vanuit de admin portal.

Beschikbare endpoints:

| URL                                   | Inhoud                                     |
|---------------------------------------|--------------------------------------------|
| `/docs`                               | Overzichtspagina met links naar alle specs |
| `/docs/swaggerui/documenten-api.html` | Swagger UI — Documenten API                |
| `/docs/swaggerui/wopi-api.html`       | Swagger UI — WOPI API                      |
| `/docs/openapi/documenten.json`       | Gegenereerde OpenAPI spec (JSON)           |
| `/docs/openapi/wopi.json`             | Gegenereerde OpenAPI spec (JSON)           |

## enkelvoudiginformatieobjecten

| Endpoint                       | Method | Beschrijving                      | Status |
|--------------------------------|--------|-----------------------------------|--------|
| `/`                            | GET    | Lijst opvragen (met filters)      | ✅      |
| `/`                            | POST   | Nieuw object aanmaken             | ✅      |
| `/{uuid}`                      | GET    | Enkel object opvragen             | ✅      |
| `/{uuid}`                      | PUT    | Volledig bijwerken                | ✅      |
| `/{uuid}`                      | PATCH  | Gedeeltelijk bijwerken            | ✅      |
| `/{uuid}`                      | DELETE | Verwijderen                       | ✅      |
| `/{uuid}`                      | HEAD   | Headers opvragen                  | ✅      |
| `/{uuid}/download`             | GET    | Binaire inhoud downloaden         | ✅      |
| `/{uuid}/lock`                 | POST   | Vergrendelen                      | ✅      |
| `/{uuid}/unlock`               | POST   | Ontgrendelen                      | ✅      |
| `/{uuid}/_zoek`                | POST   | Zoeken op basis van verzoekinhoud | ✅      |
| `/{uuid}/audittrail`           | GET    | Audittrail opvragen               | ✅      |
| `/{uuid}/audittrail/{at_uuid}` | GET    | Enkel audittrailrecord opvragen   | ✅      |

## objectinformatieobject

| Endpoint  | Method | Beschrijving               | Status          |
|-----------|--------|----------------------------|-----------------|
| `/`       | GET    | Lijst opvragen             | ✅               |
| `/`       | POST   | Relatie aanmaken           | ✅               |
| `/`       | DELETE | Bulk-verwijderen op filter | ✅ experimenteel |
| `/{uuid}` | GET    | Enkel record opvragen      | ✅               |
| `/{uuid}` | DELETE | Verwijderen                | ✅               |
| `/{uuid}` | HEAD   | Headers opvragen           | ✅               |

## bestandsdelen

| Endpoint  | Method | Beschrijving          | Status |
|-----------|--------|-----------------------|--------|
| `/{uuid}` | PUT    | Bestandsdeel uploaden | ✅      |

## Experimentele uitbreidingen

Deze functies zijn geïmplementeerd maar vallen buiten de VNG Documenten API 1.5.0 specificatie. Ze kunnen zonder aankondiging wijzigen.

### enkelvoudiginformatieobjecten — extra filterparameters op `GET /`

De standaard API definieert een beperkte set queryfilters. De volgende parameters zijn als uitbreiding toegevoegd:

| Parameter                                         | Beschrijving                                            |
|---------------------------------------------------|---------------------------------------------------------|
| `informatieobjecttype`                            | Filter op URL-referentie naar het informatieobjecttype  |
| `vertrouwelijkheidaanduiding`                     | Filter op vertrouwelijkheidaanduiding                   |
| `titel`                                           | Filter op titel (hoofdletterongevoelig, bevat)          |
| `auteur`                                          | Filter op auteur (hoofdletterongevoelig, bevat)         |
| `status`                                          | Filter op status                                        |
| `beschrijving`                                    | Filter op beschrijving (hoofdletterongevoelig, bevat)   |
| `trefwoorden__overlap`                            | Filter op trefwoorden (overlap, kommagescheiden)        |
| `locked`                                          | Filter op vergrendeld (`true`) of ontgrendeld (`false`) |
| `creatiedatum__gte` / `creatiedatum__lte`         | Filter op creatiedatum (datum)                          |
| `registratiedatum__gte` / `registratiedatum__lte` | Filter op beginRegistratie (datum-tijd)                 |
| `ordering`                                        | Sortering op één of meer velden (kommagescheiden)       |
| `objectinformatieobjecten__object`                | Filter op URL-referentie naar het gerelateerde object   |
| `objectinformatieobjecten__objectType`            | Filter op objecttype van het gerelateerde object        |

### objectinformatieobject — paginering op `GET /`

De standaard levert een platte array zonder paginering. Met de experimentele parameters `page` en `pageSize` wordt een gepagineerde response teruggegeven. Dit werkt op dezelfde manier als bij het `enkelvoudiginformatieobjecten` endpoint.

### objectinformatieobject — bulk-verwijderen op `DELETE /`

Niet-standaard endpoint dat alle relaties verwijdert die voldoen aan een filter. Verplicht precies één van de queryparameters `informatieobject` (URL naar een EIO) of `object` (URL naar een willekeurig object). Geeft 204 bij succes, 404 als er geen overeenkomende relaties zijn.

## Niet ondersteund

De volgende onderdelen van de Documenten API zijn buiten scope en niet geïmplementeerd:

- **gebruiksrechten** — autorisatieregels per document
- **verzendingen** — registratie van fysieke en digitale verzendingen
- **API scopes** — autorisatie op basis van OAuth-scopes

## Overig

| Functionaliteit   | Status                                                                                |
|-------------------|---------------------------------------------------------------------------------------|
| Open Notificaties | Optioneel — zie [configuratie.md](configuratie.md#notificaties-open-notificaties-api) |
| WOPI-host         | Optioneel — zie [wopi/wopi.md](wopi/wopi.md)                                          |
| NLX               | Niet ondersteund                                                                      |
