# DMF-PoC

This repository is a Proof of Concept (PoC) for a new Document Registration Component (DRC) in the Common Ground landscape. It implements a minimal, functional version of the Documenten API, with additional filtering for new relations between non-Zaken objects.

## Document API implementation progress.

### enkelvoudiginformatieobjecten
| Endpoint | Method | Description | Status | Remarks|
| -------- | -------- | -------- | ---------- |  --------------- |
| / | GET  | Get a list of enkelvoudiginformatieobjecten based on the given query parameters | $${\color{green}Done}$$ | | 
| / | POST | Create a enkelvoudiginformatieobject | $${\color{green}Done}$$ | | 
| /{UUID}/audittrail | GET | Get all audittrail records for a enkelvoudiginformatieobject | $${\color{orange}In \space review}$$ | |
| /{UUID}/audittrail/{at_UUID} | GET | Get a single audittrail record based on enkelvoudiginformatieobject-id and audittrail-id | $${\color{orange}In \space review}$$ | | 
| /{UUID} | GET | Get a single enkelvoudigInformatieObject | $${\color{green}Done}$$ | | 
| /{UUID} | PUT | Fully updates a enkelvoudiginformatieobject | $${\color{green}Done}$$ | | 
| /{UUID} | PATCH | Partially updates a enkelvoudiginformatieobject | $${\color{green}Done}$$ | | 
| /{UUID} | DELETE | Deletes a enkelvoudiginformatieobject | $${\color{green}Done}$$ | | 
| /{UUID} | HEAD | Retrieves headers for a specific enkelvoudiginformatieobject | $${\color{green}Done}$$ | | 
| /{UUID}/download | GET | Download the binary data from the enkelvoudiginformatieobject | $${\color{green}Done}$$ | | 
| /{UUID}/lock | POST | Locks a enkelvoudiginformatieobject | $${\color{green}Done}$$ | | 
| /{UUID}/unlock | POST | Unlocks enkelvoudiginformatieobject | $${\color{green}Done}$$ | | 
| /{UUID}/_zoek| POST | Searches for enkelvoudiginformatieobject records, based on the search body of the request| $${\color{green}Done}$$ | | 

### gebruiksrechten 
| Endpoint | Method | Description | Status | Remarks|
| -------- | -------- | -------- | ---------- |  --------------- |
| / | GET  | Get a list of gebruiksrechten based on the given query parameters | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| / | POST  | Create gebruiksrechten | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID} | GET | Get a single gebruiksrechten instance based on Id| $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID} | PUT | Completely updates a a single gebruiksrechten instance based on Id| $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID} | PATCH | Partually updates a a single gebruiksrechten instance based on Id| $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID} | DELETE | Deletes a single gebruiksrechten instance based on Id| $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID} | HEAD | Gets the headers for a single gebruiksrechten instance based on Id| $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 

### objectinformatieobject 
| Endpoint | Method | Description | Status | Remarks|
| -------- | -------- | -------- | ---------- |  --------------- |
| / | GET  | Get a list of objectinformatieobject records based on the given query parameters | $${\color{green}Done}$$ |  | 
| / | POST  | Create objectinformatieobject relation | $${\color{green}Done}$$ | | 
| /{UUID} | GET | Get a single objectinformatieobject relation based on Id | $${\color{green}Done}$$ | | 
| /{UUID} | DELETE | Deletes a single objectinformatieobject relation based on Id | $${\color{green}Done}$$ | | 
| /{UUID} | HEAD | Gets the headers for a single objectinformatieobject relation based on Id | $${\color{green}Done}$$ | | 

### verzendingen 
| Endpoint | Method | Description | Status | Remarks|
| -------- | -------- | -------- | ---------- |  --------------- |
| / | GET  | Get a list of verzendingen based on the given query parameters | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| / | POST  | Create verzending | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID} | GET | Get a single verzending instance based on Id| $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID} | PUT | Completely updates a a single verzending instance based on Id| $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID} | PATCH | Partually updates a a single verzending instance based on Id| $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID} | DELETE | Deletes a single verzending instance based on Id| $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID} | HEAD | Gets the headers for a single verzending instance based on Id| $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 

### bestandsdelen 
| Endpoint | Method | Description | Status | Remarks|
| -------- | -------- | -------- | ---------- |  --------------- |
| /{UUID} | PUT | Upload a bestandsdeel | $${\color{red}Not \space implemented}$$ | | 

## Prerequisites

### macOS
```bash
brew install openjdk@21 gradle docker docker-compose
```

## Quick Start

1. **Clone and build:**
   ```bash
   git clone <repository-url>
   cd DMF-PoC
   ./gradlew build
   ```

2. **Start services:**
   ```bash
   docker-compose up -d
   ```

3. **Run migrations:**
   ```bash
   ./gradlew flywayMigrate
   ```

4. **Verify:**
   ```bash
   ./gradlew flywayInfo
   ```

## Common Tasks

### Database Migrations

```bash
# Check migration status
./gradlew flywayInfo

# Apply pending migrations
./gradlew flywayMigrate

# Undo last migration
./flyway-undo.sh <version>

# Generate migration from Exposed models
./gradlew generateMigration -Pargs="V2__Description"
```

See [docs/DATABASE.md](docs/DATABASE.md) for detailed migration workflow.

### Development

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Start application
./gradlew run
```

## Project Structure

- `/src/main/kotlin` — Application source code
- `/src/main/resources/db/migration` — Flyway migration scripts
- `/docs` — Documentation
- `/docker` — Docker configuration

## Tech Stack

- **Language:** Kotlin
- **ORM:** Exposed 1.0.0-rc-4
- **Database:** PostgreSQL
- **Migrations:** Flyway
- **Build:** Gradle
- **API Spec:** OpenAPI 3.0 (see `docs/documenten-1.5.0.yaml`)

## Documentation

- [Database Migrations](docs/DATABASE.md) - Migration workflow and limitations
- [API Specification](docs/documenten-1.5.0.yaml) - OpenAPI spec

## License

EUPL 1.2 - See [LICENSE.md](LICENSE.md)

