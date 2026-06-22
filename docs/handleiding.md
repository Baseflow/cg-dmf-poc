# Gebruikshandleiding

Integratie in een Common Ground landschap. Deze handleiding beschrijft hoe CG-DMF wordt geïntegreerd in een Common Ground landschap. Behandeld worden: OpenZaak, GZAC/Valtimo, IKO, Open Notificaties en WOPI.

## DMF registreren als DRC-service in OpenZaak
OpenZaak moet weten dat de CG-DMF de DRC is, zodat het objectreferenties kan valideren en aanmaken.

### Maak applicatie aan in CG-DMF

1. Log in bij de admin-portal van CG-DMF
2. Ga naar **Applicaties**
3. Klik op **Toevoegen**
4. Kies een applicatie-naam (bijv. openzaak-dmf)
5. Creëer of genereer een client-secret
6. Sla op

![Applicatie toevoegen](images/applicatie-toevoegen.png)
![Applicaties lijst](images/applicaties-lijst.png)

Het is ook mogelijk om met behulp van de environmentvariabele `CLIENT_SECRETS` een client-secret aa te maken.

### Registreer service in OpenZaak

1. Ga in de OpenZaak-beheerinterface naar **API autorisaties → Services**
2. Klik op **Service toevoegen**
3. Vul in:
   - **Label**: `CG-DMF`
   - **Type**: `DRC (informatieobjecten)`
   - **API root URL**: `https://cg-dmf.example.com/documenten/api/v1/`
   - **Autorisatietype**: `ZGW client_id + secret`
   - **Client id**: het client-ID waarmee CG-DMF zichzelf identificeert bij OpenZaak.
   - **Client secret**: het bijbehorende secret
   - **Gebruikers-id**: `openzaak`
   - **Gebruikersrepresentatie**: `Open Zaak`
4. Sla op

![OpenZaak service toevoegen](images/openzaak-service-toevoegen.png)

--- 

## CG-DMF toegang geven tot de OpenZaak-catalogus

De DMF valideert het `informatieobjecttype` van elk document tegen de catalogus in OpenZaak.

### Applicatie aanmaken in OpenZaak

1. Ga in OpenZaak naar **API autorisaties → Applicaties**
2. Klik op **Applicatie toevoegen**
3. Vul in:
   - **Naam**: `DMF`
   - **Client id**: kies een client-ID voor de communicatie van DMF naar OpenZaak (bijv. `cg-dmf`)
   - **Client secret**: kies een secret
   - Selecteer **Heeft alle authorisaties**, of kies in Beheer authorisaties, iig. voor alle `Documenten API` autorisaties.
4. Sla op

![OpenZaak applicatie lijst](images/openzaak-applicatie-lijst.png)

### API-koppeling aanmaken in CG-DMF

1. Log in bij de admin-portal van CG-DMF
2. Ga naar **API koppelingen**
3. Klik op **Toevoegen**
4. Geef de API-koppeling een naam
5. Vul in:
   - **Type** selecteer: ZTC - Catalogi API
   - **Base URL**: https://openzaak.example.com/catalogi/api/v1/
   - **Authenticatie type**: ZGW authenticatie
   - **Client ID**: Naam als aangemaakt in de vorige stap
   - **Client secret**: Secret als aangemaakt in de vorige stap
   - **Validatie**: Ja om te valideren. Dit kan uitgeschakeld worden, maar dan worden `informatieobjecttype`-waarden niet gevalideerd tegen de OpenZaak-catalogus. Valideren heeft een aanzienlijke performance-impact, maar is wel belangrijk om consistentie te bewaken.
6. Sla op
7. Herhaal deze stappen om ook de `ZRC-Zaken API` te koppelen.

![API koppeling toevoegen](images/api-koppeling-toevoegen.png)
![API koppelingen lijst](images/api-koppelingen-lijst.png)

Het is ook mogelijk om de waarden als omgevingsvariabelen in de [configuratie](/docs/configuratie.md) van de DMF in te stellen.
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

### Credentials aanmaken
Maak credentials aan voor GZAC/Valtimo in zowel CG-DMF als in OpenZaak.

Zie ook:
- [Maak een applicatie aan in CG-DMF](#maak-een-applicatie-aan-in-gzac)
- [Maak een applicatie aan in OpenZaak](#applicatie-aanmaken-in-openzaak)

### 1. Authenticatie-plugins instellen
In GZAC/Valtimo is een authenticatie-plugin vereist voor communicatie met de Documenten API.

1. Navigeer in de sidebar link naar `Admin`
2. Kies hier `Plugins`
3. Kies `Plugin configureren` en voeg een nieuwe plugin toe van het type `Open Zaak`. Deze plugin implementeert de ZGW-authenticatie en kan door andere plugins gebruikt worden als authorisatie plugin.
4. Configureer de plugin met:
   - **Configuratie-naam**: bijv. `OpenZaak-auth`
   - **Client ID**: het client-ID dat u in de vorige stap heeft aangemaakt in OpenZaak
   - **Client secret**: het bijbehorende secret
5. Sla op
6. Voeg nu een tweede plugin toe van het type `Open Zaak`.
7. Configureer deze plugin met:
   - **Configuratie-naam**: bijv. `CG-DMF-auth`
   - **Client ID**: het client-ID dat u in de vorige stap heeft aangemaakt in CG-DMF
   - **Client secret**: het bijbehorende secret
8. Sla op

![GZAC plugins lijst](images/gzac-plugins-lijst.png)

### 2. Zaken API-plugin instellen

Voeg nog een plugin toe van het type `Zaken API`.

Configureer de plugin met:
- **Naam**: bv. `OpenZaak Zaken API`
- **Zaken API URL**: bv. `https://openzaak.example.com/zaken/api/v1/`
- **Configuratie authenticatie-plug-in (vereist)**: de OpenZaak-authenticatieplugin (stap 1)

### 3. Documenten-plugin instellen
Voeg nog een plugin toe van het type `Documenten API`.

Configureer de plugin met:
- **Naam**: bv. `CG-DMF Documenten API`
- **Documenten API URL**: bv. `https://cg-dmf.example.com/documenten/api/v1/`
- **Bronorganisatie RSIN**: Het RSIN van de bronorganisatie waarin de documenten worden opgeslagen.
- **Configuratie authenticatie-plug-in (vereist)**: de CG-DMF authenticatieplugin (stap 1)
- **Documenten API-versie**: Kies voor `1.5.0-baseflow`. Deze versie is vereist om extra object relatie acties te kunnen gebruiken vanuit GZAC.

![GZAC documenten plugin](images/gzac-documenten-plugin.png)

Raadpleeg de [GZAC/Valtimo documentatie](https://docs.valtimo.nl/features/plugins/configure-documenten-api-plugin) voor aanvullende details over de plugin-configuratie.

### 4. upload-proces aanmaken

Het systeem-uploadproces koppelt een formuliercomponent voor bestandsuploads aan de DMF en OpenZaak.

1. Ga naar **Admin → System processes → Processen**
2. Maak een nieuw proces aan met de naam `DMF upload proces`
3. Kies rechts bij _General_ eerst een naam: bv. `upload-dmf-drc`
4. Voeg toe: start event → taak **Upload document** → taak **Link document aan zaak** → eind event
5. Maak van beide taken een **service task**. Dit doe je door de Taak te selecteren en op het _wrench_ icoon te klikken en in de dropdown voor `Service task` te kiezen..
6. Configureer **Upload document** taak door deze te selecteren en in het menu rechts:
    - **Process link → Proceskoppeling aanmaken**
    - Kies de Documenten-plugin van de CG-DMF DRC zoals aangemaakt in stap 3 en dan Volgende.
    - Kies **Geüpload document opslaan**
7. Configureer **Link document aan zaak**:
    - **Process link → Proceskoppeling aanmaken**
    - Kies de Zaken API-plugin
    - Kies **Koppel geüpload document aan zaak**
8. Sla het proces op

<!-- screenshots: gzac-process-flow, gzac-process-plugin gzac-process-upload, gzac-process-linkzaak -->

### 5. Dossier aanmaken
We maken hier een voorbeeld dossier aan met een simpele upload flow naar de DRC. Hierbij:
- Identificeren we een 'fysiek object' genaamd _Werfkelder_ bij de start van het proces.
- Hebben we een taak waarbij iemand in het dossier een document moet uploaden.
- Dit document wordt geupload naar de CG-DMF DRC en aan de aangemaakte zaak gekoppeld. Het document is nu 'vindbaar' bij de zaak, maar nog niet bij het fysieke object _Werfkelder_. 
- De volgende stap in het proces is het uitvoeren van een nieuwe taak; Link document
- Na het uitvoeren van deze Taak, wordt er een nieuwe link gemaakt tussen het document en de _Werfkelder_. Het document is nu gekoppeld aan zowel de zaak als het fysieke object _Werfkelder_ en ook via beide te relaties vindbaar.
- De laatste taak in het process is het weer verwijderen van de net toegevoegde link. Na het verwijderen van de link is het document weer enkel gekoppeld via de zaak-relatie.

Ga naar **Admin → Configuratie → Dossiers** en maak een nieuw dossier aan.
Gebruik een naam en key die nog niet bestaan. Deze eigenschappen zijn later lastig te wijzigen.

1. Ga naar de **Algemeen**-tab en stel in:
   - **Uploadproces**: `Documenten API upload document` zoals aangemaakt in de vorige stap.
2. Ga naar **ZGW**-tab en stel in:
   - Controleer: De Documenten API-plugin gebruikt **versie** 1.5.0-baseflow
   - Kies een **zaaktype** uit de catalogus
   - Stel de **OpenZaak-plugin** in
   - Kies **Automatisch aanmaken voor elk dossier**
3. Ga naar **Document**-tab en zet `additionalProperties` op `true`

#### Formuleren
Ga naar **Formulieren**-tab van het dossier en maak enkele formulieren aan
- **Start**: naam `werfkelder.start`. Het formulier is enkel een tekstinvoer voor Werfkelder en een submit-knop. Dit is het beginpunt van het process. Het werfkelder ID in dit tekstveld is het ID waarmee de upload gaan koppelen.
- **Upload**: naam `werfkelder.upload`. Het formulier vraagt een gebruiker een bestand te uploaden, en de DRC metadata te verzamelen.
  - Kies vanuit de component builder links, voor **Advanced** en sleep een `Documenten API File Upload` in het formulier.
  - Stel de property in als `documentenApiUrl`. In deze property wordt de URL van het geregistreerde Documenten API-proces doorgegeven voor later gebruik.
- **Link**: naam `werfkelder.link`. Dit formulier is enkel een submit knop voor de gebruiker om te bevestigen dat ze de volgende stap (het linken) bevestigen.
- **Unlink**: naam `werfkelder.unlink`.Dit formulier is enkel een submit knop voor de gebruiker om te bevestigen dat ze de volgende stap (het unlinken) bevestigen.

#### Gebruikers process aanmaken
Ga naar **Processen** en maak een nieuw gebruikersproces aan met `Proces creëren`
- Vink aan: **Start een nieuw dossier** en **Door de gebruiker te starten**
- Voeg toe: een start, 5 taken, en een eind event en verbind ze
- Start:
  - Selecteer de start-taak, klik in het rechter menu op _Process link_ en dan _Proceskoppeling aanmaken_.
  - Kies in het dialoogvenster voor _Formulier_
  - Koppel het `werfkelder.start`-formulier aan de start van het proces
  - Sla op.
- Taak 1:
  - Selecteer de eerste taak. Klik op de _wrench_-icoon en maak dit een _User Task_.
  - Kies rechts voor _Process link_ en dan voor _Formulier_.
  - Configureer met formulier naam: `werfkelder.upload` en voor de grootte: Modaal en Medium
  - De waarde van 
  - Kies dan **Voltooien**
- Taak 2:
  - Maak dit een _User Task_.
  - Kies rechts voor _Process link_ en dan voor _Formulier_.
  - Configureer met formulier naam: `werfkelder.link` en voor de grootte: Modaal en Medium
  - Kies dan **Voltooien**
- Taak 3:
  - Maak dit een _Service Task_.
  - Kies rechts voor _Process link_.
  - Kies de plugin configuratie `CG-DMF Documenten API` zoals eerder toegevoegd in stap 3. 
  - Kies voor **Document koppelen aan object**
  - In het dialoogvenster dien je nu Object URL en Object type in te vullen.
    - ObjectUrl is bijv: `pv:werfkelderObjectUrl`
    - Objecttype is bijv: `wistor`
    - Deze waardes zijn de toekomstige waardes van het `objectinformatieobject` dat aangemaakt zal worden.
    - De URL van het aangemaakte `objectinformatieobject` zal later in het process beschikbaar zijn als: `pv:objectInformatieObjectUrl`
  - Kies dan **Voltooien**
- Taak 4:
  - Maak dit een _User Task_.
  - Kies rechts voor _Process link_ en dan voor _Formulier_.
  - Configureer met formulier naam: `werfkelder.unlink` en voor de grootte: Modaal en Medium
  - Kies dan **Voltooien**
- Taak 5:
  - Maak dit een _Service Task_.
  - Kies rechts voor _Process link_.
  - Kies de plugin configuratie `CG-DMF Documenten API` zoals eerder toegevoegd in stap 3.
  - Kies voor **Document koppeling verwijderen**
  - In het dialoogvenster dien je nu Object URL en Object type in te vullen.
    - Objectinformatieobject-URL is: `pv:objectInformatieObjectUrl`
    - Hiermee wordt de relatie met ID zoals vastgelegd in deze process variabele verwijderd.
  - Kies dan **Voltooien**

#### Afronden configuratie
Activeer de dossierversie via **Meer → Actief maken**

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

