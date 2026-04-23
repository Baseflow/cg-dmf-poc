// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
//
// Shared constants used across seed.js and perf.js.

/** Path where seed.js writes IOT URLs so perf.js can read them.
 *  Uses /tmp so the path is absolute and writable both in Docker and locally. */
export const IOT_URLS_FILE = '/tmp/iot-urls.json';

