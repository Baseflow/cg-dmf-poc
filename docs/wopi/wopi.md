# WOPI integratie

## Overzicht

[WOPI (Web Application Open Platform Interface)](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/) is een protocol waarmee een webtoepassing documenten kan bekijken en bewerken via een externe opslaan-server, zonder de bestanden zelf te downloaden.

De DMF implementeert een **WOPI-host** zodat een WOPI-client zoals [Collabora Online](https://www.collaboraoffice.com/collabora-online/) documenten rechtstreeks uit de DRC kan openen en tonen.

---

## Geïmplementeerde WOPI-endpoints

De WOPI-routes zijn beschikbaar onder het basispad `/wopi/api/v1`.

| Endpoint                                    | Method | X-WOPI-Override header         | WOPI-operatie      | Beschrijving                                                                                          | Geïmplementeerd |
| ------------------------------------------- | ------ | ------------------------------ | ------------------ | ----------------------------------------------------------------------------------------------------- | :-------------: |
| `/wopi/api/v1/token/{file_id}`              | POST   | —                              | IssueToken *(DMF-specifiek)* | Geeft een kortlopend toegangstoken (SLAT) terug voor het opgegeven document.            | ✅ |
| `/wopi/api/v1/files/{file_id}`              | GET    | —                              | [CheckFileInfo](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/checkfileinfo) | Geeft metadata terug (bestandsnaam, grootte, versie, rechten) als JSON. | ✅ |
| `/wopi/api/v1/files/{file_id}`              | POST   | `LOCK`                         | [Lock](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/lock) / [RefreshLock](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/refreshlock) | Vergrendelt het document. Als het bestand al vergrendeld is met hetzelfde token, wordt de vergrendeling vernieuwd (RefreshLock). | ✅ |
| `/wopi/api/v1/files/{file_id}`              | POST   | `LOCK` + `X-WOPI-OldLock`     | [UnlockAndRelock](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/unlockandrelock) | Vervangt een bestaande vergrendeling door een nieuwe. | ✅ |
| `/wopi/api/v1/files/{file_id}`              | POST   | `UNLOCK`                       | [Unlock](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/unlock) | Ontgrendelt het document. | ✅ |
| `/wopi/api/v1/files/{file_id}`              | POST   | `RENAME_FILE`                  | [RenameFile](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/renamefile) | Hernoemt het document via de `X-WOPI-RequestedName` header. | ✅ |
| `/wopi/api/v1/files/{file_id}`              | POST   | `DELETE`                       | [DeleteFile](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/deletefile) | Verwijdert het document, mits het niet vergrendeld is en geen referenties heeft. | ✅ |
| `/wopi/api/v1/files/{file_id}`              | POST   | `PUT_RELATIVE`                 | [PutRelativeFile](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/putrelativefile) | Maakt een nieuwe versie of kopie aan van het document op basis van `X-WOPI-RelativeTarget` of `X-WOPI-SuggestedTarget`. | ✅ |
| `/wopi/api/v1/files/{file_id}/contents`     | GET    | —                              | [GetFile](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/getfile) | Streamt de binaire bestandsinhoud van het document. | ✅ |
| `/wopi/api/v1/files/{file_id}/contents`     | POST   | `PUT`                          | [PutFile](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/putfile) | Slaat nieuwe bestandsinhoud op (vereist een actieve vergrendeling voor bestaande documenten). | ✅ |
| `/wopi/api/v1/containers/{container_id}`    | GET    | —                              | [CheckContainerInfo](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/containers/checkcontainerinfo) | Niet ondersteund in deze implementatie (geeft HTTP 501 terug). | ❌ |

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

| Variabele              | Standaardwaarde | Beschrijving                                                                                      |
|------------------------| --------------- | ------------------------------------------------------------------------------------------------- |
| `WOPI_ENABLED`         | `false`         | Zet op `true` om de WOPI-routes in te schakelen                                                   |
| `WOPI_CLIENT_BASE_URL` | _(leeg)_        | Publieke URL van de WOPI-client instantie, bijv. `https://collabora.example.com`                  |
| `WOPI_HOST_BASE_URL`   | _(leeg)_        | Publieke basis-URL van de DMF die door de WOPI-client bereikbaar is, bijv. `https://dmf.example.com` |

> **Let op:** `WOPI_HOST_BASE_URL` moet bereikbaar zijn vanuit de WOPI-client container.

---

## Document openen via een WOPI-client (bijv. Collabora Online)

Een frontend opent een document in een WOPI-client door een HTML-formulier te `POST`en naar de client-URL. Het onderstaande voorbeeld gebruikt Collabora Online, maar de structuur is vergelijkbaar voor andere WOPI-clients:

```html
<form method="POST"
      action="{WOPI_CLIENT_BASE_URL}/browser/{hash}/cool.html?WOPISrc={WOPI_HOST_BASE_URL}/wopi/api/v1/files/{uuid}"
      target="collabora-iframe">
  <input type="hidden" name="access_token" value="{JWT_TOKEN}" />
  <input type="submit" value="Open document" />
</form>
```

Een werkend voorbeeldbestand met Collabora Online is beschikbaar in [`wopi_test.html`](../../docs/wopi/wopi_test.html) in de projectroot.

---
