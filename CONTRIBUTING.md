# Bijdragen aan CG-DMF

Bijdragen zijn welkom via pull requests op [github.com/Baseflow/cg-dmf](https://github.com/Baseflow/cg-dmf).

Zie [docs/ontwikkeling.md](docs/ontwikkeling.md) voor het opzetten van een lokale ontwikkelomgeving.

## Licentie

Door bij te dragen aan dit project stemt u ermee in dat uw bijdragen worden uitgebracht onder de **EUPL-1.2**-licentie (zie [LICENSE.md](LICENSE.md)).

Elk nieuw bronbestand bevat de volgende header:

```
// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
```

Pas het jaar aan als u een bestaand bestand wijzigt dat een ouder jaar bevat (bijv. `2024-2026`). Niet alle bestandstypen ondersteunen deze header — sla hem over waar dat niet van toepassing is.

## Werkwijze

1. **Fork** de repository en maak een branch aan vanuit `develop`.
2. Schrijf uw wijziging. Voeg tests toe voor nieuwe functionaliteit.
3. Zorg dat de checks slagen (zie hieronder per component).
4. Dien een **pull request** in op de `develop`-branch met een duidelijke beschrijving van wat er is gewijzigd en waarom.

---

## Backend

De backend is geschreven in Kotlin met het Ktor-framework.

### Codestijl

Opmaak wordt afgedwongen door [Spotless](https://github.com/diffplug/spotless) met [ktlint](https://pinterest.github.io/ktlint/). Voer altijd uit vóór een commit:

```bash
./gradlew spotlessApply
```

### Tests

```bash
# Unit tests (H2 in-memory, MockK)
./gradlew test

# Volledig build incl. stijlcheck
./gradlew build
```

Voeg voor nieuwe API-endpoints ook een voorbeeld toe aan de Bruno-collectie in `.bruno/CG-DMF/`. Zie [docs/ontwikkeling.md](docs/ontwikkeling.md) voor het uitvoeren van integratietests.

---

## Admin portal

De admin portal is een Next.js-applicatie en bevindt zich in `frontend/admin-portal/`. De toolchain is los van de backend.

### Vereisten

Node.js (LTS). Afhankelijkheden installeren:

```bash
cd frontend/admin-portal
npm install
```

### Ontwikkelserver

```bash
npm run dev
```

### Codestijl

Opmaak wordt afgedwongen door [Prettier](https://prettier.io/) en [ESLint](https://eslint.org/). Voer uit vóór een commit:

```bash
npm run format
npm run lint
```

### Tests en typecheck

```bash
# Unit tests (Vitest)
npm test

# TypeScript typecheck
npm run typecheck
```

Alle vier de commando's (`format`, `lint`, `test`, `typecheck`) moeten slagen voordat een PR wordt ingediend.

---

## Meldingen

- **Bugs en verbetervoorstellen**: open een [GitHub-issue](https://github.com/Baseflow/cg-dmf/issues).
- **Beveiligingsproblemen**: zie [SECURITY.md](SECURITY.md) — maak géén publiek issue aan.
