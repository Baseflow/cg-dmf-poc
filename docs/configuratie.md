# Configuratiehandleiding

Deze handleiding geeft gedetailleerde instructies voor het configureren van DMF voor systeembeheerders.
Voor dag tot dag configuratie voor beheerders van ZGW landschappen, zie [handleiding.md](handleiding.md)

## Databaseconfiguratie

Zorg ervoor dat de volgende omgevingsvariabelen zijn ingesteld:

| Variabele      | Standaardwaarde                               | Beschrijving                                        |
|----------------|-----------------------------------------------|-----------------------------------------------------|
| `DB_URL`       | `jdbc:postgresql://localhost:5432/documenten` | JDBC-URL naar de PostgreSQL-database                |
| `DB_USER`      | `documenten`                                  | Databasegebruiker                                   |
| `DB_PASSWORD`  | `documenten`                                  | Databasewachtwoord                                  |
| `DB_POOL_SIZE` | `10`                                          | Maximaal aantal verbindingen in de verbindingspool. |

Het databaseschema wordt automatisch aangemaakt en bijgewerkt wanneer de applicatie start.

## Authenticatie-configuratie

Om authenticatie en autorisatie met Keycloak en via ZGW authenticatie mogelijk te maken, configureert u de volgende omgevingsvariabelen:

| Variabele                 | Standaardwaarde                       | Beschrijving                                                                                                                                                                                                                                      |
| ------------------------- | ------------------------------------- |---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `OIDC_ISSUER`             | `http://localhost:8081/realms/cg-dmf` | Issuer-URL van de OIDC-provider die wordt gebruikt om binnenkomende JWT-tokens te valideren                                                                                                                                                       |
| `OIDC_RESOURCE_CLIENT_ID` | _(leeg)_                              | Optionele OIDC client-id voor role-resolutie uit `resource_access.<client_id>.roles`. Indien leeg: fallback naar token claims `azp`, daarna `client_id`, en als laatste alle `resource_access.*.roles`.                                           |
| `ADMIN_ROLE`              | `dmf-admin`                           | Vereiste rol voor toegang tot beheer-endpoints onder `/settings/**`.                                                                                                                                                                              |
| `CLIENT_CREDENTIALS`      | _(leeg)_                              | Komma-gescheiden lijst van `client_id:secret`-paren voor ZGW-stijl JWT-authenticatie (GZAC / Valtimo / Open Zaak). Voorbeeld: `gzac:supersecret,valtimo:anothersecret`. Credentials kunnen ook gegenereerd worden met behulp van de admin portal. |

Zorg ervoor dat de Keycloak-realm en client zijn geconfigureerd om overeen te komen met deze waarden.

OIDC issuer is voor rechtstreekse toegang van gebruikers tot de API. Dit gebruiken we om de beheerinterface te faciliteren.
CLIENT_CREDENTIALS wordt gebruikt door GZAC en/of openzaak om te communiceren met de DMF als service

### Rolclaims voor beheer-endpoints (`/settings/**`)

Voor autorisatie op de beheer-endpoints gebruikt de API role-claims op basis van het authenticatietype:

- **OIDC token (`auth-jwt`)**: `realm_access.roles` en `resource_access.<client_id>.roles`
- **ZGW token (`auth-zgw`)**: top-level `roles`

`ADMIN_ROLE` moet voorkomen in de claim-bron die hoort bij het token-type van de aanvraag.

## Versleuteling

Toegangssleutels en geheime sleutels van blobopslag-repositories worden versleuteld opgeslagen in de database
met AES-256-PBE-GCM. De volgende omgevingsvariabelen zijn verplicht:

| Variabele               | Standaardwaarde | Beschrijving                                                                                                                                                  |
|-------------------------|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ENCRYPTION_SECRET_KEY` | _(geen)_        | Wachtwoordzin voor AES-256-PBE-GCM sleutelafleiding (verplicht)                                                                                               |
| `ENCRYPTION_SALT`       | _(geen)_        | Salt voor sleutelafleiding; hex of platte tekst wordt geaccepteerd. Bij gebruik van hex moet deze uit een even aantal hexadecimale tekens bestaan (verplicht) |

**Waarden genereren:**

```bash
# Genereer een sterke ENCRYPTION_SECRET_KEY (Base64, 32 bytes willekeurig)
openssl rand -base64 32

# Genereer een ENCRYPTION_SALT (hex, 16 bytes willekeurig)
openssl rand -hex 16
```

Sla de gegenereerde waarden op in een geheimenbeheerder (bijv. Kubernetes Secrets, HashiCorp Vault of Azure Key Vault).
Gebruik nooit dezelfde waarden in meerdere omgevingen. Als `ENCRYPTION_SECRET_KEY` of `ENCRYPTION_SALT` gewijzigd
worden,
kunnen bestaande versleutelde referenties niet meer worden ontsleuteld — sla de sleutels dus veilig op.

## Blob storage-configuratie

De applicatie ondersteunt S3-compatibele opslag (bijv. MinIO, AWS S3) en Azure Blob Storage.
Configureer één of meerdere opslagrepositories via omgevingsvariabelen met een numeriek achtervoegsel (1, 2, 3, …).

Opslag repositories kunnen worden geconfigureerd met variabelen, of met behulp van de beheerinterface.

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

| Variabele                    | Standaardwaarde | Beschrijving                                                                                                        |
|------------------------------|-----------------|---------------------------------------------------------------------------------------------------------------------|
| `BESTANDSDELEN_TRIGGER_SIZE` | `314572800`    | Minimale bestandsgrootte in bytes (exclusief) waarbij de bestandsdelen-workflow wordt geactiveerd (standaard: 300 MB) |
| `BESTANDSDELEN_CHUNK_SIZE`   | `104857600`    | Grootte in bytes van elk afzonderlijk bestandsdeel-chunk (standaard: 100 MB)                                          |

## OpenZaak-integratie

Om het opgegeven informatieobjecttype te valideren in de catalogus, stel de volgende omgevingsvariabelen in:

Ga naar OpenZaak en navigeer naar API Autorisaties -> Applicaties
- Kies voor Applicatie toevoegen
- Voer een naam in (bijv. DMF)
- Kies een client-id en client-secret voor de communicatie van DMF naar OpenZaak
- Sla de applicatie op en gebruik deze waarden in de onderstaande omgevingsvariabelen.

| Variabele                     | Standaardwaarde                     | Beschrijving                                                      |
|-------------------------------|-------------------------------------|-------------------------------------------------------------------|
| `OPENZAAK_ENDPOINT`           | `https://openzaak.dev.baseflow.com` | Basis-URL van de Open Zaak-instantie                              |
| `OPENZAAK_CLIENT_ID`          | `cg-dmf`                            | Client-ID gebruikt voor authenticatie met Open Zaak               |
| `OPENZAAK_CLIENT_SECRET`      | `baseflow`                          | Client secret gebruikt voor authenticatie met Open Zaak           |
| `OPENZAAK_VALIDATION_ENABLED` | `true`                              | Stel in op `false` om Open Zaak-objecttypevalidatie over te slaan |

Daarnaast moet u deze service registreren in Open Zaak.

* Ga naar de Open Zaak-beheerinterface en navigeer naar `API authorisaties` en selecteer het tabblad `Services`.
* Klik op de knop `Service toevoegen`.
* Voer de volgende informatie in:
    * Kies een label en service slug
    * Type is: `DRC (informatieobjecten)`
    * Api root url: `https://example.com/documenten/api/v1/`
    * Authorisatietype: `ZGW client_id + secret`
    * Client id: ... (zoals gebruikt in DMF, zie ook `CLIENT_CREDENTIALS`)
    * Client secret: ... (zoals gebruikt in DMF)
    * Gebruikers-id: `openzaak`
    * Gebruikersrepresentatie: `Open Zaak`

Hiermee kan OpenZaak de URLs van de DMF-DRC herkennen en objecten aanmaken/valideren in de DMF-DRC.

Ook moeten er Zaak types en informatie object types zijn gemaakt in Open Zaak om later door GZAC gebruikt te kunnen
worden.

## Notificaties (Open Notificaties API)

Notificaties zijn optioneel. Wanneer leeg gelaten, is de integratie automatisch uitgeschakeld.
Stel de volgende omgevingsvariabelen in indien nodig:

| Variabele                | Standaardwaarde | Beschrijving                                                                            |
|--------------------------|-----------------|-----------------------------------------------------------------------------------------|
| `NOTIFICATION_API_URL`   | _(leeg)_        | Basis-URL van de Open Notificaties API, bijv. `https://notificaties.example.com/api/v1` |
| `NOTIFICATION_API_TOKEN` | _(leeg)_        | Bearer-token met de scope `notificaties.publiceren`                                     |
| `NOTIFICATION_KANAAL`    | `documenten`    | Naam van het notificatiekanaal (kanaal) zoals geregistreerd in Open Notificaties        |
| `NOTIFICATION_SOURCE`    | `drc`           | Bronidentificatie verzonden met elke notificatie                                        |

## WOPI

De DMF implementeert een WOPI-host waarmee WOPI-clients (bijvoorbeeld Collabora of Microsoft 365) documenten kunnen
openen en bewerken in de browser. Zie [docs/wopi/wopi.md](wopi/wopi.md) voor een volledig overzicht.

| Variabele                | Standaardwaarde | Beschrijving                                                                                                                                                       |
|--------------------------|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `WOPI_ENABLED`           | `false`         | Zet op `true` om de WOPI functionaliteit beschikbaar te maken.                                                                                                    |
| `WOPI_SLAT_SECRET`       | _(geen)_        | **Verplicht wanneer `WOPI_ENABLED=true`.** Salt voor het encoderen/decoderen van het Short-Lived Access Token (SLAT). Minimaal aanbevolen lengte: 32 tekens. De applicatie start niet op als deze ontbreekt terwijl WOPI is ingeschakeld. |
| `WOPI_SLAT_TTL_SECONDS`  | `3600`          | Levensduur in seconden van het SLAT-token.                                                                                                                        |

## Ingress en paden

De DMF-backend stelt een aantal HTTP-paden beschikbaar. Bij het inrichten van een reverse proxy of ingress-controller
is het van belang te weten welke paden extern bereikbaar moeten zijn en welke beschermd of afgeschermd moeten blijven.

### Cluster intern (geen externe blootstelling nodig)

Deze paden worden uitsluitend gebruikt door Kubernetes-interne componenten (kubelet voor health probes).

| Pad          | Omschrijving                              |
|--------------|-------------------------------------------|
| `/health/*`  | Liveness, readiness en validatie probes   |

### Openbaar (geen authenticatie vereist)

| Pad       | Omschrijving                                              |
|-----------|-----------------------------------------------------------|
| `/docs/*` | API-documentatieportal en OpenAPI-specificaties           |

### Primaire functionaliteit (authenticatie vereist)

Beschikbaar stellen aan consumenten (GZAC, Open Zaak, etc.). Alle verzoeken vereisen een geldig ZGW JWT-token.

| Pad                      | Omschrijving                                                                          |
|--------------------------|---------------------------------------------------------------------------------------|
| `/documenten/api/v1/*`   | VNG Documenten API 1.5.0 — documenten, relaties en bestandsdelen                     |
| `/wopi/api/v1/*`         | WOPI-host — tokenuitgifte (ZGW) en bestandsbewerkingen (kortlevend SLAT-token)        |

### Alleen beheer

Beperk toegang via een apart netwerk, IP-allowlist of interne ingress. Vereist authenticatie én de `dmf-admin`-rol (instelbaar via `ADMIN_ROLE`).

| Pad           | Omschrijving                                                                 |
|---------------|------------------------------------------------------------------------------|
| `/settings/*` | Beheer van applicatie-instellingen, opslag, OIDC-providers en ZGW-koppelingen |

## Aanvullende opmerkingen

- Zorg ervoor dat alle omgevingsvariabelen zijn ingesteld voordat u de applicatie start.
- Voor ontwikkeling kunt u een `.env`-bestand gebruiken om de configuratie te vereenvoudigen.
