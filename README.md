# DMF-PoC

This repository is a Proof of Concept (PoC) for a new Document Registration Component (DRC) in the Common Ground landscape. It implements a minimal, functional version of the Documenten API, with additional filtering for new relations between non-Zaken objects.

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

