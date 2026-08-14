# WOPI integratie

## Overzicht

[WOPI (Web Application Open Platform Interface)](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/) is een protocol waarmee een webtoepassing documenten kan bekijken en bewerken via een externe opslaan-server, zonder de bestanden zelf te downloaden.

De DMF implementeert een **WOPI-host** zodat een WOPI-client zoals [Collabora Online](https://www.collaboraoffice.com/collabora-online/) documenten rechtstreeks uit de DRC kan openen en tonen.

---

## WOPI-endpoints

De WOPI-routes zijn beschikbaar onder het basispad `/wopi/api/v1`.

### Token (DMF-specifiek)

| Endpoint                        | Method | X-WOPI-Override | WOPI-operatie                | Beschrijving                                                                         | Geïmplementeerd |
| ------------------------------- | ------ | --------------- | ---------------------------- | ------------------------------------------------------------------------------------ | :-------------: |
| `/wopi/api/v1/token/{file_id}`  | POST   | —               | IssueToken *(DMF-specifiek)* | Geeft een kortlopend toegangstoken (SLAT) terug voor het opgegeven document.         | ✅ |

### Host page (DMF-specifiek)

| Endpoint                                  | Method | Beschrijving                                                                                                                                                             | Geïmplementeerd |
| ------------------------------------------ | ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | :--------------: |
| `/wopi/files/{file_id}?wopiClient={url}`   | GET    | Retourneert een pagina die automatisch een `POST` doet naar de opgegeven WOPI-client-URL, met een correct opgebouwde `WOPISrc` en het toegangstoken. Vereist het SLAT-token als `access_token` query parameter of `Authorization: Bearer`-header. | ✅ |

### Files

| Endpoint                                 | Method | X-WOPI-Override                                            | WOPI-operatie      | Beschrijving                                                                                                            | Geïmplementeerd |
| ---------------------------------------- | ------ |------------------------------------------------------------| ------------------ |-------------------------------------------------------------------------------------------------------------------------|:---------------:|
| `/wopi/api/v1/files/{file_id}`           | GET    | —                                                          | [CheckFileInfo](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/checkfileinfo) | Geeft metadata terug (bestandsnaam, grootte, versie, rechten) als JSON.                                                 |        ✅        |
| `/wopi/api/v1/files/{file_id}`           | POST   | `LOCK`                                                     | [Lock](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/lock)| Vergrendelt het document.                                                                                               |        ✅        |
| `/wopi/api/v1/files/{file_id}`           | POST   | `REFRESH_LOCK`                                             | [RefreshLock](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/refreshlock) | Vergrendelt het document. Als het bestand al vergrendeld is met hetzelfde token, wordt de vergrendeling vernieuwd.      |        ❌         |
| `/wopi/api/v1/files/{file_id}`           | POST   | `LOCK` | [UnlockAndRelock](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/unlockandrelock) | Vervangt een bestaande vergrendeling door een nieuwe. Vereist de `X-WOPI-OldLock`- en `X-WOPI-Lock`-request headers.                                                                  |        ✅        |
| `/wopi/api/v1/files/{file_id}`           | POST   | `UNLOCK`                                                   | [Unlock](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/unlock) | Ontgrendelt het document.                                                                                               |        ✅        |
| `/wopi/api/v1/files/{file_id}`           | POST   | `GET_LOCK`                                                 | [GetLock](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/getlock) | Geeft de huidige vergrendelingswaarde van het document terug.                                                           |        ❌        |
| `/wopi/api/v1/files/{file_id}`           | POST   | `RENAME_FILE`                                              | [RenameFile](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/renamefile) | Hernoemt het document via de `X-WOPI-RequestedName` header.                                                             |        ✅        |
| `/wopi/api/v1/files/{file_id}`           | POST   | `DELETE`                                                   | [DeleteFile](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/deletefile) | Verwijdert het document, mits het niet vergrendeld is en geen referenties heeft.                                        |        ✅        |
| `/wopi/api/v1/files/{file_id}`           | POST   | `PUT_RELATIVE`                                             | [PutRelativeFile](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/putrelativefile) | Maakt een nieuwe versie of kopie aan van het document op basis van `X-WOPI-RelativeTarget` of `X-WOPI-SuggestedTarget`. |        ✅        |
| `/wopi/api/v1/files/{file_id}`           | POST   | `GET_SHARE_URL`                                            | [GetShareUrl](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/getshareurl) | Geeft een deelbare URL terug voor het document (bijv. voor view-only toegang).                                          |        ❌        |
| `/wopi/api/v1/files/{file_id}/contents`  | GET    | —                                                          | [GetFile](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/getfile) | Streamt de binaire bestandsinhoud van het document.                                                                     |        ✅        |
| `/wopi/api/v1/files/{file_id}/contents`  | POST   | `PUT`                                                      | [PutFile](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/putfile) | Slaat nieuwe bestandsinhoud op (vereist een actieve vergrendeling voor bestaande documenten).                           |        ✅        |

### Containers

| Endpoint                                                          | Method | X-WOPI-Override      | WOPI-operatie      | Beschrijving                                                                                   | Geïmplementeerd |
| ----------------------------------------------------------------- | ------ | -------------------- | ------------------ | ---------------------------------------------------------------------------------------------- | :-------------: |
| `/wopi/api/v1/containers/{container_id}`                          | GET    | —                    | [CheckContainerInfo](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/containers/checkcontainerinfo) | Geeft metadata en capabilities terug voor de opgegeven container. | ❌ |
| `/wopi/api/v1/containers/{container_id}`                          | POST   | `DELETE`             | [DeleteContainer](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/containers/deletecontainer) | Verwijdert de container en alle inhoud. | ❌ |
| `/wopi/api/v1/containers/{container_id}`                          | POST   | `RENAME_CONTAINER`   | [RenameContainer](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/containers/renamecontainer) | Hernoemt de container. | ❌ |
| `/wopi/api/v1/containers/{container_id}/children`                 | GET    | —                    | [EnumerateChildren](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/containers/enumeratechildren) | Geeft een lijst van bestanden en subcontainers terug. | ❌ |
| `/wopi/api/v1/containers/{container_id}/children/containers`      | POST   | —                    | [CreateChildContainer](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/containers/createchildcontainer) | Maakt een nieuwe subcontainer aan. | ❌ |
| `/wopi/api/v1/containers/{container_id}/children/files`           | POST   | —                    | [CreateChildFile](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/containers/createchildfile) | Maakt een nieuw bestand aan in de container. | ❌ |

### Ecosystem

| Endpoint                  | Method | X-WOPI-Override | WOPI-operatie      | Beschrijving                                                                                   | Geïmplementeerd |
| ------------------------- | ------ | --------------- | ------------------ | ---------------------------------------------------------------------------------------------- | :-------------: |
| `/wopi/api/v1/ecosystem`  | GET    | —               | [CheckEcosystem](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/ecosystem/checkecosystem) | Geeft de root-URL terug voor de WOPI-host en ondersteunde capabilities. | ❌ |

---

## WOPI-client instellen

Om de WOPI-host implementatie te kunnen testen is het nodig om een WOPI-client te configureren. Er zijn verschillende WOPI-clients beschikbaar, maar een eenvoudig te installeren en veel gebruikte client is [Collabora Online](https://www.collaboraonline.com/).
Wij gaan in dit hoofdstuk ook uit van de Collabora Online WOPI-client maar instructies voor andere clients zijn vergelijkbaar.

### Vereisten

- Een draaiende WOPI-client (bijv. Collabora Online via Docker).
- De DMF moet bereikbaar zijn vanuit de WOPI-client via HTTP(S).

---

## Configuratie

Stel de volgende omgevingsvariabelen in om WOPI te configureren:

| Variabele               | Standaardwaarde | Beschrijving                                                                                      |
|-------------------------|-----------------|-----------------------------------------------------------------------------------------------------|
| `WOPI_ENABLED`          | `false`         | Zet op `true` om de WOPI-routes in te schakelen                                                   |
| `WOPI_SLAT_SECRET`      | _(geen)_        | **Verplicht wanneer `WOPI_ENABLED=true`.** Salt voor het Short-Lived Access Token (SLAT). Minimaal aanbevolen lengte: 32 tekens. |
| `WOPI_SLAT_TTL_SECONDS` | `3600`          | Levensduur in seconden van het SLAT-token.                                                         |

`WOPI_HOST_BASE_URL` en `WOPI_CLIENT_BASE_URL` in het voorbeeld hieronder zijn **geen omgevingsvariabelen van de DMF** — ze worden nergens in de applicatiecode gelezen. Het zijn illustratieve placeholders voor basis-URLs die de **frontend/integrator** zelf moet kennen:
- de publieke basis-URL van de DMF, bereikbaar vanuit de WOPI-clientcontainer (bijv. `https://dmf.example.com`), gebruikt om de `WOPISrc`-parameter op te bouwen;
- de publieke URL van de WOPI-clientinstantie (bijv. `https://collabora.example.com`), gebruikt als doel van het formulier.

> **Let op:** de DMF-basis-URL moet bereikbaar zijn vanuit de WOPI-clientcontainer.

---

## Document openen via een WOPI-client (bijv. Collabora Online)

De eenvoudigste manier om een document te openen is via de WOPI Host pagina van de DMF zelf (`GET /wopi/files/{file_id}`).

### Stappen

1. Vraag een kortlopend toegangstoken (SLAT) op via `POST /wopi/api/v1/token/{file_id}`.
2. Laad (bijv. in een `<iframe>`, of door de browser te redirecten) de host page van de DMF:

   ```
   GET {DMF_BASE_URL}/wopi/files/{file_id}?wopiClient={WOPI_CLIENT_PAGE_URL}&access_token={SLAT}
   ```

   - `{DMF_BASE_URL}` is het adres waarop de DMF voor de gebruiker bereikbaar is.
   - `{WOPI_CLIENT_PAGE_URL}` is de volledige, URL-encoded pagina-URL van de WOPI-client (bijv. `https://collabora.example.com/browser/<hash>/cool.html`). Deze is clientspecifiek en meestal op te vragen via de discovery-endpoint van de WOPI-client.
   - `{SLAT}` mag in plaats van de query parameter ook worden meegegeven via de `Authorization: Bearer`-header.

   Voorbeeld:

   ```
   GET https://dmf.example.com/wopi/files/dd3283aa-04c1-4f37-809c-345606ecddc9?wopiClient=https%3A%2F%2Fcollabora.example.com%2Fbrowser%2F4610258811%2Fcool.html&access_token=eyJ...
   ```

3. De DMF retourneert een HTML-pagina die automatisch een `POST` doet naar de WOPI-client met de juiste `WOPISrc`- en 
   `access_token`-velden. De WOPI-client haalt het document vervolgens zelf op bij de DMF via `/wopi/api/v1/files/{file_id}`.

> **Let op:** [`wopi_test.html`](../../docs/wopi/wopi_test.html) in de projectroot demonstreert nog de oudere, 
> handmatige formulier-aanpak en is niet bijgewerkt naar de `/wopi/files/{file_id}`-hostpagina hierboven. Gebruik dit
> voorbeeld als referentie om zelf een WOPI Host pagina te bouwen.

---
