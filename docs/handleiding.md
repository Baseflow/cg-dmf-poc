# Gebruikshandleiding — Integratie in een Common Ground landschap

Deze handleiding beschrijft hoe CG-DMF wordt geïntegreerd in een Common Ground landschap. Behandeld worden: OpenZaak, GZAC/Valtimo, IKO, Open Notificaties en WOPI.

## OpenZaak koppelen

### Vereisten

- Een werkende OpenZaak-installatie
- Een CG-DMF-instantie bereikbaar vanuit OpenZaak
- ZGW client credentials voor communicatie van Openzaak naar DMF-DRC (aangemaakt via de admin portal of `CLIENT_CREDENTIALS`)

### DMF registreren als DRC-service in OpenZaak

OpenZaak moet weten dat de CG-DMF de DRC is, zodat het objectreferenties kan valideren en aanmaken.

1. Ga in de OpenZaak-beheerinterface naar **API autorisaties → Services**
2. Klik op **Service toevoegen**
3. Vul in:
   - **Label**: `CG-DMF`
   - **Type**: `DRC (informatieobjecten)`
   - **API root URL**: `https://cg-dmf.example.com/documenten/api/v1/`
   - **Autorisatietype**: `ZGW client_id + secret`
   - **Client id**: het client-ID waarmee CG-DMF zichzelf identificeert bij OpenZaak (zie ook `CLIENT_CREDENTIALS`)
   - **Client secret**: het bijbehorende secret
   - **Gebruikers-id**: `openzaak`
   - **Gebruikersrepresentatie**: `Open Zaak`
4. Sla op

### CG-DMF toegang geven tot de OpenZaak-catalogus

De DMF valideert het `informatieobjecttype` van elk document tegen de catalogus in OpenZaak.

1. Ga in OpenZaak naar **API autorisaties → Applicaties**
2. Klik op **Applicatie toevoegen**
3. Vul in:
   - **Naam**: `DMF`
   - **Client id**: kies een client-ID voor de communicatie van DMF naar OpenZaak (bijv. `cg-dmf`)
   - **Client secret**: kies een secret
4. Sla op
5. Stel de waarden in als omgevingsvariabelen op de DMF:
   ```env
   OPENZAAK_ENDPOINT=https://openzaak.example.com
   OPENZAAK_CLIENT_ID=cg-dmf
   OPENZAAK_CLIENT_SECRET=<secret>
   OPENZAAK_VALIDATION_ENABLED=true
   ```

### Zaaktypen en informatieobjecttypen aanmaken

Zorg dat in OpenZaak de benodigde zaaktypen en informatieobjecttypen zijn geconfigureerd voordat u documenten aanmaakt via GZAC.

---

## GZAC / Valtimo koppelen

### Vereisten

- Een werkende GZAC/Valtimo-installatie
- Een CG-DMF-instantie bereikbaar vanuit GZAC
- ZGW client credentials voor communicatie van GZAC naar DMF (aangemaakt via de admin portal of `CLIENT_CREDENTIALS`)

### 1. Authenticatie-plugins instellen

Maak twee instanties van de **Open Zaak authenticatie plugin** aan:

- Eén voor communicatie van GZAC naar **OpenZaak** (zaakbeheer)
- Eén voor communicatie van GZAC naar **CG-DMF** (documentbeheer)

Elk met hun eigen client ID en secret.

### 2. OpenZaak-plugin instellen

Configureer de **OpenZaak-plugin** met:
- De basis-URL van uw OpenZaak-instantie
- De authenticatieplugin voor OpenZaak (stap 1)

### 3. Documenten-plugin instellen

Configureer de **Documenten-plugin** (of DMF-plugin) met:
- **Documenten API URL**: `https://cg-dmf.example.com/documenten/api/v1/`
- **Authenticatieplugin**: de authenticatieplugin voor CG-DMF (stap 1)
- **Versie**: `1.5.0` of hoger

Raadpleeg de [GZAC/Valtimo documentatie](https://docs.valtimo.nl/features/plugins/configure-documenten-api-plugin) voor aanvullende details over de plugin-configuratie.

### 4. Systeem upload-proces aanmaken

Het systeem-uploadproces koppelt een formuliercomponent voor bestandsuploads aan de DMF en OpenZaak.

1. Ga naar **Admin → System processes → Processen**
2. Maak een nieuw proces aan met de naam `DMF upload proces`
3. Voeg toe: start event → taak **Upload document** → taak **Link document aan zaak** → eind event
4. Maak van beide taken een **service task**
5. Configureer **Upload document**:
   - **Process link → Proceskoppeling aanmaken**
   - Kies de Documenten-plugin
   - Kies **Geüpload document opslaan**
6. Configureer **Link document aan zaak**:
   - **Process link → Proceskoppeling aanmaken**
   - Kies de OpenZaak-plugin
   - Kies **Koppel geüpload document aan zaak**
7. Sla het proces op

### 5. Dossier aanmaken

1. Ga naar **Admin → Configuratie → Dossiers** en maak een nieuw dossier aan
2. Ga naar **Formulieren** en maak drie formulieren aan:
   - **Start**: enkel een submit-knop
   - **Upload**: bevat het DMF-uploadcomponent (zie hieronder)
   - **Eind**: standaard submit-knop
3. Voeg in het Upload-formulier het **Documenten API upload**-component toe:
   - Stel onder **Advanced** de sectie `DMF upload proces` in
   - Stel de property in als `documentenApiUrl`
4. Ga naar **Processen** en maak een gebruikersproces aan:
   - Start event → user task **Upload document** → user task **Afronden** → end event
   - Koppel de formulieren aan de overeenkomstige taken via **Process link**
5. Ga naar **Algemeen** en stel in:
   - **Uploadproces**: `Documenten API upload document`
   - Vink aan: **Start een nieuw dossier** en **Door de gebruiker te starten**
   - Koppel het start-formulier aan het start event
6. Ga naar **ZGW** en stel in:
   - Controleer: **De Documenten API-plugin gebruikt versie 1.5.0**
   - Kies een **zaaktype** uit de catalogus
   - Stel de **OpenZaak-plugin** in
   - Kies **Automatisch aanmaken voor elk dossier**
7. Ga naar **Document** en zet `additionalProperties` op `true`
8. Activeer de dossierversie via **Meer → Actief maken**

---

## IKO — Integraal Klant Objectbeeld

IKO is een systeem dat naast GZAC kan worden ingezet om via diverse API's een geïntegreerd overzicht te tonen van informatie rondom een klant of zaak (het "beeld"). IKO kan de Documenten API van CG-DMF bevragen om documenten op te nemen in dit overzicht.

### Koppeling

IKO communiceert met de Documenten API via ZGW client credentials. Maak een credential aan via de admin portal (zie [installatie.md — Client credentials](installatie.md#client-credentials)) en configureer deze in IKO als authenticatiemethode voor de DMF.

Stel de **Documenten API URL** in IKO in op:
```
https://cg-dmf.example.com/documenten/api/v1/
```

Raadpleeg de IKO-documentatie voor de specifieke configuratiestappen in IKO zelf.

---

## Open Notificaties

CG-DMF kan events publiceren naar een Open Notificaties-instantie wanneer documenten worden aangemaakt,
bijgewerkt of verwijderd.

### Configuratie

Open Notificaties zijn standaard niet ingeschakeld. Stel hiervoor de volgende omgevingsvariabelen in:

```env
NOTIFICATION_API_URL=https://notificaties.example.com/api/v1
NOTIFICATION_API_TOKEN=<bearer-token-met-notificaties.publiceren-scope>
NOTIFICATION_KANAAL=documenten
NOTIFICATION_SOURCE=drc
```

Wanneer `NOTIFICATION_API_URL` leeg is, zijn notificaties uitgeschakeld.

### Kanaal registreren

Het kanaal `documenten` moet bestaan in Open Notificaties voordat de DMF er events op kan publiceren. Maak het kanaal aan via de Open Notificaties-beheerinterface als het nog niet bestaat.

### Autorisatie in Open Notificaties

De DMF heeft een Bearer-token nodig met de scope `notificaties.publiceren`. Maak dit token aan in de Open Notificaties-beheerinterface en stel het in als `NOTIFICATION_API_TOKEN`.

---

## WOPI — In-browser documentweergave

CG-DMF implementeert een WOPI-host waarmee een WOPI-client (bijv. Collabora Online) documenten rechtstreeks vanuit de DRC kan openen en tonen in de browser.

Zie [wopi/wopi.md](wopi/wopi.md) voor de volledige WOPI-integratiehandleiding.

### Snelconfiguratie

```env
WOPI_ENABLED=true
WOPI_HOST_BASE_URL=https://cg-dmf.example.com
WOPI_CLIENT_BASE_URL=https://collabora.example.com
```

Een frontend opent een document door een HTML-formulier te POSTen naar de Collabora-URL met het WOPI-source-pad en een JWT-token. Zie [wopi/wopi.md](wopi/wopi.md) voor een werkend voorbeeldbestand.

