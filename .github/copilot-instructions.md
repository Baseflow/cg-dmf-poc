# Copilot Project Instructions

## Project Overview

This repository is a Proof of Concept (PoC) for a new Document Registration Component (DRC) in the Common Ground landscape.
It implements a minimal, functional version of the Documenten API,
with additional filtering for new relations between non-Zaken objects.

## Tech Stack

- Language: Kotlin
- Java: version 21 (LTS)
- ORM: Exposed v1.0.0 (or the rc version)
- Build Tool: Gradle
- Database: PostgreSQL
- Migration Tool: Flyway
- Containerization: Docker, Docker Compose (development), Kubernetes (production)

## Directory Structure

- `/src/main/kotlin` — Application source code
  - `api/` — HTTP layer, split by domain (package `com.baseflow.api`)
    - `documenten/` — Documenten API 1.5.0 routes and module (`com.baseflow.api.documenten`)
      - `routes/` — Individual route handlers (EIO, OIO, SIO, BestandsDelen, AuditTrail)
    - `admin/` — Internal management endpoints (`com.baseflow.api.admin`)
      - `routes/` — BlobStorageRepository management routes
    - `infra/` — Health checks and OpenAPI spec endpoints (`com.baseflow.api.infra`)
    - `wopi/` — WOPI protocol support (planned, not yet implemented) (`com.baseflow.api.wopi`)
    - `middleware/` — Shared Ktor plugins (AuditTrail, Notification, ConditionalHeaders, etc.)
    - `models/` — Shared request/response models (not yet domain-split)
  - `config/` — Application configuration and dependency injection
  - `entities/` — Exposed ORM table definitions
  - `services/` — Business logic services
  - `tooling/` — Gradle tasks (migration generator, OpenAPI export)
- `/src/main/resources/db/migration` — Database migration scripts (Flyway)
- `/docs` — Documentation, including OAS spec

## Database Migrations

The project uses Flyway for managing database schema changes. We use Exposed 1.0.0-rc-4 with migration utilities.

All tables should be listed in `src/main/kotlin/tooling/AllTables.kt`.

### Quick Commands
- **Apply migrations:** `./gradlew flywayMigrate`
- **Check status:** `./gradlew flywayInfo`
- **Validate:** `./gradlew flywayValidate`
- **Undo migration:** `./flyway-undo.sh <version>` (manual script, Community Edition limitation)

### Creating Migrations from Exposed Models
1. Update your Exposed `Table` definition in `src/main/kotlin/entities/`
2. Generate migration: `./gradlew generateMigration -Pargs="V2__Description"`
3. Review generated SQL (may need manual enhancement)
4. Create matching undo script: `U2__Description.sql`
5. Apply: `./gradlew flywayMigrate`

**Important:** The migration generator provides a starting point but may not detect all changes. Always review and test generated SQL.

### Migration Files
- **Location:** `src/main/resources/db/migration/`
- **Naming:** `V<version>__<Description>.sql` for upgrades, `U<version>__<Description>.sql` for undos
- **Undo scripts:** Must be created manually (Flyway Community Edition limitation)

See `docs/DATABASE.md` for detailed workflow and known limitations.


## Code Style

This project uses [Spotless](https://github.com/diffplug/spotless) for Kotlin code formatting.

After making significant code changes, always run:

```bash
./gradlew spotlessApply
```

You can check for formatting violations without applying fixes with:

```bash
./gradlew spotlessCheck
```

## Setup & Development

1. Clone the repository.
2. Build with Gradle:  
   `./gradlew build`
3. Start development environment:  
   `docker-compose up`
4. Access API docs at `/docs/documenten-1.5.0.yaml`.

## Service Description

This is a Kotlin application exposing the Documenten API, using Exposed ORM for PostgreSQL persistence.
It implements the basic EnkelvoudigInformatieObject and ObjectInformatieObject endpoints,
but will mostly ignore the Audittrail, Verzendingen and Gebruiksrechten part of the API. We will also not be sending Notifications at this stage.

It should support additional filtering for new object relations other than Zaken.

## Contribution

- Open source, EUPL 1.2 license.
- See `CODE_OF_CONDUCT.md` and `CONTRIBUTING.md` for guidelines.

When possible, I want new files to have a prefix of:
```
// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
```
But use appropriate commenting, and some files may simply not be suited to be prefixed with this.
If you edit a file and the years in the header are not matching, you can update it to a range of the year that was already there and the year in the example.

## Deployment

- Development: Docker Compose
- Production: Kubernetes manifests provided

## Testing

- Unit tests should be made for business logic
- Unit tests are located in `src/test/kotlin`
- Examples to use and test a running server are also desired in the form of Bruno (a Postman like tool). These are stored in the .bruno directory.

## API Specification

- The original API is in `docs/documenten-1.5.0.yaml` for the OpenAPI specification (OAS).
