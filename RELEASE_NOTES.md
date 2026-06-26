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
