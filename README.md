# DMF-PoC

This repository is a Proof of Concept (PoC) for a new Document Registration Component (DRC) in the Common Ground
landscape. It implements a minimal, functional version of the Documenten API, with additional filtering for new
relations between non-Zaken objects.

## Document API implementation progress.

### enkelvoudiginformatieobjecten

| Endpoint                     | Method | Description                                                                               | Status                  | Remarks |
|------------------------------|--------|-------------------------------------------------------------------------------------------|-------------------------|---------|
| /                            | GET    | Get a list of enkelvoudiginformatieobjecten based on the given query parameters           | $${\color{green}Done}$$ |         | 
| /                            | POST   | Create a enkelvoudiginformatieobject                                                      | $${\color{green}Done}$$ |         | 
| /{UUID}/audittrail           | GET    | Get all audittrail records for a enkelvoudiginformatieobject                              | $${\color{green}Done}$$ |         |
| /{UUID}/audittrail/{at_UUID} | GET    | Get a single audittrail record based on enkelvoudiginformatieobject-id and audittrail-id  | $${\color{green}Done}$$ |         | 
| /{UUID}                      | GET    | Get a single enkelvoudigInformatieObject                                                  | $${\color{green}Done}$$ |         | 
| /{UUID}                      | PUT    | Fully updates a enkelvoudiginformatieobject                                               | $${\color{green}Done}$$ |         | 
| /{UUID}                      | PATCH  | Partially updates a enkelvoudiginformatieobject                                           | $${\color{green}Done}$$ |         | 
| /{UUID}                      | DELETE | Deletes a enkelvoudiginformatieobject                                                     | $${\color{green}Done}$$ |         | 
| /{UUID}                      | HEAD   | Retrieves headers for a specific enkelvoudiginformatieobject                              | $${\color{green}Done}$$ |         | 
| /{UUID}/download             | GET    | Download the binary data from the enkelvoudiginformatieobject                             | $${\color{green}Done}$$ |         | 
| /{UUID}/lock                 | POST   | Locks a enkelvoudiginformatieobject                                                       | $${\color{green}Done}$$ |         | 
| /{UUID}/unlock               | POST   | Unlocks enkelvoudiginformatieobject                                                       | $${\color{green}Done}$$ |         | 
| /{UUID}/_zoek                | POST   | Searches for enkelvoudiginformatieobject records, based on the search body of the request | $${\color{green}Done}$$ |         | 

### gebruiksrechten

| Endpoint | Method | Description                                                        | Status                                  | Remarks                         |
|----------|--------|--------------------------------------------------------------------|-----------------------------------------|---------------------------------|
| /        | GET    | Get a list of gebruiksrechten based on the given query parameters  | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /        | POST   | Create gebruiksrechten                                             | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID}  | GET    | Get a single gebruiksrechten instance based on Id                  | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID}  | PUT    | Completely updates a a single gebruiksrechten instance based on Id | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID}  | PATCH  | Partually updates a a single gebruiksrechten instance based on Id  | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID}  | DELETE | Deletes a single gebruiksrechten instance based on Id              | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID}  | HEAD   | Gets the headers for a single gebruiksrechten instance based on Id | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 

### objectinformatieobject

| Endpoint | Method | Description                                                                      | Status                  | Remarks |
|----------|--------|----------------------------------------------------------------------------------|-------------------------|---------|
| /        | GET    | Get a list of objectinformatieobject records based on the given query parameters | $${\color{green}Done}$$ |         | 
| /        | POST   | Create objectinformatieobject relation                                           | $${\color{green}Done}$$ |         | 
| /{UUID}  | GET    | Get a single objectinformatieobject relation based on Id                         | $${\color{green}Done}$$ |         | 
| /{UUID}  | DELETE | Deletes a single objectinformatieobject relation based on Id                     | $${\color{green}Done}$$ |         | 
| /{UUID}  | HEAD   | Gets the headers for a single objectinformatieobject relation based on Id        | $${\color{green}Done}$$ |         | 

### verzendingen

| Endpoint | Method | Description                                                    | Status                                  | Remarks                         |
|----------|--------|----------------------------------------------------------------|-----------------------------------------|---------------------------------|
| /        | GET    | Get a list of verzendingen based on the given query parameters | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /        | POST   | Create verzending                                              | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID}  | GET    | Get a single verzending instance based on Id                   | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID}  | PUT    | Completely updates a a single verzending instance based on Id  | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID}  | PATCH  | Partually updates a a single verzending instance based on Id   | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID}  | DELETE | Deletes a single verzending instance based on Id               | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 
| /{UUID}  | HEAD   | Gets the headers for a single verzending instance based on Id  | $${\color{red}Not \space implemented}$$ | Out of scope for our PoC so far | 

### bestandsdelen

| Endpoint | Method | Description           | Status                                  | Remarks |
|----------|--------|-----------------------|-----------------------------------------|---------|
| /{UUID}  | PUT    | Upload a bestandsdeel | $${\color{red}Not \space implemented}$$ |         | 

### overig

| Functionaliteit | Status                          | Remarks |
|-----------------|---------------------------------|---------|
| Notificaties    | Out of scope for our PoC so far |         |
| API scopes      | Out of scope for our PoC so far |         |

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

## Configuration

All settings are read from environment variables (or a `.env` file in development).
Copy `.env.example` to `.env` and adjust the values before starting the application.

### Database

| Variable            | Default      | Description       |
|---------------------|--------------|-------------------|
| `DATABASE_HOST`     | `localhost`  | PostgreSQL host   |
| `DATABASE_PORT`     | `5432`       | PostgreSQL port   |
| `DATABASE_NAME`     | `documenten` | Database name     |
| `DATABASE_USER`     | `documenten` | Database user     |
| `DATABASE_PASSWORD` | `documenten` | Database password |

### Application

| Variable   | Default | Description                     |
|------------|---------|---------------------------------|
| `APP_PORT` | `8080`  | HTTP port the server listens on |

### MinIO (object storage)

| Variable            | Default                 | Description                                   |
|---------------------|-------------------------|-----------------------------------------------|
| `MINIO_ENDPOINT`    | `http://localhost:9000` | MinIO / S3-compatible endpoint URL            |
| `MINIO_ACCESS_KEY`  | `minioadmin`            | Access key (username)                         |
| `MINIO_SECRET_KEY`  | `minioadmin`            | Secret key (password)                         |
| `MINIO_BUCKET_NAME` | `documenten`            | Bucket used for document storage              |
| `MINIO_URL_EXPIRY`  | `PT15M`                 | Pre-signed URL expiry as an ISO-8601 duration |

### Authentication

| Variable                 | Default                               | Description                                                                                                       |
|--------------------------|---------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `OIDC_ISSUER`            | `http://localhost:8081/realms/cg-dmf` | Issuer URL of the OIDC provider used to validate incoming JWT tokens                                              |
| `ZGW_ALLOWED_CLIENT_IDS` | `gzac`                                | Comma-separated list of `client_id` values accepted for ZGW-style JWT authentication (GZAC / Valtimo / Open Zaak) |

### OpenZaak integration

Only needed when `OPENZAAK_VALIDATION_ENABLED=true` (the default).

| Variable                      | Default                             | Description                                             |
|-------------------------------|-------------------------------------|---------------------------------------------------------|
| `OPENZAAK_ENDPOINT`           | `https://openzaak.dev.baseflow.com` | Base URL of the Open Zaak instance                      |
| `OPENZAAK_CLIENT_ID`          | `cg-dmf`                            | Client ID used to authenticate with Open Zaak           |
| `OPENZAAK_CLIENT_SECRET`      | `baseflow`                          | Client secret used to authenticate with Open Zaak       |
| `OPENZAAK_VALIDATION_ENABLED` | `true`                              | Set to `false` to skip Open Zaak object-type validation |

### Notifications (Open Notificaties API)

Notifications are **disabled** when `NOTIFICATION_API_URL` or `NOTIFICATION_API_TOKEN` is left blank.

| Variable                 | Default      | Description                                                                           |
|--------------------------|--------------|---------------------------------------------------------------------------------------|
| `NOTIFICATION_API_URL`   | _(empty)_    | Base URL of the Open Notificaties API, e.g. `https://notificaties.example.com/api/v1` |
| `NOTIFICATION_API_TOKEN` | _(empty)_    | Bearer token with the `notificaties.publiceren` scope                                 |
| `NOTIFICATION_KANAAL`    | `documenten` | Notification channel name (kanaal) as registered in Open Notificaties                 |
| `NOTIFICATION_SOURCE`    | `drc`        | Source identifier sent with each notification                                         |

## Documentation

- [Database Migrations](docs/DATABASE.md) - Migration workflow and limitations
- [API Specification](docs/documenten-1.5.0.yaml) - OpenAPI spec

## License

EUPL 1.2 - See [LICENSE.md](LICENSE.md)

