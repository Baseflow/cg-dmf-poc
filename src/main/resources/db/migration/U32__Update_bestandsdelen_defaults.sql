-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- U32: Revert bestandsdelen chunk/trigger defaults from 300 MB / 100 MB back to 4 GB / 3 GB.
-- Only updates rows that still carry the V32 defaults; custom values are left untouched.

UPDATE dmf_settings SET value = '4294967296'  WHERE key = 'trigger_size_bytes' AND value = '314572800';
UPDATE dmf_settings SET value = '3221225472'  WHERE key = 'chunk_size_bytes'   AND value = '104857600';
