## 1.1.1

### Backend

- Fixes null reference exception thrown by FlywayDB.

## 1.1.0

### Backend

- Adds support to unlock and relock a file.
- Adds support to host a WOPI Host page, making integration through WOPI easier.

## 1.0.1

### Backend

- Fix downloading and fetching information of old versions of an EIO

## 1.0.0

### Backend

- Downloaden van een document retourneert nu een correcte foutmelding wanneer de opslag tijdelijk niet
  beschikbaar is (503) of wanneer het bestand ontbreekt in de opslag (500), in plaats van een lege 200-response.
- De applicatiepoort is nu correct in te stellen via de `PORT` omgevingsvariabele.
- Maximale grootte van HTTP-headerwaarden verhoogd, voor omgevingen met grote cookie-waarden of veel services.

### Admin Portal

- Omgevingsvariable badge toegevoegd aan readonly entries van storage repositories.
- ZGW-token generator toegevoegd, beschikbaar via de Documenten API sectie in de navigatie.
- Bestandsdelen groottelimieten worden nu weergegeven in MB voor betere leesbaarheid.

## 0.9.8

### Backend
- When disableChunkedEncoding is true, do not use data streams. This is for
  the S3-proxy, which doesn't handle signing and streaming.

## 0.9.7

### Backend
- Additional logging for S3 blobstorage

### Admin portal
- Make it possible to configure disableChecksums and disableChunkedEncoding

### Helm charts
- Set ingress settings to allow for larger cookie values
- Allow longer and bigger requests to the backend API

## 0.9.6

### Backend

- Datum- en tijdvelden worden nu consistent als UTC opgeslagen via `Instant`, zodat tijdzonefouten bij PostgreSQL worden voorkomen
- Correcte standaardwaarden voor chunk size en trigger size voor bestandsdelen (CGDMF-179)
- Instellingen (DMF-settings) zijn type-safe met server-side validatie op type en minimumwaarden (CGDMF-166)
- JWT-authenticatie verwijderd uit de Documenten API en WOPI-endpoints; ZGW-auth is nu de enige ondersteunde authenticatiemethode (CGDMF-177)
- OpenAPI-specificatie beschikbaar op de standaard VNG-Realisatie paden (`/documenten/api/v1/openapi.json` en `.yaml`); het bestaande pad blijft beschikbaar (CGDMF-282)
- Autorisatiefouten door ontbrekende rollen worden nu gelogd, wat het oplossen van toegangsproblemen vereenvoudigt

### Admin Portal

- Ingebouwde documentatieviewer (Docsify) voor de handleiding, rechtstreeks beschikbaar vanuit het portaal
- Handleiding toegankelijk via de nieuwe "Handleiding" sectie in de navigatie
- Nieuwe "API Verkenner" sectie in de navigatie met toegang tot de OpenAPI-interface
- "Maak standaard" optie toegevoegd in het dropdown-menu van repositories
- Admin portal is toegevoegd aan de Docker Compose configuratie voor lokale ontwikkeling

## 0.9.0

### Backend

- Initiële release van de DMF-DRC, een implementatie van de Documenten API v1.5.0
- Ondersteuning voor relaties met objecten anders dan zaak/besluit
- Implementatie van het grootste deel van de DRC API (verzendingen en gebruiksrechten zijn niet geïmplementeerd)
- Ondersteuning voor S3 en Azure als opslagengines
- Ondersteuning voor WOPI protocol om bestanden te openen en bewerken in externe editors zoals Collabora

### Admin Portal

- Initiële release van de beheersinterface

### Helm Chart

- Initiële release van het cg-dmf Helm chart
