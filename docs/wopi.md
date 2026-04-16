# WOPI & Collabora Online integratie

## Overzicht

[WOPI (Web Application Open Platform Interface)](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/) is een protocol waarmee een webtoepassing documenten kan bekijken en bewerken via een externe opslaan-server, zonder de bestanden zelf te downloaden.

De DMF implementeert een **WOPI-host** zodat een WOPI-client zoals [Collabora Online](https://www.collaboraoffice.com/collabora-online/) documenten rechtstreeks uit de DRC kan openen en tonen.

---

## Geïmplementeerde WOPI-endpoints

De WOPI-routes zijn beschikbaar onder het basispad `/wopi/files`.

| Endpoint                      | Method | WOPI-operatie   | Beschrijving                                                  |
|-------------------------------| ------ | --------------- | ------------------------------------------------------------- |
| `/wopi/files/{file_id}`       | GET    | CheckFileInfo   | Geeft metadata terug (bestandsnaam, grootte) als JSON         |
| `/wopi/files/{file_id}/contents` | GET    | GetFile         | Streamt de binaire bestandsinhoud van het document            |

### CheckFileInfo response

```json
{
  "BaseFileName": "voorbeeld.docx",
  "Size": 102400
}
```

---

## Collabora Online instellen

### Vereisten

- Een draaiende Collabora Online instantie (bijv. via Docker).
- De DMF moet bereikbaar zijn vanuit de Collabora Online instantie via HTTP(S).

---

## Configuratie

Stel de volgende omgevingsvariabelen in om WOPI en Collabora te configureren:

| Variabele                  | Standaardwaarde | Beschrijving                                                                                      |
| -------------------------- | --------------- | ------------------------------------------------------------------------------------------------- |
| `WOPI_ENABLED`             | `true`          | Zet op `false` om de WOPI-routes volledig uit te schakelen                                        |
| `COLLABORA_URL`            | _(leeg)_        | Publieke URL van de Collabora Online instantie, bijv. `https://collabora.example.com`             |
| `WOPI_BASE_URL`            | _(leeg)_        | Publieke basis-URL van de DMF die door Collabora bereikbaar is, bijv. `https://dmf.example.com`  |

> **Let op:** `WOPI_BASE_URL` moet bereikbaar zijn vanuit de Collabora-container. In lokale ontwikkeling is dit `http://app:8080` (de Docker Compose service naam).

---

## Document openen in Collabora

Een frontend opent een document in Collabora door een HTML-formulier te `POST`en naar de Collabora WOPI-client-URL:

```html
<form method="POST"
      action="{COLLABORA_URL}/browser/{hash}/cool.html?WOPISrc={WOPI_BASE_URL}/wopi/api/v1/files/{uuid}"
      target="collabora-iframe">
  <input type="hidden" name="access_token" value="{JWT_TOKEN}" />
  <input type="submit" value="Open document" />
</form>
```

Een werkend voorbeeldbestand is beschikbaar in [`wopi_test.html`](../wopi_test.html) in de projectroot.

---
