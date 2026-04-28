-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht

DROP INDEX IF EXISTS eio_version_trefwoorden_trefwoord_id_version_id;

CREATE INDEX IF NOT EXISTS eio_version_trefwoorden_version_id
    ON eio_version_trefwoorden (version_id);
CREATE INDEX IF NOT EXISTS eio_version_trefwoorden_trefwoord_id
    ON eio_version_trefwoorden (trefwoord_id);