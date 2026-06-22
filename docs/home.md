# Document Management Functionaliteit DRC

Dit project is een nieuw Document Registratie Component (DRC) in het Common Ground landschap.
Het implementeert een minimale, functionele versie van de [VNG Documenten API 1.5.0](https://vng-realisatie.github.io/gemma-zaken/standaard/documenten/),
met aanvullende functionaliteit voor relaties tussen niet-Zaken objecten.

## Doel
Deze DRC implementatie is initieel geschreven in opdracht van de Gemeente Utrecht door Baseflow BV.

Het doel van deze implementatie is het faciliteren van documenten opslag en registratie voor het Werven project en overige afdelingen waarbij documenten over objecten in de fysieke ruimte worden beheerd en bewaard.
Hierbij speelt het probleem dat de informatie vaak het resultaat is van een proces, maar dat de raadpleging en ontsluiting van de informatie en documenten vaak plaatsvind vanuit het oogpunt van het fysieke object. We noemen dit ook wel 'objectgericht werken' in plaats van zaakgericht werken. Het [IKO](https://github.com/Integraal-Klant-en-Objectbeeld/iko) of wel Integraal Klant en Objectbeeld is een initiatief van de Gemeente Utrecht om dit te faciliteren.

In het huidige ontwerp van de Common Ground APIs zijn documenten gekoppeld aan Zaken. Maar voor het raadplegen vanuit 'een niet zaak' is dit erg inefficient. Zeker als je nagaat dat bijv. 1 foto makkelijk over 8 fysieke objecten kan gaan, en dat 1 fysiek object soms 10 duizenden documenten kan hebben over duizenden zaken. Veel documenten zijn ook van VOOR een tijd waarbij zaakgericht werd gewerkt.

Deze implementatie wijzigt daardoor wat voor relaties een documentenregistratie kan hebben.
Naast ObjectInformatieObject relaties van het type zaak en/of besluit kan het nu ook relaties van andere types hebben.
Zowel IKO als GZAC kunnen hiervan gebruik maken door in de DRC configuratie te kiezen voor DRC API versie `1.5.0-baseflow`.
Voor de API wijzigingen die hiervoor zijn gemaakt zie [docs/implementatie.md](docs/implementatie.md).

Daarnaast experimenteert de implementatie met het gebruik van de [WOPI](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/concepts/wopi-overview) standaard voor in-browser documentweergave en bewerken. Hiermee kan een gebruiker een document direct openen in de browser zonder dat het eerst gedownload hoeft te worden.

## Licentie

[EUPL 1.2](https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12)

## Ontwikkelaar

Ontwikkeld door [Baseflow](https://www.baseflow.com) in opdracht van Gemeente Utrecht.
