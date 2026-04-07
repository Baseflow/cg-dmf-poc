## GZAC configuratie

Primaire informatie:
https://docs.valtimo.nl/features/plugins/configure-documenten-api-plugin

### Plugin configuratie
Plugin configuratie is vrij eenvoudig en volgt de stappen in de primaire informatie.
Je hebt in iig nodig:
- 2 instances van Open Zaak authenticatie plugin
  - 1 met het ZGW client id en secret om te kunnen authenticeren met openzaak
  - 1 met het ZGW client id en secret om te kunnen autheticaeren bij DMF/DRC
- Open Zaak plugin, om zaken aan te kunnen maken, welk gebruik maat van de Openzaak authenticatie plugin met het openzaak id+secret
- Documenten plugin (en/of DMF plugin)
  - Documenten api url is: `https://example.com/documenten/api/v1/`
  - Authenticatie plugin is: De naam van de auth plugin voor DMF/DRC.
  - Versie: minimaal 1.5.0

### Systeem proces configuratie
Dit is het systeem proces dat de upload afhandelt van het formulier component dat we later gebruiken.
Het proces heeft kennis van de zaken en documenten API plugins en kan de ids etc van deze onderdelen zelf doorgeven van stap naar stap.
- Ga naar Admin > System processes > Processen
- Hier zou je moeten kunnen vinden: Documenten API upload document
- Klik op het proces en bekijk het.

We geen nu een kopie van dit proces maken
- Creëer een nieuw proces met de naam 'DMF upload proces'
- Voeg een start event (dunne cirkel) toe
- Voeg twee nieuwe Taken toe (rechthoek), genaamd "Upload document" en "link document aan zaak"
- Voeg een eind event toe (dikke cirkel)
- Voor de taken, selecteer ze en kies het 'sleutel' icoon
- Maak van beide taken een 'service task'
- Selecteer nu elk element van links naar rechts, en gebruik de pijl om start te verbinden aan taak, taak aan taak en de laatste taak weer aan het end event.
- Selecteer 'Upload document'
  - Kies rechts voor 'Process link' en kies 'Proceskoppeling aanmaken'
  - Kies de Documenten API plugin die je wil gebruiken, Volgende
  - Kies 'Geupload document opslaan'
  - Sla op
- Selecteer 'Link document to zaak'
  - Kies rechts voor 'Process link' en kies 'Proceskoppeling aanmaken'
  - Kies de Zaken API OpenZaak plugin die je wil gebruiken, Volgende
  - Kies 'Koppel geüpload document aan zaak', Volgende
  - Sla op
- Sla het proces op


### Dossier configuratie
Om een dossier aan te maken, let op de volgende stappen:
- Ga naar Admin > Configuratie > Dossiers en maak een nieuw dossier aan.
- Bewerk het dossier en ga naar Formulieren
- Creëer drie nieuwe formulieren: Start, Upload en Eind
  - Start formulier: heeft enkel een submit knop.
  - Eind formulier: heeft enkel de default submit knop.
  - Upload formulier zal bij het creëren van een nieuw dossier als stap gepresenteerd worden aan de gebruiker.
    - In de sectie 'Advanced', kies 'DMF upload process' dat je in de vorige stap hebt aangemaakt.
    - Drag en drop dit component in het formulier, boven de 'Submit'-knop
    - De property houden we documentenApiUrl
    - De url (van het enkelvoudig informatie object) kan ook met Process variables in een variabele van het proces worden gezet
- Ga naar 'Processen' en creëer een nieuw proces
  - Voeg een start event, twee user tasks toe en een end event.
  - Verbind ze met pijlen
  - Noem de user tasks 'Upload document' en 'Afronden'
  - Selecteer het start event en kies 'Process link' in het menu aan de rechterkant
  - Kies 'Koppelen' en selecteer 'formulier'
  - Kies nu het 'start formulier' dat je eerder hebt aangemaakt
  - Sla dit op
  - Selecteer de user task en kies 'Process link' in het menu aan de rechterkant
  - Koppel nu het 'upload formulier'
  - Selecteer de user task "Afronden" en kies 'Process link' in het menu aan de rechterkant
  - Koppel nu het 'eind' formulier
  - Sla het proces op
- Ga naar 'Algemeen' sectie van je dossier en zet 'Uploadproces koppelen aan dossierdefinitie' op de waarde 'Documenten API upload document'.
  - Vink 'Start een nieuw dossier' en 'Door de gebruiker te starten' aan
  - Er zou een 'Start event' state moeten zijn, selecteer deze
  - Onder de opties, ga naar 'Process link'
  - Kies 'Formulier'
  - Kies je gedefinieerde formulier
  - Sla je proces op
- Ga naar de 'ZGW' sectie van je dossier. We hebben een Zaak koppeling nodig, omdat het huidige interne proces voor DRC uploads er vanuit gaat dat er altijd aan een ZGW zaak gekoppeld wordt.
  - Je moet zien: De Documenten API-plugin gebruikt versie 1.5.0
  - Koppel een zaak type.
  - Kies een zaaktype van je catalogus uit de dropdown
  - Selecteer de Open Zaak plugin van je Open Zaak instance.
  - Kies 'automatisch aanmaken voor elke dossier'
- Ga naar de 'Document' sectie van je dossier en zet `additionalProperties` op `true` en sla dat op
- Maak je Dossier versie de actieve versie (rechts boven onder 'Meer')

Je zou nu een werkend upload formulier voor de Zaken API moeten hebben, dat je vanuit de 'Dossiers' sectie links zou moeten kunnen terugvinden een aanmaken. 
