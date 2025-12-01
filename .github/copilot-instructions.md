# Copilot Project Instructions

## Project Overview

This repository is a Proof of Concept (PoC) for a new Document Registration Component (DRC) in the Common Ground landscape.
It implements a minimal, functional version of the Documenten API,
with additional filtering for new relations between non-Zaken objects.

## Tech Stack

- Language: Kotlin
- ORM: Exposed
- Build Tool: Gradle
- Database: PostgreSQL
- Containerization: Docker, Docker Compose (development), Kubernetes (production)
- OpenAPI Spec: docs/documenten.yaml
- License: EUPL 1.2
- Code of Conduct: included

## Directory Structure

- `/src/main/kotlin` — Application source code
- `/docs` — Documentation, including OAS spec
- `/docker` — Docker and Docker Compose files

## Setup & Development

1. Clone the repository.
2. Build with Gradle:  
   `./gradlew build`
3. Start development environment:  
   `docker-compose up`
4. Access API docs at `/docs/documenten-1.5.0.yaml`.

## Service Description

This is a Kotlin application exposing the Documenten API, using Exposed ORM for PostgreSQL persistence.
It implements the basic EnkelvoudigInformationObject and ObjectInformationObject endpoints,
but will mostly ignore the Audittrail, verzendingen and gebruiksrechten part of the API. We will also not be sending Notifications at this stage.

It should support additional filtering for new object relations other than Zaken.

## Contribution

- Open source, EUPL 1.2 license.
- See `CODE_OF_CONDUCT.md` and `CONTRIBUTING.md` for guidelines.

When possible i want new files to have a prefix of
```
// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
```

## Deployment

- Development: Docker Compose
- Production: Kubernetes manifests provided

## API Specification

- The original API is in `docs/documenten-1.5.0.yaml` for the OpenAPI specification.
