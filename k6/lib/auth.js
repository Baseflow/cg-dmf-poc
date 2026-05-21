// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
//
// ZGW-style HS256 JWT token helper for k6.
// k6 ships with the `k6/crypto` built-in — no external dependencies needed.

import { hmac } from 'k6/crypto';
import encoding from 'k6/encoding';

const JWT_CLIENT_ID = __ENV.JWT_CLIENT_ID || 'gzac';

/**
 * Build a minimal ZGW JWT (HS256) and return the full "Bearer <token>" header value.
 *
 * @param {string} clientId     - JWT `iss` / `client_id` claim (e.g. "gzac")
 * @param {string} clientSecret - Shared HMAC-SHA256 secret
 * @returns {string}            - "Bearer <jwt>"
 */
export function zgwBearer(clientId, clientSecret) {
  const header = base64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const raw = {
        iss: clientId,
        iat: Math.floor(Date.now() / 1000),
        client_id: clientId,
        user_id: "k6-perf@example.com",
        user_representation: "k6 Performance Test",
        roles: ["dmf-admin"],
    }
  if (clientId === __ENV.JWT_CLIENT_ID) {
    raw.aud = 'account'
    raw.roles = ['realm-admin']
  }
  const payload = base64url(JSON.stringify(raw));

  const signingInput = `${header}.${payload}`;
  const signature = base64url(
    // hmac returns a hex string by default — decode to binary before base64url-encoding
    hexToBytes(hmac('sha256', clientSecret, signingInput, 'hex'))
  );

  return `Bearer ${signingInput}.${signature}`;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function base64url(data) {
  // data may be a string or an ArrayBuffer / typed array
  const b64 =
    typeof data === 'string'
      ? encoding.b64encode(data)
      : encoding.b64encode(data, 'rawstd');
  return b64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 * Convert a lowercase hex string produced by k6/crypto to a Uint8Array
 * so we can base64url-encode it.
 */
function hexToBytes(hex) {
  const len = hex.length / 2;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  }
  return bytes.buffer;
}

