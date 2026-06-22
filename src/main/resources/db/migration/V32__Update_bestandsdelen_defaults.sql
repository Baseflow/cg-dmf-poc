-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V32: Update bestandsdelen chunk/trigger defaults from 4 GB / 3 GB to 300 MB / 100 MB.
-- Only updates rows that still carry the original seeded defaults; custom values are left untouched.

UPDATE dmf_settings SET value = '314572800'  WHERE key = 'trigger_size_bytes' AND value = '4294967296';
UPDATE dmf_settings SET value = '104857600'  WHERE key = 'chunk_size_bytes'   AND value = '3221225472';
