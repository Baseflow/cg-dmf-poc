// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
//
// Shared test-data pools.
// All values are pre-defined arrays; callers pick a random entry with pick().

import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import encoding from 'k6/encoding';

// ---------------------------------------------------------------------------
// Metadata pools (10 values each)
// ---------------------------------------------------------------------------

export const BRONORGANISATIES = [
  '623058911', '012345678', '987654321', '111222333',
  '444555666', '777888999', '123456789', '234567890',
  '345678901', '456789012',
];

export const TALEN = ['dut', 'eng', 'deu', 'fra', 'spa', 'ita', 'por', 'pol', 'nld', 'ara'];

export const AUTEURS = [
  'Alice de Vries', 'Bob Jansen', 'Clara Pietersen', 'David Bakker',
  'Eva Smit', 'Frank Visser', 'Grace van Dam', 'Henk Hoekstra',
  'Ingrid Meijer', 'Jan de Boer',
];

export const TITELS = [
  'Aanvraag vergunning', 'Besluit bezwaar', 'Overeenkomst dienstverlening',
  'Jaarverslag 2025', 'Notulen vergadering', 'Offerte opdracht',
  'Factuur levering', 'Rapport inspectie', 'Correspondentie burger',
  'Interne memo',
];

export const STATUSSEN = [
  'definitief', 'concept', 'vastgesteld', 'in_bewerking',
  'definitief', 'concept', 'vastgesteld', 'in_bewerking',  // doubled to increase weight
  'definitief', 'concept',
];

export const VERTROUWELIJKHEDEN = [
  'openbaar', 'beperkt_openbaar', 'intern', 'zaakvertrouwelijk',
  'vertrouwelijk', 'confidentieel', 'geheim', 'zeer_geheim',
  'openbaar', 'intern',
];

export const BESCHRIJVINGEN = [
  'Gegenereerd door k6 prestatietest', 'Testdocument voor loadtest',
  'Automatisch aangemaakt', 'Performance seed document',
  'Bulk import item', 'Data seeding record',
  'Load test artefact', 'Testbestand bulk run',
  'k6 generated document', 'Synthetic test data',
];

export const TREFWOORDEN_POOL = [
  'vergunning', 'bezwaar', 'overeenkomst', 'rapport', 'factuur',
  'notulen', 'offerte', 'correspondentie', 'besluit', 'memo',
];


export const OBJECT_TYPES = ['zaak', 'besluit', 'overige'];

// ---------------------------------------------------------------------------
// Synthetic file records (10 sizes).
// Files are generated lazily on first use to avoid blowing up k6 init time.
// Each entry: { name, formaat, base64, size }
// ---------------------------------------------------------------------------

const FILE_DEFS = [
  { name: 'tiny.txt',    formaat: 'text/plain',       sizeKb: 1    },
  { name: 'small.txt',   formaat: 'text/plain',       sizeKb: 10   },
  { name: 'medium.txt',  formaat: 'text/plain',       sizeKb: 50   },
  { name: 'doc-100.bin', formaat: 'application/octet-stream', sizeKb: 100  },
  { name: 'doc-250.bin', formaat: 'application/octet-stream', sizeKb: 250  },
  { name: 'doc-500.bin', formaat: 'application/octet-stream', sizeKb: 500  },
  { name: 'img-100.png', formaat: 'image/png',        sizeKb: 100  },
  { name: 'img-500.png', formaat: 'image/png',        sizeKb: 500  },
  { name: 'sheet.bin',   formaat: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', sizeKb: 200 },
  { name: 'big.bin',     formaat: 'application/octet-stream', sizeKb: 1024 },
];

/**
 * Generate a base64-encoded string of `sizeKb` kilobytes of printable ASCII.
 * Uses k6/encoding so it works both at init time and in VU context.
 */
function makeBase64(sizeKb) {
  const bytes = sizeKb * 1024;
  // Build a Uint8Array of printable ASCII (0x20–0x7E) pseudo-randomly
  const buf = new Uint8Array(bytes);
  for (let i = 0; i < bytes; i++) {
    buf[i] = 0x20 + (Math.floor(Math.random() * 95)); // 0x20 to 0x7E
  }
  return encoding.b64encode(buf.buffer);
}

// Pre-compute a FILES array; capped at 100 KB per file for init-time sanity.
// The 500 KB / 1 MB files are generated lazily via getFile() below.
const _filesCache = {};

/**
 * Get a file record by index (0–9). Large files are generated on first access.
 * @param {number} idx
 * @returns {{ name: string, formaat: string, base64: string, size: number }}
 */
export function getFile(idx) {
  if (!_filesCache[idx]) {
    const def = FILE_DEFS[idx];
    _filesCache[idx] = {
      name: def.name,
      formaat: def.formaat,
      base64: makeBase64(def.sizeKb),
      size: def.sizeKb * 1024,
    };
  }
  return _filesCache[idx];
}

/**
 * Pick a random file record (lazily generated on first access).
 * @returns {{ name: string, formaat: string, base64: string, size: number }}
 */
export function pickFile() {
  return getFile(randomIntBetween(0, FILE_DEFS.length - 1));
}

// ---------------------------------------------------------------------------
// Utility
// ---------------------------------------------------------------------------

/** Pick a random element from an array. */
export function pick(arr) {
  return arr[randomIntBetween(0, arr.length - 1)];
}

/** Pick between 0 and maxCount random trefwoorden (unique). */
export function pickTrefwoorden(maxCount = 3) {
  const count = randomIntBetween(0, maxCount);
  const shuffled = [...TREFWOORDEN_POOL].sort(() => Math.random() - 0.5);
  return shuffled.slice(0, count);
}

/** ISO date string (YYYY-MM-DD) within the last 3 years. */
export function randomDate() {
  const now = Date.now();
  const threeYearsMs = 3 * 365 * 24 * 60 * 60 * 1000;
  const ts = now - Math.floor(Math.random() * threeYearsMs);
  return new Date(ts).toISOString().split('T')[0];
}

/**
 * Geometrically distributed integer in [1, max].
 * Most values will be low (≈1–5), a few will be high.
 * p=0.95 means P(n) = (1-p)^(n-1) * p → heavy tail trimmed to max.
 */
export function geometricInt(max, p = 0.95) {
  for (let n = 1; n < max; n++) {
    if (Math.random() < p) return n;
  }
  return max;
}

