# Configuratiehandleiding

Deze handleiding geeft gedetailleerde instructies voor het configureren van verschillende componenten om samen te werken met de DMF (als DRC-component).

## Databaseconfiguratie

Zorg ervoor dat de volgende omgevingsvariabelen zijn ingesteld:

| Variabele      | Standaardwaarde                              | Beschrijving                         |
| -------------- | -------------------------------------------- | ------------------------------------ |
| `DB_URL`       | `jdbc:postgresql://localhost:5432/documenten`| JDBC-URL naar de PostgreSQL-database |
| `DB_USER`      | `documenten`                                 | Databasegebruiker                    |
| `DB_PASSWORD`  | `documenten`                                 | Databasewachtwoord                   |

Het databaseschema wordt automatisch aangemaakt en bijgewerkt wanneer de applicatie start.

## S3-configuratie

MinIO of een andere S3 opslag worden gebruikt voor objectopslag. Stel de volgende omgevingsvariabelen in:

| Variabele        | Standaardwaarde         | Beschrijving                                   |
| ---------------- | ----------------------- | --------------------------------------------- |
| `S3_ENDPOINT`    | `http://localhost:9000` | MinIO / S3-compatibele eindpunt-URL           |
| `S3_ACCESS_KEY`  | `minioadmin`            | Toegangssleutel (gebruikersnaam)              |
| `S3_SECRET_KEY`  | `minioadmin`            | Geheime sleutel (wachtwoord)                  |
| `S3_BUCKET`      | `documenten`            | Bucket gebruikt voor documentopslag           |


## Keycloak-configuratie

Om authenticatie en autorisatie met Keycloak mogelijk te maken, configureert u de volgende omgevingsvariabelen:

| Variabele                 | Standaardwaarde                       | Beschrijving                                                                                                       |
| ------------------------- | ------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `OIDC_ISSUER`             | `http://localhost:8081/realms/cg-dmf` | Issuer-URL van de OIDC-provider die wordt gebruikt om binnenkomende JWT-tokens te valideren                      |
| `ZGW_ALLOWED_CLIENT_IDS`  | `gzac`                                | Komma-gescheiden lijst van `client_id`-waarden die worden geaccepteerd voor ZGW-stijl JWT-authenticatie (GZAC / Valtimo / Open Zaak) |

Zorg ervoor dat de Keycloak-realm en client zijn geconfigureerd om overeen te komen met deze waarden.

OIDC issuer is voor rechtstreeks toegang van gebruikers to the API. Dit gebruiken we o.a. ook voor de beheer interface.

ZGW_ALLOWED_CLIENT_IDS wordt gebruikt door openzaak en/of GZAC om te communiceren met de DMF als service

## OpenZaak-integratie

Om te integreren met OpenZaak, stel de volgende omgevingsvariabelen in:

| Variabele                      | Standaardwaarde                     | Beschrijving                                             |
| ------------------------------ | ----------------------------------- | ------------------------------------------------------- |
| `OPENZAAK_ENDPOINT`            | `https://openzaak.dev.baseflow.com` | Basis-URL van de Open Zaak-instantie                    |
| `OPENZAAK_CLIENT_ID`           | `cg-dmf`                            | Client-ID gebruikt voor authenticatie met Open Zaak     |
| `OPENZAAK_CLIENT_SECRET`       | `baseflow`                          | Client secret gebruikt voor authenticatie met Open Zaak |
| `OPENZAAK_VALIDATION_ENABLED`  | `true`                              | Stel in op `false` om Open Zaak-objecttypevalidatie over te slaan |

Daarnaast moet u deze service registreren in Open Zaak.
* Ga naar de Open Zaak-beheerinterface en navigeer naar `API authorisaties` en selecteer het tabblad `Services`.
* Klik op de knop `Service toevoegen`.
* Voer de volgende informatie in:
  * Kies een label en service slug
  * Type is: `DRC (informatieobjecten)`
  * Api root url: `https://example.com/documenten/api/v1/`
  * Authorisatietype: `ZGW client_id + secret`
  * Client id: ... (zoals gebruikt in DMF, zie ook `ZGW_ALLOWED_CLIENT_IDS`)
  * Client secret: ... (zoals gebruikt in DMF)
  * Gebruikers-id: `openzaak`
  * Gebruikersrepresentatie: `Open Zaak`

Ook moeten er Zaak types en informatie object types zijn gemaakt in Open Zaak om later door GZAC gebruikt te kunnen worden.

## Notificaties (Open Notificaties API)

Notificaties zijn optioneel. Wanneer leeg gelaten, is de integratie automatisch uitgeschakeld.
Stel de volgende omgevingsvariabelen in indien nodig:

| Variabele                 | Standaardwaarde | Beschrijving                                                                           |
| ------------------------- | --------------- | ------------------------------------------------------------------------------------- |
| `NOTIFICATION_API_URL`    | _(leeg)_        | Basis-URL van de Open Notificaties API, bijv. `https://notificaties.example.com/api/v1` |
| `NOTIFICATION_API_TOKEN`  | _(leeg)_        | Bearer-token met de scope `notificaties.publiceren`                                   |
| `NOTIFICATION_KANAAL`     | `documenten`    | Naam van het notificatiekanaal (kanaal) zoals geregistreerd in Open Notificaties      |
| `NOTIFICATION_SOURCE`     | `drc`           | Bronidentificatie verzonden met elke notificatie                                      |

## Aanvullende opmerkingen

- Zorg ervoor dat alle omgevingsvariabelen zijn ingesteld voordat u de applicatie start.
- Voor ontwikkeling kunt u een `.env`-bestand gebruiken om de configuratie te vereenvoudigen.
