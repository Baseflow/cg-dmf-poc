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
| `ZGW_CLIENT_SECRETS`      | _(leeg)_                              | Komma-gescheiden lijst van `client_id:secret`-paren voor ZGW-stijl JWT-authenticatie (GZAC / Valtimo / Open Zaak). Voorbeeld: `gzac:supersecret,valtimo:anothersecret`. Tokens van clients die niet in deze lijst staan worden geweigerd. |

Zorg ervoor dat de Keycloak-realm en client zijn geconfigureerd om overeen te komen met deze waarden.

OIDC issuer is voor rechtstreeks toegang van gebruikers to the API. Dit gebruiken we o.a. ook voor de beheer interface.

ZGW_CLIENT_SECRETS wordt gebruikt door openzaak en/of GZAC om te communiceren met de DMF als service

## Versleuteling (at-rest encryptie van opslaginloggegevens)

Toegangssleutels en geheime sleutels van blobopslag-repositories worden versleuteld opgeslagen in de database
met AES-256-PBE-GCM. De volgende omgevingsvariabelen zijn verplicht:

| Variabele               | Standaardwaarde | Beschrijving                                                                 |
| ----------------------- | --------------- | ---------------------------------------------------------------------------- |
| `ENCRYPTION_SECRET_KEY` | _(geen)_        | Wachtwoordzin voor AES-256-PBE-GCM sleutelafleiding (verplicht)             |
| `ENCRYPTION_SALT`       | _(geen)_        | Salt voor sleutelafleiding; hex of platte tekst wordt geaccepteerd. Bij gebruik van hex moet deze uit een even aantal hexadecimale tekens bestaan (verplicht) |

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

## Blob storage-configuratie

De applicatie ondersteunt S3-compatibele opslag (bijv. MinIO, AWS S3) en Azure Blob Storage.
Configureer één of meerdere opslagrepositories via omgevingsvariabelen met een numeriek achtervoegsel (1, 2, 3, …).

| Variabele                          | Vereist | Beschrijving                                                                 |
| ---------------------------------- | ------- | ---------------------------------------------------------------------------- |
| `BLOB_STORAGE_NAME<N>`             | nee     | Leesbare naam voor de repository (standaard: `repo-<N>`)                    |
| `BLOB_STORAGE_TYPE<N>`             | ja      | Opslagtype: `S3` of `Azure Blob Storage`                                    |
| `BLOB_STORAGE_URL<N>`              | ja      | Eindpunt-URL (S3: `http://minio:9000`, Azure: `https://<account>.blob.core.windows.net`) |
| `BLOB_STORAGE_ACCESS_KEY<N>`       | ja      | Toegangssleutel (S3) of accountnaam (Azure)                                 |
| `BLOB_STORAGE_SECRET_KEY<N>`       | ja      | Geheime sleutel (S3) of accountsleutel (Azure)                              |
| `BLOB_STORAGE_BUCKET<N>`           | nee     | Bucketnaam (S3) of containernaam (Azure) (standaard: `documenten`)          |
| `BLOB_STORAGE_REGION<N>`           | nee     | Regio (alleen S3, bijv. `eu-west-1`)                                        |
| `BLOB_STORAGE_DISABLE_CHECKSUMS<N>`         | nee     | Zet op `true` als het eindpunt geen AWS checksum-extensies ondersteunt      |
| `BLOB_STORAGE_DISABLE_CHUNKED_ENCODING<N>`  | nee     | Zet op `true` als het eindpunt of een tussenliggende proxy geen chunked transfer encoding ondersteunt |

De volgende variabelen gelden globaal voor alle geconfigureerde repositories (geen numeriek achtervoegsel):

| Variabele                                | Standaard | Beschrijving                                                                                          |
| ---------------------------------------- | --------- | ----------------------------------------------------------------------------------------------------- |
| `BLOB_STORAGE_CONNECT_TIMEOUT_SECONDS`   | `10`      | Maximale tijd in seconden om een TCP-verbinding op te bouwen                                          |
| `BLOB_STORAGE_READ_WRITE_TIMEOUT_SECONDS`| `10`      | Maximale tijd in seconden tussen twee opeenvolgende bytes tijdens een overdracht                      |
| `BLOB_STORAGE_MAX_IDLE_SECONDS`          | `300`     | Maximale tijd in seconden dat een verbinding inactief in de pool mag blijven voordat deze wordt gesloten |

**Voorbeeld — één S3-repository:**

```env
BLOB_STORAGE_NAME1=minio-local
BLOB_STORAGE_TYPE1=S3
BLOB_STORAGE_URL1=http://localhost:9000
BLOB_STORAGE_ACCESS_KEY1=minioadmin
BLOB_STORAGE_SECRET_KEY1=minioadmin
BLOB_STORAGE_BUCKET1=documenten
```

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
  * Client id: ... (zoals gebruikt in DMF, zie ook `ZGW_CLIENT_SECRETS`)
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
