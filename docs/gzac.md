## GZAC configuratie

Primaire informatie:
https://docs.valtimo.nl/features/plugins/configure-documenten-api-plugin

### Plugin configuratie
Plugin configuratie is vrij eenvoudig en volgt de stappen in de primaire informatie.
Je hebt in iig nodig:
- Open Zaak authenticatie plugin
- Open Zaak plugin
- Documenten plugin (en/of DMF plugin)

### Systeem proces configuratie
Dit is het proces interne systeem proces dat de upload afhandelt van het formulier component dat we later gebruiken.
Het proces heeft kennis van de zaken en documenten API plugins en kan de ids etc van deze onderdelen zelf doorgeven van stap naar stap.
- Ga naar Admin > System processes -> Processen
- Hier zou je moeten kunnen vinden: Documenten API upload document
- Klik op het proces en bewerk het
- Selecteer 'Upload document'
  - Kies rechts voor 'Process link' en kies 'Proceskoppeling aanmaken'
  - Kies de Documenten API plugin die je wil gebruiken, Volgende
  - Kies 'Geupload document opslaan'
  - Sla op
- Selecteer 'Link document to zaak'
  - Kies rechts voor 'Process link' en kies 'Proceskoppeling aanmaken'
  - Kies de Zaken API OpenZaak plugin die je wil gebruiken, Volgende
  - Kies 'Zaak aanmaken', Volgende
  - Configureer een zaaktype en een RSIN van de aan te maken zaak
  - Sla op
- Sla het proces op


### Dossier configuratie
Om een dossier aan te maken, let op de volgende stappen:
- Ga naar Admin > Configuratie > Dossiers en maak een nieuw dossier aan.
- Bewerk het dossier en ga naar Formulieren
- Creëer een nieuw formulier. Dit formulier zal bij het creëren van een nieuw dossier gepresenteerd worden aan de gebruiker.
  - In de sectie 'Advanced', kies 'Documenten API file upload' 
  - Drag en drop dit component in het formulier, boven de 'Submit'-knop
  - Configureer nu iig het 'informatie object type' dat je wilt gebruiken voor uploads
    - TODO, je wil misschien eigenlijk dat dit ook automatisch on the fly kan worden bepaald....
  - De property houden we documentenApiUrl
  - De url (van het enkelvoudig informatie object) kan ook met Process variables in een variabele van het proces worden gezet
  - Sla het formulier op
- Ga naar 'Algemeen' sectie van je dossier en zet 'Uploadproces koppelen aan dossierdefinitie' op de waarde 'Documenten API upload document'.
- Creëer een nieuw proces
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