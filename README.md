# CG-DMF — Document Management Functie

**CG-DMF** is een open source [Document Registratie Component (DRC)](https://vng-realisatie.github.io/gemma-zaken/standaard/documenten/) voor het [Common Ground](https://commonground.nl/) landschap.
Het implementeert de VNG **Documenten API 1.5.0** en enkele experimentele functionaliteiten.

De applicatie bestaat uit een **backend** (Kotlin/Ktor) en een **admin portal** (Next.js). Documenten worden opgeslagen in S3-compatibele opslag (MinIO, AWS S3) of Azure Blob Storage.

## Functionaliteiten

- Volledige implementatie van de [VNG Documenten API 1.5.0](https://vng-realisatie.github.io/gemma-zaken/)
- Koppeling met documenten aan willekeurige objecten (niet alleen Zaken)
- S3-compatibele opslag (MinIO, AWS S3) en Azure Blob Storage
- Optionele integratie met [Open Notificaties](https://github.com/open-zaak/open-notificaties)
- Optionele WOPI-host voor in-browser documentweergave en bewerken (bijv. Collabora Online)
- Beheerinterface voor opslag, authenticatie en clientbeheer

## Doel
Deze DRC implementatie is initieel geschreven in opdracht van de Gemeente Utrecht door Baseflow BV.

Het doel van deze implementatie is het faciliteren van documenten opslag en registratie voor het Werven project en overige afdelingen waarbij documenten over objecten in de fysieke ruimte worden beheerd en bewaard.
Hierbij speelt dat het probleem dat de informatie vaak het resultaat is van een proces, maar dat de raadpleging en ontsluiting van de informatie en document meer object georiënteerd is. We noemen dit ook wel 'objectgericht werken' in plaats van zaakgericht werken. Het [IKO](https://github.com/Integraal-Klant-en-Objectbeeld/iko) of wel Integraal Klant en Objectbeeld is een initiatief van de Gemeente Utrecht om dit te faciliteren.

In het huidige ontwerp van de Common Ground APIs zijn documenten gekoppeld aan Zaken. Maar voor het raadplegen vanuit 'een niet zaak' is dit erg inefficient. Zeker als je nagaat dat bijv. 1 foto makkelijk over 8 fysieke objecten kan gaan, en dat 1 fysiek object soms 10 duizenden documenten kan hebben over duizenden zaken. Veel documenten zijn ook van VOOR een tijd waarbij zaakgericht werd gewerkt.

Deze implementatie wijzigt daardoor wat voor relaties een documentenregistratie kan hebben.
Naast ObjectInformatieObject relaties van het type zaak en/of besluit kan het nu ook relaties van andere types hebben.
Zowel IKO als GZAC kunnen hiervan gebruik maken door in de DRC configuratie te kiezen voor DRC api versie `1.5.0-baseflow`.
Voor de API wijzigingen die hiervoor zijn gemaakt zie [docs/implementatie.md](docs/implementatie.md).

Daarnaast experimenteert de implementatie met het gebruik van de [WOPI](https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/concepts/wopi-overview) standaard voor in-browser documentweergave en bewerken. Hiermee kan een gebruiker een document direct openen in de browser zonder dat het eerst gedownload hoeft te worden.

## Documentatie

| Onderwerp                               | Beschrijving                                             |
|-----------------------------------------|----------------------------------------------------------|
| [Installatie](docs/installatie.md)      | Productie-installatie: Hel, Docker Compose, Helm         |
| [Gebruik](docs/handleiding.md)          | Integratie met OpenZaak, GZAC, IKO, notificaties en WOPI |
| [Ontwikkeling](docs/ontwikkeling.md)    | Lokale ontwikkelomgeving, testen, releases               |
| [Configuratie](docs/configuratie.md)    | Alle omgevingsvariabelen en configuratieopties           |
| [Documenten API](docs/implementatie.md) | Implementatiestatus van de API-endpoints                 |
| [Databasemigraties](docs/DATABASE.md)   | Migratieworkflow (voor ontwikkelaars)                    |
| [WOPI](docs/wopi/wopi.md)               | In-browser documentweergave en -bewerking via WOPI       |

## Snel aan de slag

Voor een productie-installatie: zie [docs/installatie.md](docs/installatie.md).  
Voor lokale ontwikkeling: zie [docs/ontwikkeling.md](docs/ontwikkeling.md).

## Bijdragen

Bijdragen zijn welkom via pull requests op [github.com/Baseflow/cg-dmf](https://github.com/Baseflow/cg-dmf).  
Zie [CONTRIBUTING.md](CONTRIBUTING.md) voor richtlijnen en [SECURITY.md](SECURITY.md) voor het melden van beveiligingsproblemen.

## Licentie

Copyright © 2025–2026 Gemeente Utrecht

EUPL-1.2 — zie [LICENSE.md](LICENSE.md)
