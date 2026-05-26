# k6 Performance Test Suite

This directory contains [k6](https://k6.io/) performance tests for the CG-DMF Documenten API.

## Structure

```
k6/
├── README.md
├── seed.js          # One-shot data seeding script (~10 000 EIOs, versions, OIOs)
├── perf.js          # Steady-state performance / load test
└── lib/
    ├── auth.js      # JWT helper (ZGW HS256 token)
    ├── data.js      # Shared test-data pools (metadata, files)
    └── api.js       # Thin wrappers around every API endpoint
```

## Prerequisites

```bash
brew install k6        # macOS
# or: https://k6.io/docs/get-started/installation/
```

## Configuration

All tuneable settings are driven by environment variables:

| Variable            | Default                         | Description                               |
|---------------------|---------------------------------|-------------------------------------------|
| `BASE_URL`          | `http://localhost:8080`         | Base URL of the DRC service               |
| `CATALOGUS_BASE_URL`| `https://openzaak.dev.baseflow.com/catalogi/api/v1` | Catalogi API base |
| `JWT_CLIENT_ID`     | `gzac`                          | ZGW JWT client_id for the DRC             |
| `JWT_CLIENT_SECRET` | *(required)*                    | ZGW JWT client secret for the DRC (HS256) |
| `OPENZAAK_CLIENT_ID`| `cg-dmf`                        | JWT client_id for the Catalogi API        |
| `OPENZAAK_CLIENT_SECRET` | `baseflow`                 | JWT secret for the Catalogi API           |
| `EIO_TARGET`        | `10000`                         | Number of EIOs to seed                    |
| `OIO_TARGET`        | `10000`                         | Number of OIOs to seed                    |
| `MAX_VERSIONS`      | `1000`                          | Maximum additional versions per EIO       |

## Running

### 1. Seed the database (run once)

```bash
JWT_CLIENT_SECRET=your-secret k6 run k6/seed.js
```

For a smaller local seed:

```bash
JWT_CLIENT_SECRET=your-secret \
  EIO_TARGET=100 OIO_TARGET=200 MAX_VERSIONS=5 \
  k6 run k6/seed.js
```

### 2. Performance test (after seeding)

```bash
JWT_CLIENT_SECRET=your-secret k6 run k6/perf.js
```

With custom thresholds / VUs:

```bash
JWT_CLIENT_SECRET=your-secret k6 run --vus 20 --duration 5m k6/perf.js
```

### 3. Output options

```bash
# Real-time dashboard (requires k6 cloud or k6 OSS + Grafana)
k6 run --out json=results.json k6/perf.js

# JUnit XML (for CI)
k6 run --out junit=results.xml k6/perf.js
```

## Notes

- **Phase 0** of the seeder creates 10 `InformatieObjectTypen` live in the OpenZaak Catalogi API
  and publishes them before any EIO is created. All EIOs reference one of these freshly created types.
- The seeding script uses a single VU and runs sequentially so you can watch progress in the console.
- Files are **randomly generated** in memory (no real PDF content needed).
- Each EIO gets between **1 and `MAX_VERSIONS`** total versions (geometrically distributed so most
  EIOs have few versions, mirroring real-world usage).
- OIOs are assigned randomly to existing EIOs; the unique constraint on `(informatieobject, object)`
  is respected by using a unique UUID per OIO as part of the object URL.

## GitHub Pages Trends

The trends page lives at `k6/trends/index.html` on the `gh-pages` branch.
It dynamically loads the latest 20 `k6` runs via `manifest.json` and fetches each run's
`perf-summary.json` from its run folder (`k6/<runId>/perf-summary.json`).

Per-run `k6/<runId>/` pages and `perf-summary.json` files are still published by CI.
The trends page itself is maintained directly on `gh-pages`.
