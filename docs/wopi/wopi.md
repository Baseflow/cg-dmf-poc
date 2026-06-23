# WOPI integratie

## Overzicht

[WOPI (Web Application Open Platform Interface)](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/) is een protocol waarmee een webtoepassing documenten kan bekijken en bewerken via een externe opslaan-server, zonder de bestanden zelf te downloaden.

De DMF implementeert een **WOPI-host** zodat een WOPI-client zoals [Collabora Online](https://www.collaboraoffice.com/collabora-online/) documenten rechtstreeks uit de DRC kan openen en tonen.

---

## Geïmplementeerde WOPI-endpoints

De WOPI-routes zijn beschikbaar onder het basispad `/wopi/api/v1/files`.

| Endpoint                             | Method | WOPI-operatie | Beschrijving                                               |
| ------------------------------------ | ------ | ------------- | ---------------------------------------------------------- |
| `/wopi/api/v1/files/{file_id}`       | GET    | CheckFileInfo | Geeft metadata terug (bestandsnaam, grootte) als JSON      |
| `/wopi/api/v1/files/{file_id}/contents` | GET    | GetFile       | Streamt de binaire bestandsinhoud van het document         |

### CheckFileInfo response

```json
{
  "BaseFileName": "voorbeeld.docx",
  "Size": 102400
}
```

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
  <input type="hidden" name="access_token" value="{ACCESS_TOKEN}" />
  <input type="submit" value="Open document" />
</form>
```

Een werkend voorbeeldbestand met Collabora Online is beschikbaar in [`wopi_test.html`](../../docs/wopi/wopi_test.html) in de projectroot.

---
