-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht

-- Covering index for trefwoorden filter queries (filter by trefwoord_id, return version_id).
CREATE INDEX IF NOT EXISTS eio_version_trefwoorden_trefwoord_id_version_id
    ON eio_version_trefwoorden (trefwoord_id, version_id);

-- Drop the now-redundant single-column indexes:
-- (version_id) is covered by the unique (version_id, trefwoord_id) index.
-- (trefwoord_id) is covered by the new (trefwoord_id, version_id) index above.
DROP INDEX IF EXISTS eio_version_trefwoorden_version_id;
DROP INDEX IF EXISTS eio_version_trefwoorden_trefwoord_id;
