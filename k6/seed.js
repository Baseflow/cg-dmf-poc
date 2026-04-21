// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
//
// Data seeding script for the CG-DMF Documenten API.
//
// Generates:
//   - 10 InformatieObjectTypen in the OpenZaak Catalogi API (Phase 0)
//   - ~EIO_TARGET EnkelvoudigInformatieObjecten            (Phase 1)
//   - For each EIO: 1..MAX_VERSIONS total document versions (Phase 2)
//   - ~OIO_TARGET ObjectInformatieObjecten                 (Phase 3)
//
// Usage:
//   JWT_CLIENT_SECRET=<secret> k6 run k6/seed.js
//
// Optional overrides (env vars):
//   BASE_URL, JWT_CLIENT_ID, EIO_TARGET, OIO_TARGET, MAX_VERSIONS
//   CATALOGUS_BASE_URL       — base URL of the OpenZaak Catalogi API
//   OPENZAAK_CLIENT_ID       — client_id for Catalogi JWT (default: cg-dmf)
//   OPENZAAK_CLIENT_SECRET   — secret for Catalogi JWT   (default: baseflow)

import {check} from 'k6';
import http from 'k6/http';
import {Counter, Rate} from 'k6/metrics';
import {zgwBearer} from './lib/auth.js';
import {
    BRONORGANISATIES, TALEN, AUTEURS, TITELS, STATUSSEN,
    VERTROUWELIJKHEDEN, BESCHRIJVINGEN,
    OBJECT_TYPES,
    pick, pickFile, pickTrefwoorden, randomDate, geometricInt,
} from './lib/data.js';
import {
    createEio, updateEio, createOio,
    createInformatieobjecttype, publishInformatieobjecttype, listCatalogussen,
} from './lib/api.js';
import { IOT_URLS_FILE } from './lib/constants.js';

// Module-level store left intentionally empty — IOT URLs are fetched fresh
// from the Catalogi API inside handleSummary() via HTTP.

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CATALOGUS_BASE = __ENV.CATALOGUS_BASE_URL || 'https://openzaak.dev.baseflow.com/catalogi/api/v1';
const JWT_CLIENT_ID = __ENV.JWT_CLIENT_ID || 'gzac';
const JWT_CLIENT_SECRET = __ENV.JWT_CLIENT_SECRET;
const OPENZAAK_CLIENT_ID = __ENV.OPENZAAK_CLIENT_ID || 'cg-dmf';
const OPENZAAK_CLIENT_SECRET = __ENV.OPENZAAK_CLIENT_SECRET || 'baseflow';
const EIO_TARGET = parseInt(__ENV.EIO_TARGET || '10000', 10);
const OIO_TARGET = parseInt(__ENV.OIO_TARGET || '10000', 10);
const MAX_VERSIONS = parseInt(__ENV.MAX_VERSIONS || '1000', 10);
const IOT_COUNT = 10; // number of informatieobjecttypen to create

if (!JWT_CLIENT_SECRET) {
    throw new Error('JWT_CLIENT_SECRET environment variable is required');
}

// ---------------------------------------------------------------------------
// k6 options — single VU, sequential so we can build the EIO id list
// ---------------------------------------------------------------------------

export const options = {
    vus: 1,
    iterations: 1,
    thresholds: {
        // Seed is allowed to be slow; just ensure no hard failures
        'seed_errors': ['count == 0'],
    },
};

// ---------------------------------------------------------------------------
// Custom metrics
// ---------------------------------------------------------------------------

const seedErrors = new Counter('seed_errors');
const eioCreated = new Counter('eio_created');
const verCreated = new Counter('versions_created');
const oioCreated = new Counter('oio_created');
const iotCreated = new Counter('iot_created');
const successRate = new Rate('seed_success_rate');

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeHeaders(bearer) {
    return {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'Authorization': bearer,
    };
}

function objectUrl(type, id) {
    // Synthetic object URLs; each OIO gets a unique object URL to satisfy the
    // unique constraint on (informatieobject, object).
    switch (type) {
        case 'zaak':
            return `https://zaken.example.com/api/v1/zaken/${id}`;
        case 'besluit':
            return `https://besluiten.example.com/api/v1/besluiten/${id}`;
        default:
            return `https://objecten.example.com/api/v1/objecten/${id}`;
    }
}

function randomUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
    });
}

/**
 * Build a full EIO create/update request body.
 * @param {string[]} iotUrls — pool of live informatieobjecttype URLs to pick from
 */
function eioBody(iotUrls) {
    const file = pickFile();
    const hasFile = Math.random() > 0.1; // 90 % of records have a file

    return {
        bronorganisatie: pick(BRONORGANISATIES),
        identificatie: `K6-${randomUUID()}`,
        titel: pick(TITELS),
        taal: pick(TALEN),
        auteur: pick(AUTEURS),
        creatiedatum: randomDate(),
        status: pick(STATUSSEN),
        vertrouwelijkheidaanduiding: pick(VERTROUWELIJKHEDEN),
        beschrijving: pick(BESCHRIJVINGEN),
        trefwoorden: pickTrefwoorden(4),
        informatieobjecttype: pick(iotUrls),
        bestandsnaam: hasFile ? file.name : undefined,
        formaat: hasFile ? file.formaat : undefined,
        inhoud: hasFile ? file.base64 : undefined,
        bestandsomvang: hasFile ? file.size : undefined,
    };
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

export default function () {
    const bearer = zgwBearer(JWT_CLIENT_ID, JWT_CLIENT_SECRET);
    const headers = makeHeaders(bearer);

    // -----------------------------------------------------------------------
    // Phase 0: Create 10 InformatieObjectTypen in the OpenZaak Catalogi API
    // -----------------------------------------------------------------------
    console.log(`[seed] Phase 0 — creating ${IOT_COUNT} informatieobjecttypen in Catalogi API …`);

    const openzaakBearer = zgwBearer(OPENZAAK_CLIENT_ID, OPENZAAK_CLIENT_SECRET);
    const openzaakHeaders = makeHeaders(openzaakBearer);

    // Resolve the catalogus URL from the first available catalogus
    let catalogusUrl = null;
    const catalogusRes = listCatalogussen(CATALOGUS_BASE, openzaakHeaders);
    if (catalogusRes.status === 200) {
        const body = catalogusRes.json();
        const results = body.results || body; // some versions return array directly
        const first = Array.isArray(results) ? results[0] : results[0];
        catalogusUrl = first && first.url;
    }
    if (!catalogusUrl) {
        console.error(`[seed] Could not resolve catalogus URL (status ${catalogusRes.status}); aborting Phase 0.`);
        console.error(`[seed] Response: ${(catalogusRes.body || '').substring(0, 300)}`);
        return;
    }
    console.log(`[seed] Using catalogus: ${catalogusUrl}`);

    const iotUrls = [];
    const omschrijvingen = [
        'Vergunningsdocument', 'Bezwaarschrift', 'Overeenkomst',
        'Jaarverslag', 'Notulen', 'Offerte', 'Factuur',
        'Inspectierapport', 'Correspondentie', 'Internmemo',
    ];

    for (let i = 0; i < IOT_COUNT; i++) {
        const omschrijving = `K6-${omschrijvingen[i]}-${randomUUID().slice(0, 8)}`;
        const res = createInformatieobjecttype(CATALOGUS_BASE, openzaakHeaders, catalogusUrl, omschrijving);
        const ok = check(res, { 'iot create 201': (r) => r.status === 201 });

        if (!ok) {
            seedErrors.add(1);
            console.error(
                `[seed] IOT create failed (${res.status}): ${(res.body || '').substring(0, 300)}`
            );
            continue;
        }

        const iot = res.json();
        const iotUrl = iot.url;

        // Publish the concept so the DRC can validate it successfully
        const iotUuid = iotUrl.split('/').pop();
        const refreshedOpenzaakBearer = zgwBearer(OPENZAAK_CLIENT_ID, OPENZAAK_CLIENT_SECRET);
        const publishRes = publishInformatieobjecttype(CATALOGUS_BASE, makeHeaders(refreshedOpenzaakBearer), iotUuid);
        const published = check(publishRes, { 'iot publish 200': (r) => r.status === 200 });

        if (!published) {
            // concept types still return 200 from the DRC validation — warn but continue
            console.warn(
                `[seed] IOT publish failed (${publishRes.status}) for ${iotUrl}: ${(publishRes.body || '').substring(0, 200)}`
            );
        }

        iotUrls.push(iotUrl);
        iotCreated.add(1);
        successRate.add(1);
        console.log(`[seed]   Created IOT [${i + 1}/${IOT_COUNT}]: ${iotUrl}`);
    }

    if (iotUrls.length === 0) {
        console.error('[seed] No informatieobjecttypen were created; aborting.');
        return;
    }
    console.log(`[seed] Phase 0 done — ${iotUrls.length} informatieobjecttypen created.`);


    const eioIds = [];

    // -----------------------------------------------------------------------
    // Phase 1: Create EIOs
    // -----------------------------------------------------------------------
    console.log(`[seed] Phase 1 — creating ${EIO_TARGET} EIOs …`);

    for (let i = 0; i < EIO_TARGET; i++) {
        if (i > 0 && i % 500 === 0) {
            // Refresh token every 500 requests (tokens expire)
            const freshBearer = zgwBearer(JWT_CLIENT_ID, JWT_CLIENT_SECRET);
            headers['Authorization'] = freshBearer;
            console.log(`[seed]   ${i}/${EIO_TARGET} EIOs created`);
        }

        const res = createEio(BASE_URL, headers, eioBody(iotUrls));
        const ok = check(res, {
            'eio create 201': (r) => r.status === 201,
        });

        if (ok) {
            const body = res.json();
            eioIds.push(body.id);
            eioCreated.add(1);
            successRate.add(1);
        } else {
            seedErrors.add(1);
            successRate.add(0);
            console.error(
                `[seed] EIO create failed (${res.status}): ${(res.body || '').substring(0, 300)}`
            );
        }
    }

    console.log(`[seed] Phase 1 done — ${eioIds.length} EIOs created.`);

    if (eioIds.length === 0) {
        console.error('[seed] No EIOs were created; aborting.');
        return;
    }

    // -----------------------------------------------------------------------
    // Phase 2: Add extra versions
    // Each EIO already has version 1 from Phase 1.
    // We add between 0 and (MAX_VERSIONS - 1) additional versions using PUT.
    // Most EIOs get very few extra versions (geometric distribution).
    // -----------------------------------------------------------------------
    console.log(`[seed] Phase 2 — adding extra versions (max ${MAX_VERSIONS}) …`);

    let totalVersions = eioIds.length; // each EIO starts with 1 version

    for (let i = 0; i < eioIds.length; i++) {
        if (i > 0 && i % 500 === 0) {
            headers['Authorization'] = zgwBearer(JWT_CLIENT_ID, JWT_CLIENT_SECRET);
            console.log(`[seed]   ${i}/${eioIds.length} EIOs versioned`);
        }

        const extraVersions = geometricInt(MAX_VERSIONS - 1) - 1; // 0 to MAX_VERSIONS-1
        for (let v = 0; v < extraVersions; v++) {
            const res = updateEio(BASE_URL, headers, eioIds[i], eioBody(iotUrls));
            const ok = check(res, {
                'eio update 200': (r) => r.status === 200,
            });
            if (ok) {
                verCreated.add(1);
                totalVersions++;
                successRate.add(1);
            } else {
                seedErrors.add(1);
                successRate.add(0);
                // Don't abort — some failures are expected (e.g. locked records)
            }
        }
    }

    console.log(`[seed] Phase 2 done — ${totalVersions} total versions across ${eioIds.length} EIOs.`);

    // -----------------------------------------------------------------------
    // Phase 3: Create OIOs
    // Assign OIO_TARGET OIOs to randomly chosen EIOs.
    // Each OIO gets a unique object URL (using a UUID) so the unique constraint
    // on (informatieobject, object) is never violated.
    // -----------------------------------------------------------------------
    console.log(`[seed] Phase 3 — creating ${OIO_TARGET} OIOs …`);

    for (let i = 0; i < OIO_TARGET; i++) {
        if (i > 0 && i % 500 === 0) {
            headers['Authorization'] = zgwBearer(JWT_CLIENT_ID, JWT_CLIENT_SECRET);
            console.log(`[seed]   ${i}/${OIO_TARGET} OIOs created`);
        }

        const eioId = eioIds[Math.floor(Math.random() * eioIds.length)];
        const objType = pick(OBJECT_TYPES);
        const objId = randomUUID(); // unique per OIO → no constraint violations
        const objUrl = objectUrl(objType, objId);

        const res = createOio(BASE_URL, headers, eioId, objUrl, objType);
        const ok = check(res, {
            'oio create 201': (r) => r.status === 201,
        });

        if (ok) {
            oioCreated.add(1);
            successRate.add(1);
        } else {
            seedErrors.add(1);
            successRate.add(0);
            console.error(
                `[seed] OIO create failed (${res.status}): ${(res.body || '').substring(0, 300)}`
            );
        }
    }

    console.log(
        `[seed] Seeding complete — IOTs: ${iotUrls.length}, EIOs: ${eioIds.length}, OIOs target: ${OIO_TARGET}`
    );

    console.log(`[seed] IOT URLs will be written to ${IOT_URLS_FILE} — use with perf.js.`);
}

/**
 * handleSummary is called by k6 after the test finishes.
 * Since k6 v0.43, handleSummary can make HTTP requests, so we query the
 * Catalogi API directly to fetch the IOT URLs that were just created.
 * This avoids any dependency on module-level state, which is not shared
 * across k6's separate VU runtimes.
 */
export function handleSummary(data) {
    const openzaakBearer = zgwBearer(OPENZAAK_CLIENT_ID, OPENZAAK_CLIENT_SECRET);
    const headers = makeHeaders(openzaakBearer);

    const iotUrls = [];
    let nextUrl = `${CATALOGUS_BASE}/informatieobjecttypen`;

    while (nextUrl) {
        const res = http.get(nextUrl, { headers });
        if (res.status !== 200) {
            console.error(`[seed] handleSummary: failed to list IOTs (${res.status})`);
            break;
        }
        const body = res.json();
        const results = Array.isArray(body) ? body : (body.results || []);
        for (const iot of results) {
            if (iot.url && iot.omschrijving && iot.omschrijving.startsWith('K6-')) {
                iotUrls.push(iot.url);
            }
        }
        nextUrl = body.next || null;
    }

    console.log(`[seed] Writing ${iotUrls.length} IOT URLs to ${IOT_URLS_FILE} …`);
    return {
        [IOT_URLS_FILE]: JSON.stringify({ iotUrls }, null, 2),
        stdout: `\n[seed] ${iotUrls.length} IOT URLs written to ${IOT_URLS_FILE}. Run perf.js to execute load tests.\n`,
    };
}

