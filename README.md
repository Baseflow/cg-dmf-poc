# DMF-PoC

This repository is a Proof of Concept (PoC) for a new Document Registration Component (DRC) in the Common Ground landscape. It implements a minimal, functional version of the Documenten API, with additional filtering for new relations between non-Zaken objects.

## Prerequisites

### macOS
```
brew install openjdk@21 gradle docker docker-compose
```

## Setup & Development

1. Clone the repository.
2. Build with Gradle:  
   `./gradlew build`
3. Start development environment:  
   `docker-compose up`
4. Access API docs at `/docs/documenten-1.5.0.yaml`.

## Contribution

- Open source, EUPL 1.2 license.
- See `CODE_OF_CONDUCT.md` and `CONTRIBUTING.md` for guidelines.

## Deployment

- Development: Docker Compose
- Production: Kubernetes manifests provided