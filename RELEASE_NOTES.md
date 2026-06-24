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
