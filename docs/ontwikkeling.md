# Ontwikkelhandleiding

Deze handleiding beschrijft hoe u CG-DMF lokaal opzet voor ontwikkeling. Voor een productie-installatie, zie [installatie.md](installatie.md).

## Inhoudsopgave

- [Vereisten](#vereisten)
- [Repository klonen](#repository-klonen)
- [Backend](#backend)
  - [Stack starten](#stack-starten)
  - [Configuratie](#configuratie)
  - [Bouwen en starten](#bouwen-en-starten)
  - [Tests uitvoeren](#tests-uitvoeren)
  - [Codestijl](#codestijl)
  - [Databasemigraties aanmaken](#databasemigraties-aanmaken)
- [Admin portal](#admin-portal)
  - [Starten](#starten)
  - [Tests en typecheck](#tests-en-typecheck)
  - [Codestijl](#codestijl-1)
- [Helm chart](#helm-chart)
- [Releaseproces](#releaseproces)
  - [Een release aanmaken](#een-release-aanmaken)
  - [Branch- en imagetag-schema](#branch--en-imagetag-schema)
  - [Hotfixes](#hotfixes)

## Vereisten

| Tool                    | Versie                  | Installatie (macOS)                      |
|-------------------------|-------------------------|------------------------------------------|
| Java (JDK)              | 21                      | `brew install openjdk@21`                |
| Gradle                  | meegeleverd via wrapper | —                                        |
| Node.js                 | LTS                     | `brew install node` of via nvm           |
| Docker + Docker Compose | recent                  | `brew install docker docker-compose`     |
| Bruno (optioneel)       | recent                  | [usebruno.com](https://www.usebruno.com) |
| Helm (optioneel)        | recent                  | `brew install helm`                      |

## Repository klonen

```bash
git clone https://github.com/Baseflow/cg-dmf.git
cd cg-dmf
```

---

## Backend

De backend is geschreven in Kotlin met het Ktor-framework en bevindt zich in `src/`.

### Stack starten

De applicatie heeft minimaal PostgreSQL, Keycloak en MinIO nodig:

```bash
docker compose up -d postgres keycloak minio
```
Voor de volledige stack inclusief Open Notificaties, Azure Blob-emulator (Azurite), RabbitMQ en Redis:

```bash
docker compose up -d
```

De `docker-compose.override.yml` wordt automatisch meegeladen en publiceert alle poorten naar de host.

### Configuratie

Kopieer het voorbeeldbestand en pas aan:

```bash
cp .env.example .env
```

De meeste standaardwaarden in `.env.example` werken direct voor lokale ontwikkeling. De volgende waarden vereisen handmatige actie:

**Encryptiesleutels** — verplicht zodra u blob storage-credentials opslaat via de admin portal. Genereer willekeurige waarden:

```bash
openssl rand -base64 32   # → ENCRYPTION_SECRET_KEY
openssl rand -hex 16      # → ENCRYPTION_SALT
```

**ZGW-clientcredentials** — verplicht voor ZGW HS256-authenticatie (GZAC, OpenZaak). Vul `CLIENT_CREDENTIALS` in met één of meer `clientId:secret`-paren:

```env
CLIENT_CREDENTIALS=gzac:yoursecret,valtimo:anothersecret
```

Het secret hier moet overeenkomen met wat u als `--env-var jwt.clientSecret` meegeeft aan Bruno-tests.

**Keycloak OIDC-secret** — de realm-export in `data/realm-export.json` bevat het vaste client secret `cg-dmf-client-secret` voor de admin portal. Dit werkt out-of-the-box met de lokale Keycloak-container; voor productie dient u dit te vervangen (zie [installatie.md](installatie.md#keycloak-configuratie)).

Zie [configuratie.md](configuratie.md) voor een beschrijving van alle overige variabelen.


**OpenZaak** is geen vereiste voor lokale ontwikkeling. Validatie van `informatieobjecttype`-URLs wordt stil overgeslagen als er geen ZTC-connectie geconfigureerd is. Om validatie expliciet uit te schakelen, zet in `.env`:

```env
OPENZAAK_VALIDATION_ENABLED=false
```

### Bouwen en starten

```bash
./gradlew build
./gradlew run
```

De backend draait daarna op `http://localhost:8080`. De API-documentatie is beschikbaar op `http://localhost:8080/docs`.

### Tests uitvoeren

#### Unit tests

```bash
./gradlew test
```

Unit tests gebruiken H2 in-memory database en MockK voor mocks.

#### Integratietests (Bruno)

De Bruno-collectie in `.bruno/CG-DMF/` bevat integratietests voor alle API-endpoints.

**Via Bruno-applicatie:**

1. Open de collectie `.bruno/CG-DMF/` in de Bruno-applicatie.
2. Klik op het schildpictogram naast de omgevingsselectie en schakel van Sandbox naar Developer-modus.
3. Selecteer de omgeving **Localhost** rechtsboven.

**Via CLI:**

```bash
npm install -g @usebruno/cli
bru run \
  --sandbox=developer \
  --env Localhost \
  --env-var jwt.clientSecret=yoursecret \
  --env-var jwt.clientId=gzac \
  --reporter-junit results.xml
```

Zorg dat de volgende variabelen zijn ingesteld als u wilt dat alle bestandsdelen-tests slagen:

```env
BESTANDSDELEN_TRIGGER_SIZE=100000
BESTANDSDELEN_CHUNK_SIZE=100000
```

**Via Docker Compose (zoals in CI):**

```bash
JWT_CLIENT_SECRET=your-secret docker compose \
  -f docker-compose.integration-test.yml \
  up bruno --build
```

#### Prestatietests (k6)

K6-scripts staan in `k6/`. Ze bestaan uit een seeding-stap (`seed.js`) en een loadtest-stap (`perf.js`).

```bash
JWT_CLIENT_SECRET=<secret> docker compose \
  -f docker-compose.yml \
  -f docker-compose.k6.yml \
  up --exit-code-from k6
```

Resultaten worden geschreven naar `./build/k6/`.

Configureerbare variabelen: `EIO_TARGET`, `OIO_TARGET`, `MAX_VERSIONS`, `RAMP_DURATION`, `STEADY_DURATION`, `MAX_VUS`.

### Codestijl

Opmaak wordt afgedwongen door Spotless + ktlint. Voer dit altijd uit vóór een commit:

```bash
./gradlew spotlessApply
```

Controleren zonder automatisch corrigeren:

```bash
./gradlew spotlessCheck
```

### Databasemigraties aanmaken

Bij wijziging van een Exposed-tabeldefinitie:

1. Maak een nieuwe tabel of pas deze aan via de Exposed entity definitie in `src/main/kotlin/entities/`
2. Registreer nieuwe tabellen in `src/main/kotlin/tooling/AllTables.kt` (als het een nieuwe tabel is)
3. Genereer de migratie-SQL:
   ```bash
   ./gradlew generateMigration -Pargs="V<n>__Beschrijving"
   ```
4. Controleer en verfijn de gegenereerde SQL
5. Maak handmatig een undo-script aan: `U<n>__Beschrijving.sql`
6. Pas de migratie toe: `./gradlew flywayMigrate`

Zie [DATABASE.md](DATABASE.md) voor de uitgebreide migratieworkflow.

---

## Admin portal

De admin portal is een Next.js-applicatie en bevindt zich in `frontend/admin-portal/`.

### Starten

```bash
cd frontend/admin-portal
npm install
npm run dev
```

De admin portal draait daarna op `http://localhost:3000`.

### Tests en typecheck

```bash
# Unit tests (Vitest)
npm test

# TypeScript typecheck
npm run typecheck
```

### Codestijl

Opmaak wordt afgedwongen door Prettier en ESLint:

```bash
npm run format
npm run lint
```

---

## Helm chart

De Helm chart bevindt zich in `helm/cg-dmf/`. Zie [helm/CLAUDE.md](../helm/CLAUDE.md) voor chart-specifieke conventies en `helm/cg-dmf/README.md` voor de configuratiereferentie.

### Linting

```bash
helm lint helm/cg-dmf
helm lint helm/cg-dmf -f helm/cg-dmf/values.local.yaml
```

---

## Releaseproces

Releases worden gestuurd door de branchnaam — er hoeven geen versiebestanden bewerkt te worden.

### Een release aanmaken

1. Zorg dat `develop` de staat heeft die u wilt releasen.
2. Maak een `RELEASE_NOTES.md` aan in de root met een beschrijving van de wijzigingen. Voeg secties toe per gewijzigd onderdeel: backend, admin portal en/of Helm chart.
3. Maak en push een `release/X.Y.Z`-branch van `develop`:
   ```bash
   git checkout develop && git pull
   git checkout -b release/1.0.0
   git push origin release/1.0.0
   ```
4. CI bouwt automatisch Docker-images, publiceert ze naar Docker Hub en Azure Container Registry, en maakt een GitHub Release aan.
5. Verwijder `RELEASE_NOTES.md` na de release en commit dat naar `develop`.
6. Open een PR van `release/1.0.0` naar `main` om `main` up-to-date te houden.

### Branch- en imagetag-schema

| Branch           | Docker-imagetag             |
|------------------|-----------------------------|
| `develop`        | `develop-YYYYMMDDHHMM`      |
| `release/X.Y.Z`  | `X.Y.Z` en `latest`         |
| `hotfix/X.Y.Z`   | `hotfix-X.Y.Z-YYYYMMDDHHMM` |
| overige branches | branchnaam (geen push)      |

### Hotfixes

Voor een fix die direct naar productie moet:

1. Branch van `main`: `git checkout -b hotfix/1.0.1 main`
2. Pas de fix toe en push. CI bouwt een timestamped image voor testen.
3. Maak een `release/1.0.1`-branch van de hotfix-branch en push die om de volledige release te triggeren.
4. Merge `release/1.0.1` in zowel `main` als `develop`.
