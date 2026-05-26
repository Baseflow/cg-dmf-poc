# Configuratiehandleiding

Deze handleiding geeft gedetailleerde instructies voor het configureren van verschillende componenten om samen te werken met de DMF (als DRC-component).

## Databaseconfiguratie

Zorg ervoor dat de volgende omgevingsvariabelen zijn ingesteld:

| Variabele      | Standaardwaarde                              | Beschrijving                                        |
| -------------- | -------------------------------------------- |-----------------------------------------------------|
| `DB_URL`       | `jdbc:postgresql://localhost:5432/documenten`| JDBC-URL naar de PostgreSQL-database                |
| `DB_USER`      | `documenten`                                 | Databasegebruiker                                   |
| `DB_PASSWORD`  | `documenten`                                 | Databasewachtwoord                                  |
| `DB_POOL_SIZE` | `10`                                         | Maximaal aantal verbindingen in de verbindingspool. |

Het databaseschema wordt automatisch aangemaakt en bijgewerkt wanneer de applicatie start.

## Keycloak-configuratie

Om authenticatie en autorisatie met Keycloak mogelijk te maken, configureert u de volgende omgevingsvariabelen:

| Variabele                 | Standaardwaarde                       | Beschrijving                                                                                                       |
| ------------------------- | ------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `OIDC_ISSUER`             | `http://localhost:8081/realms/cg-dmf` | Issuer-URL van de OIDC-provider die wordt gebruikt om binnenkomende JWT-tokens te valideren                      |
| `ZGW_ALLOWED_CLIENT_IDS`  | `gzac`                                | Komma-gescheiden lijst van `client_id`-waarden die worden geaccepteerd voor ZGW-stijl JWT-authenticatie (GZAC / Valtimo / Open Zaak) |

Zorg ervoor dat de Keycloak-realm en client zijn geconfigureerd om overeen te komen met deze waarden.

OIDC issuer is voor rechtstreeks toegang van gebruikers to the API. Dit gebruiken we o.a. ook voor de beheer interface.

ZGW_ALLOWED_CLIENT_IDS wordt gebruikt door openzaak en/of GZAC om te communiceren met de DMF als service

## Versleuteling (at-rest encryptie van opslaginloggegevens)

Toegangssleutels en geheime sleutels van blobopslag-repositories worden versleuteld opgeslagen in de database
met AES-256-PBE-CBC. De volgende omgevingsvariabelen zijn verplicht:

| Variabele               | Standaardwaarde | Beschrijving                                                                 |
| ----------------------- | --------------- | ---------------------------------------------------------------------------- |
| `ENCRYPTION_SECRET_KEY` | _(geen)_        | Wachtwoordzin voor AES-256-PBE-CBC sleutelafleidng (verplicht)              |
| `ENCRYPTION_SALT`       | _(geen)_        | Hex-salt voor sleutelafleiding; moet een even aantal hexadecimale tekens zijn (verplicht) |

**Waarden genereren:**

```bash
# Genereer een sterke ENCRYPTION_SECRET_KEY (Base64, 32 bytes willekeurig)
openssl rand -base64 32

# Genereer een ENCRYPTION_SALT (hex, 16 bytes willekeurig)
openssl rand -hex 16
```

Sla de gegenereerde waarden op in een geheimenbeheerder (bijv. Kubernetes Secrets, HashiCorp Vault of Azure Key Vault).
Gebruik nooit dezelfde waarden in meerdere omgevingen. Als `ENCRYPTION_SECRET_KEY` of `ENCRYPTION_SALT` gewijzigd worden,
kunnen bestaande versleutelde referenties niet meer worden ontsleuteld — sla de sleutels dus veilig op.

## Blobopslag-configuratie

De DMF ondersteunt meerdere blobopslag-backends (S3-compatibel en Azure Blob Storage) via een genummerd patroon van omgevingsvariabelen. Repositories worden bij het opstarten in de database opgeslagen en zijn daarna ook beheerbaar via de beheerinterface.

### BLOB_STORAGE_* (aanbevolen)

Configureer één of meer repositories met een numeriek achtervoegsel (begin bij 1):

| Variabele                           | Standaardwaarde | Beschrijving                                                |
| ----------------------------------- | --------------- | ----------------------------------------------------------- |
| `BLOB_STORAGE_TYPE1`                | _(verplicht)_   | `S3` of `AZURE_BLOB_STORAGE`                               |
| `BLOB_STORAGE_URL1`                 | _(verplicht)_   | Eindpunt-URL van de opslagdienst                           |
| `BLOB_STORAGE_ACCESS_KEY1`          | _(verplicht)_   | Toegangssleutel / account-naam                             |
| `BLOB_STORAGE_SECRET_KEY1`          | _(verplicht)_   | Geheime sleutel / account-sleutel                          |
| `BLOB_STORAGE_BUCKET1`              | `documenten`    | Bucketnaam / containernaam                                 |
| `BLOB_STORAGE_REGION1`              | _(leeg)_        | Regio (optioneel, afhankelijk van provider)                |
| `BLOB_STORAGE_NAME1`                | `repo-1`        | Leesbare naam voor de repository                           |
| `BLOB_STORAGE_DISABLE_CHECKSUMS1`   | `false`         | Schakel checksums uit (bijv. voor MinIO-compatibiliteit)   |
| `BLOB_STORAGE_DISABLE_CHUNKED_ENCODING1` | `false`   | Schakel chunked encoding uit (bijv. voor bepaalde proxies) |

Voeg een tweede repository toe door de variabelen te herhalen met achtervoegsel `2`, enzovoort.

### Verouderde S3_* variabelen (automatisch geïmporteerd)

Wanneer er **geen** `BLOB_STORAGE_*` variabelen zijn ingesteld maar de verouderde `S3_*` variabelen wel aanwezig zijn, importeert de applicatie deze automatisch als een repository genaamd `legacy-s3` en slaat deze op in de database.

| Variabele        | Standaardwaarde         | Beschrijving                                   |
| ---------------- | ----------------------- | --------------------------------------------- |
| `S3_ENDPOINT`    | `http://localhost:9000` | MinIO / S3-compatibele eindpunt-URL           |
| `S3_ACCESS_KEY`  | `minioadmin`            | Toegangssleutel (gebruikersnaam)              |
| `S3_SECRET_KEY`  | _(verplicht)_           | Geheime sleutel (wachtwoord)                  |
| `S3_BUCKET`      | `default-bucket`        | Bucket gebruikt voor documentopslag           |
| `S3_REGION`      | `eu-west-1`             | AWS-regio                                     |

Na de eerste import is de repository zichtbaar in de beheerinterface en kan deze hernoemd of verder geconfigureerd worden. Migreer zo snel mogelijk naar de `BLOB_STORAGE_*` variabelen: verwijder daarna de `S3_*` variabelen zodat de import niet opnieuw wordt uitgevoerd.

> **Let op:** Als er noch `BLOB_STORAGE_*` noch `S3_*` variabelen zijn ingesteld, laadt de applicatie de opgeslagen repositories uit de database. Dit is de aanbevolen situatie na een volledige migratie.

## Bestandsdelen-configuratie

Bij het aanmaken van een `EnkelvoudigInformatieObject` met een `bestandsomvang` groter dan `BESTANDSDELEN_TRIGGER_SIZE`,
wordt automatisch de bestandsdelen-workflow geactiveerd. De bestanden worden dan opgesplitst in losse delen
die afzonderlijk via `PUT /bestandsdelen/{uuid}` kunnen worden geüpload.

| Variabele                     | Standaardwaarde | Beschrijving                                                                                                        |
| ----------------------------- | --------------- |---------------------------------------------------------------------------------------------------------------------|
| `BESTANDSDELEN_TRIGGER_SIZE`  | `4294967296`    | Minimale bestandsgrootte in bytes (exclusief) waarbij de bestandsdelen-workflow wordt geactiveerd (standaard: 4 GB) |
| `BESTANDSDELEN_CHUNK_SIZE`    | `3221225472`    | Grootte in bytes van elk afzonderlijk bestandsdeel-chunk (standaard: 3 GB)                                          |

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

## WOPI

De DMF implementeert een WOPI-host waarmee WOPI-clients (bijvoorbeeld Collabora of Microsoft 365) documenten kunnen openen en  bewerken in de browser. Zie [docs/wopi.md](wopi.md) voor een volledig overzicht.

| Variabele              | Standaardwaarde | Beschrijving                                                                                                                                                                              |
|------------------------| --------------- |-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `WOPI_ENABLED`         | `false`         | Zet op `true` om de WOPI functionaliteit beschikbaar te maken.                                                                                                                            |
| `WOPI_HOST_BASE_URL`   | _(leeg)_        | Publieke basis-URL van de DMF bereikbaar door de WOPI_client, bijv. `http://localhost:8080`. Het volledige WOPI-endpoint is dan bijv. `http://localhost:8080/wopi/api/v1/files/{file_id}` |
| `WOPI_CLIENT_BASE_URL` | _(leeg)_        | Publieke basis-URL van de WOPI-client bijv. `https://collabora.dev.baseflow.com`                                                                                                          |

## Aanvullende opmerkingen

- Zorg ervoor dat alle omgevingsvariabelen zijn ingesteld voordat u de applicatie start.
- Voor ontwikkeling kunt u een `.env`-bestand gebruiken om de configuratie te vereenvoudigen.
