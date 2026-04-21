// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
//
// Thin wrappers around the Documenten API endpoints and the OpenZaak Catalogi API.
// Every function returns the k6 Response object so callers can assert on it.

import http from 'k6/http';

const API_PATH = '/documenten/api/v1';

// ---------------------------------------------------------------------------
// EnkelvoudigInformatieObject
// ---------------------------------------------------------------------------

/**
 * POST /enkelvoudiginformatieobjecten
 * @param {string} baseUrl
 * @param {Object} headers  - must include Authorization
 * @param {Object} body     - EIO request payload
 * @returns {Response}
 */
export function createEio(baseUrl, headers, body) {
  return http.post(
    `${baseUrl}${API_PATH}/enkelvoudiginformatieobjecten`,
    JSON.stringify(body),
    { headers, tags: { name: 'eio_create' } }
  );
}

/**
 * GET /enkelvoudiginformatieobjecten
 * @param {string} baseUrl
 * @param {Object} headers
 * @param {Object} [params] - query params object
 * @returns {Response}
 */
export function listEio(baseUrl, headers, params = {}) {
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&');
  const url = qs
    ? `${baseUrl}${API_PATH}/enkelvoudiginformatieobjecten?${qs}`
    : `${baseUrl}${API_PATH}/enkelvoudiginformatieobjecten`;
  return http.get(url, { headers, tags: { name: 'eio_list' } });
}

/**
 * GET /enkelvoudiginformatieobjecten/:id
 * @param {string} baseUrl
 * @param {Object} headers
 * @param {string} id  - UUID
 * @returns {Response}
 */
export function getEio(baseUrl, headers, id) {
  return http.get(
    `${baseUrl}${API_PATH}/enkelvoudiginformatieobjecten/${id}`,
    { headers, tags: { name: 'eio_get' } }
  );
}

/**
 * PUT /enkelvoudiginformatieobjecten/:id  (creates new version)
 * @param {string} baseUrl
 * @param {Object} headers
 * @param {string} id
 * @param {Object} body
 * @returns {Response}
 */
export function updateEio(baseUrl, headers, id, body) {
  return http.put(
    `${baseUrl}${API_PATH}/enkelvoudiginformatieobjecten/${id}`,
    JSON.stringify(body),
    { headers, tags: { name: 'eio_update' } }
  );
}

/**
 * PATCH /enkelvoudiginformatieobjecten/:id  (partial update → new version)
 * @param {string} baseUrl
 * @param {Object} headers
 * @param {string} id
 * @param {Object} body
 * @returns {Response}
 */
export function patchEio(baseUrl, headers, id, body) {
  return http.patch(
    `${baseUrl}${API_PATH}/enkelvoudiginformatieobjecten/${id}`,
    JSON.stringify(body),
    { headers, tags: { name: 'eio_patch' } }
  );
}

/**
 * DELETE /enkelvoudiginformatieobjecten/:id
 * @param {string} baseUrl
 * @param {Object} headers
 * @param {string} id
 * @returns {Response}
 */
export function deleteEio(baseUrl, headers, id) {
  return http.del(
    `${baseUrl}${API_PATH}/enkelvoudiginformatieobjecten/${id}`,
    null,
    { headers, tags: { name: 'eio_delete' } }
  );
}

// ---------------------------------------------------------------------------
// ObjectInformatieObject
// ---------------------------------------------------------------------------

/**
 * POST /objectinformatieobjecten
 * @param {string} baseUrl
 * @param {Object} headers
 * @param {string} eioId       - UUID of the EIO
 * @param {string} objectUrl   - full URL of the related object
 * @param {string} objectType  - 'zaak' | 'besluit' | 'overige'
 * @returns {Response}
 */
export function createOio(baseUrl, headers, eioId, objectUrl, objectType) {
  const body = {
    informatieobject: `${baseUrl}${API_PATH}/enkelvoudiginformatieobjecten/${eioId}`,
    object: objectUrl,
    objectType: objectType,
  };
  return http.post(
    `${baseUrl}${API_PATH}/objectinformatieobjecten`,
    JSON.stringify(body),
    { headers, tags: { name: 'oio_create' } }
  );
}

/**
 * GET /objectinformatieobjecten
 * @param {string} baseUrl
 * @param {Object} headers
 * @param {Object} [params]
 * @returns {Response}
 */
export function listOio(baseUrl, headers, params = {}) {
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&');
  const url = qs
    ? `${baseUrl}${API_PATH}/objectinformatieobjecten?${qs}`
    : `${baseUrl}${API_PATH}/objectinformatieobjecten`;
  return http.get(url, { headers, tags: { name: 'oio_list' } });
}

// ---------------------------------------------------------------------------
// OpenZaak Catalogi API — InformatieObjectType
// ---------------------------------------------------------------------------

/**
 * POST /catalogi/api/v1/informatieobjecttypen
 * Creates a new informatieobjecttype (as concept).
 *
 * @param {string} catalogusBaseUrl  - e.g. "https://openzaak.dev.baseflow.com/catalogi/api/v1"
 * @param {Object} headers           - must include Authorization (OpenZaak JWT)
 * @param {string} catalogusUrl      - full URL of the parent catalogus resource
 * @param {string} omschrijving      - human-readable description / name
 * @param {string} [vertrouwelijkheidaanduiding] - default "openbaar"
 * @returns {Response}
 */
export function createInformatieobjecttype(catalogusBaseUrl, headers, catalogusUrl, omschrijving, vertrouwelijkheidaanduiding = 'openbaar') {
  const body = {
    catalogus: catalogusUrl,
    omschrijving,
    vertrouwelijkheidaanduiding,
    beginGeldigheid: '2024-01-01',
    informatieobjectcategorie: 'k6-perf-test',
  };
  return http.post(
    `${catalogusBaseUrl}/informatieobjecttypen`,
    JSON.stringify(body),
    { headers, tags: { name: 'iot_create' } }
  );
}

/**
 * POST /catalogi/api/v1/informatieobjecttypen/:uuid/publish
 * Publishes a concept informatieobjecttype so it can be used on EIOs.
 *
 * @param {string} catalogusBaseUrl
 * @param {Object} headers
 * @param {string} uuid  - UUID of the informatieobjecttype to publish
 * @returns {Response}
 */
export function publishInformatieobjecttype(catalogusBaseUrl, headers, uuid) {
  return http.post(
    `${catalogusBaseUrl}/informatieobjecttypen/${uuid}/publish`,
    null,
    { headers, tags: { name: 'iot_publish' } }
  );
}

/**
 * GET /catalogi/api/v1/catalogussen
 * Lists catalogussen so the seeder can resolve the catalogus URL.
 *
 * @param {string} catalogusBaseUrl
 * @param {Object} headers
 * @returns {Response}
 */
export function listCatalogussen(catalogusBaseUrl, headers) {
  return http.get(
    `${catalogusBaseUrl}/catalogussen`,
    { headers, tags: { name: 'catalogus_list' } }
  );
}

