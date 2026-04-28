// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
//
// Steady-state performance / load test for the CG-DMF Documenten API.
//
// Assumes the database has been seeded with seed.js first.
//
// Scenario mix (weighted):
//   40 % — GET single EIO by ID
//   25 % — GET list EIOs (various filters)
//   15 % — CREATE new EIO
//   10 % — PATCH (partial update) existing EIO
//    5 % — GET OIO list filtered by informatieobject (EIO URL)
//    5 % — CREATE OIO
//
// Usage:
//   JWT_CLIENT_SECRET=<secret> k6 run k6/perf.js
//
// Optional env vars:
//   BASE_URL, JWT_CLIENT_ID, CATALOGUS_BASE_URL
//   RAMP_DURATION  (default: 1m)   — ramp-up / ramp-down duration
//   STEADY_DURATION (default: 5m)  — sustained load duration
//   MAX_VUS        (default: 50)

import { check, sleep } from 'k6';
import http from 'k6/http';
import { Rate, Counter } from 'k6/metrics';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { zgwBearer } from './lib/auth.js';
import {
  BRONORGANISATIES, TALEN, AUTEURS, TITELS, STATUSSEN,
  VERTROUWELIJKHEDEN, BESCHRIJVINGEN,
  OBJECT_TYPES,
  pick, pickFile, pickTrefwoorden, randomDate,
} from './lib/data.js';
import {
  createEio, listEio, getEio, patchEio, createOio, listOio,
} from './lib/api.js';
import { IOT_URLS_FILE } from './lib/constants.js';

// Read IOT URLs written by seed.js (must be run first)
let _iotUrlsData;
try {
  _iotUrlsData = JSON.parse(open(IOT_URLS_FILE));
} catch (error) {
  throw new Error(
    `Failed to load IOT URLs from ${IOT_URLS_FILE}. ` +
    'Make sure k6/seed.js has been run first and that the file contains valid JSON. ' +
    `Original error: ${error.message}`,
  );
}

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

const BASE_URL          = __ENV.BASE_URL          || 'http://localhost:8080';
const CATALOGUS_BASE    = __ENV.CATALOGUS_BASE_URL || 'https://openzaak.dev.baseflow.com/catalogi/api/v1';
const JWT_CLIENT_ID     = __ENV.JWT_CLIENT_ID     || 'gzac';
const JWT_CLIENT_SECRET = __ENV.JWT_CLIENT_SECRET;
const OPENZAAK_CLIENT_ID     = __ENV.OPENZAAK_CLIENT_ID     || 'cg-dmf';
const OPENZAAK_CLIENT_SECRET = __ENV.OPENZAAK_CLIENT_SECRET || 'baseflow';

if (!JWT_CLIENT_SECRET) {
  throw new Error('JWT_CLIENT_SECRET environment variable is required');
}

const RAMP_DURATION   = __ENV.RAMP_DURATION   || '1m';
const STEADY_DURATION = __ENV.STEADY_DURATION || '5m';
const MAX_VUS         = parseInt(__ENV.MAX_VUS || '50', 10);

// ---------------------------------------------------------------------------
// k6 scenario options
// ---------------------------------------------------------------------------

export const options = {
  scenarios: {
    load: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: RAMP_DURATION,   target: MAX_VUS },   // ramp up
        { duration: STEADY_DURATION, target: MAX_VUS },   // steady
        { duration: RAMP_DURATION,   target: 0        },  // ramp down
      ],
    },
  },
  thresholds: {
    // Median (p50) — normal-case baseline
    // p(95) — tail / worst-case ceiling
    'http_req_duration{name:eio_get}':    ['p(50)<100',  'p(95)<1000'],
    'http_req_duration{name:eio_list}':   ['p(50)<200',  'p(95)<1000'],
    'http_req_duration{name:eio_create}': ['p(50)<300',  'p(95)<1000'],
    'http_req_duration{name:eio_patch}':  ['p(50)<200',  'p(95)<1000'],
    'http_req_duration{name:oio_list}':   ['p(50)<150',  'p(95)<1000'],
    'http_req_duration{name:oio_create}': ['p(50)<150',  'p(95)<3000'],
    // Reliability gate: no failed functional checks across all scenarios.
    // Uses Counter threshold to avoid flaky Rate-threshold booleans in summary export.
    'perf_errors_total': ['count==0'],
  },
};

// ---------------------------------------------------------------------------
// Custom metrics
// ---------------------------------------------------------------------------

const perfSuccessRate = new Rate('perf_success_rate');
const perfErrorsTotal = new Counter('perf_errors_total');
const eioGetErrors    = new Counter('eio_get_errors');
const eioListErrors   = new Counter('eio_list_errors');
const eioCreateErrors = new Counter('eio_create_errors');
const eioPatchErrors  = new Counter('eio_patch_errors');
const oioListErrors   = new Counter('oio_list_errors');
const oioCreateErrors = new Counter('oio_create_errors');

// ---------------------------------------------------------------------------
// Per-VU state — refreshed once per iteration
// ---------------------------------------------------------------------------

// Cache known EIO IDs per VU so GET/PATCH scenarios can target real records.
// Populated lazily on the first list call.
let _knownEioIds = [];
let _tokenCacheTime = 0;
let _cachedBearer = null;

function getBearer() {
  // Refresh token at most once per minute per VU
  const now = Date.now();
  if (!_cachedBearer || now - _tokenCacheTime > 55000) {
    _cachedBearer = zgwBearer(JWT_CLIENT_ID, JWT_CLIENT_SECRET);
    _tokenCacheTime = now;
  }
  return _cachedBearer;
}

function makeHeaders() {
  return {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'Authorization': getBearer(),
  };
}


function randomUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

function objectUrl(type, id) {
  switch (type) {
    case 'zaak':    return `https://zaken.example.com/api/v1/zaken/${id}`;
    case 'besluit': return `https://besluiten.example.com/api/v1/besluiten/${id}`;
    default:        return `https://objecten.example.com/api/v1/objecten/${id}`;
  }
}

/** Ensure _knownEioIds has at least some entries by doing a list call. */
function ensureKnownIds(headers) {
  if (_knownEioIds.length > 0) return;
  const res = listEio(BASE_URL, headers, { pageSize: 100 });
  if (res.status === 200) {
    const body = res.json();
    if (body.results && Array.isArray(body.results)) {
      _knownEioIds = body.results.map((e) => e.id);
    }
  }
}

function pickKnownId() {
  if (_knownEioIds.length === 0) return null;
  return _knownEioIds[randomIntBetween(0, _knownEioIds.length - 1)];
}

function eioCreateBody(iotUrls) {
  const file    = pickFile();
  const hasFile = Math.random() > 0.1;
  return {
    bronorganisatie:             pick(BRONORGANISATIES),
    identificatie:               `K6-${randomUUID()}`,
    titel:                       pick(TITELS),
    taal:                        pick(TALEN),
    auteur:                      pick(AUTEURS),
    creatiedatum:                randomDate(),
    status:                      pick(STATUSSEN),
    vertrouwelijkheidaanduiding: pick(VERTROUWELIJKHEDEN),
    beschrijving:                pick(BESCHRIJVINGEN),
    trefwoorden:                 pickTrefwoorden(4),
    informatieobjecttype:        pick(iotUrls),
    bestandsnaam:                hasFile ? file.name    : undefined,
    formaat:                     hasFile ? file.formaat : undefined,
    inhoud:                      hasFile ? file.base64  : undefined,
    bestandsomvang:              hasFile ? file.size    : undefined,
  };
}

function eioPatchBody() {
  return {
    titel:                       pick(TITELS),
    auteur:                      pick(AUTEURS),
    beschrijving:                pick(BESCHRIJVINGEN),
    status:                      pick(STATUSSEN),
    vertrouwelijkheidaanduiding: pick(VERTROUWELIJKHEDEN),
    trefwoorden:                 pickTrefwoorden(3),
  };
}

// ---------------------------------------------------------------------------
// Scenario functions (weighted dispatch)
// ---------------------------------------------------------------------------

function scenarioGetEio(headers) {
  const id = pickKnownId();
  if (!id) return;
  const res = getEio(BASE_URL, headers, id);
  const ok  = check(res, { 'GET eio 200': (r) => r.status === 200 });
  perfSuccessRate.add(ok ? 1 : 0);
  if (!ok) {
    perfErrorsTotal.add(1);
    eioGetErrors.add(1);
  }
}

function scenarioListEio(headers) {
  // Rotate through different filter combinations
  const variant = randomIntBetween(0, 5);
  let params = { pageSize: 25 };

  switch (variant) {
    case 0: params.auteur             = pick(AUTEURS); break;
    case 1: params.status             = pick(STATUSSEN); break;
    case 2: params.vertrouwelijkheidaanduiding = pick(VERTROUWELIJKHEDEN); break;
    case 3: params.trefwoorden        = pickTrefwoorden(2).join(','); break;
    case 4: params.bronorganisatie    = pick(BRONORGANISATIES); break;
    case 5: /* no filter, just page */ break;
  }

  const res = listEio(BASE_URL, headers, params);
  const ok  = check(res, { 'LIST eio 200': (r) => r.status === 200 });
  perfSuccessRate.add(ok ? 1 : 0);
  if (!ok) {
    perfErrorsTotal.add(1);
    eioListErrors.add(1);
  }

  // Cache IDs from result to improve GET/PATCH coverage
  if (ok) {
    const body = res.json();
    if (body.results && Array.isArray(body.results)) {
      for (const e of body.results) {
        if (_knownEioIds.length < 500 && !_knownEioIds.includes(e.id)) {
          _knownEioIds.push(e.id);
        }
      }
    }
  }
}

function scenarioCreateEio(headers, iotUrls) {
  const res = createEio(BASE_URL, headers, eioCreateBody(iotUrls));
  const ok  = check(res, { 'CREATE eio 201': (r) => r.status === 201 });
  perfSuccessRate.add(ok ? 1 : 0);
  if (!ok) {
    perfErrorsTotal.add(1);
    eioCreateErrors.add(1);
  } else {
    // Register newly created ID for subsequent reads
    const body = res.json();
    if (body.id && _knownEioIds.length < 500) _knownEioIds.push(body.id);
  }
}

function scenarioPatchEio(headers) {
  const id = pickKnownId();
  if (!id) return;
  const res = patchEio(BASE_URL, headers, id, eioPatchBody());
  // 200 or 409 (locked) are both acceptable here
  const ok  = check(res, { 'PATCH eio 200 or 409': (r) => r.status === 200 || r.status === 409 });
  perfSuccessRate.add(ok ? 1 : 0);
  if (!ok) {
    perfErrorsTotal.add(1);
    eioPatchErrors.add(1);
  }
}

function scenarioListOio(headers) {
  const id = pickKnownId();
  const params = id
    ? { informatieobject: `${BASE_URL}/documenten/api/v1/enkelvoudiginformatieobjecten/${id}` }
    : {};
  const res = listOio(BASE_URL, headers, params);
  const ok  = check(res, { 'LIST oio 200': (r) => r.status === 200 });
  perfSuccessRate.add(ok ? 1 : 0);
  if (!ok) {
    perfErrorsTotal.add(1);
    oioListErrors.add(1);
  }
  // A fresh UUID is used for the object URL, so 201 is the expected successful response here.
  // Any non-201 response is treated as a failure by the performance metrics below.
}

function scenarioCreateOio(headers) {
  const eioId   = pickKnownId();
  if (!eioId) return;
  const objType = pick(OBJECT_TYPES);
  const objId   = randomUUID();
  const res     = createOio(BASE_URL, headers, eioId, objectUrl(objType, objId), objType);
  // 201 OK, 400 might happen if there is already an OIO with the same (eio, object) tuple
  const ok  = check(res, { 'CREATE oio 201': (r) => r.status === 201 });
  perfSuccessRate.add(ok ? 1 : 0);
  if (!ok) {
    perfErrorsTotal.add(1);
    oioCreateErrors.add(1);
  }
}

// ---------------------------------------------------------------------------
// Weighted dispatch table
// Probabilities are cumulative thresholds [0, 1).
// ---------------------------------------------------------------------------

const DISPATCH = [
  { threshold: 0.40, fn: (h, d) => scenarioGetEio(h)         }, // 40 %
  { threshold: 0.65, fn: (h, d) => scenarioListEio(h)        }, // 25 %
  { threshold: 0.80, fn: (h, d) => scenarioCreateEio(h, d)   }, // 15 %
  { threshold: 0.90, fn: (h, d) => scenarioPatchEio(h)       }, // 10 %
  { threshold: 0.95, fn: (h, d) => scenarioListOio(h)        }, //  5 %
  { threshold: 1.00, fn: (h, d) => scenarioCreateOio(h)      }, //  5 %
];

// ---------------------------------------------------------------------------
// Default function (called once per VU iteration)
// ---------------------------------------------------------------------------

export default function (data) {
  const iotUrls = (data && data.iotUrls) || [];
  const headers = makeHeaders();

  // Lazily populate known IDs on first iteration
  ensureKnownIds(headers);

  const roll = Math.random();
  for (const { threshold, fn } of DISPATCH) {
    if (roll < threshold) {
      fn(headers, iotUrls);
      break;
    }
  }

  // Small think time to avoid hammering the server
  sleep(randomIntBetween(1, 3) * 0.1); // 0.1–0.3 s
}

// ---------------------------------------------------------------------------
// Setup: log configuration and pass IOT URLs to VUs
// ---------------------------------------------------------------------------

export function setup() {
  const iotUrls = _iotUrlsData.iotUrls || [];
  console.log('=== CG-DMF Performance Test ===');
  console.log(`  BASE_URL:        ${BASE_URL}`);
  console.log(`  JWT_CLIENT_ID:   ${JWT_CLIENT_ID}`);
  console.log(`  MAX_VUS:         ${MAX_VUS}`);
  console.log(`  RAMP_DURATION:   ${RAMP_DURATION}`);
  console.log(`  STEADY_DURATION: ${STEADY_DURATION}`);
  console.log(`  IOT URLs loaded: ${iotUrls.length}`);
  console.log('================================');
  return { iotUrls };
}

// ---------------------------------------------------------------------------
// Teardown: delete the IOTs that were created by seed.js
// ---------------------------------------------------------------------------

export function teardown(data) {
  const iotUrls = (data && data.iotUrls) || [];
  if (iotUrls.length === 0) {
    console.log('[perf] No IOT URLs to clean up.');
    return;
  }

  console.log(`[perf] Teardown — deleting ${iotUrls.length} informatieobjecttypen …`);

  let openzaakBearer = zgwBearer(OPENZAAK_CLIENT_ID, OPENZAAK_CLIENT_SECRET);
  let cleanupHeaders = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'Authorization': openzaakBearer,
  };

  for (let i = 0; i < iotUrls.length; i++) {
    const iotUrl = iotUrls[i];
    const iotUuid = iotUrl.split('/').pop();

    if (i > 0 && i % 5 === 0) {
      openzaakBearer = zgwBearer(OPENZAAK_CLIENT_ID, OPENZAAK_CLIENT_SECRET);
      cleanupHeaders['Authorization'] = openzaakBearer;
    }

    const res = http.del(
      `${CATALOGUS_BASE}/informatieobjecttypen/${iotUuid}`,
      null,
      { headers: cleanupHeaders, tags: { name: 'iot_delete' } }
    );

    if (res.status === 204) {
      console.log(`[perf]   Deleted IOT [${i + 1}/${iotUrls.length}]: ${iotUrl}`);
    } else {
      console.warn(
        `[perf]   IOT delete failed (${res.status}) for ${iotUrl}: ${(res.body || '').substring(0, 200)}`
      );
    }
  }

  console.log('[perf] Teardown complete — informatieobjecttypen cleaned up.');
}

