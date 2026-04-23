-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--

-- Remove duplicate (record_id, versie) rows, keeping the physically first row (ctid) per pair.
DELETE FROM eio_versions
WHERE ctid NOT IN (
    SELECT MIN(ctid)
    FROM eio_versions
    GROUP BY record_id, versie
);

ALTER TABLE eio_versions ADD CONSTRAINT eio_versions_record_id_versie_unique UNIQUE (record_id, versie);
CREATE INDEX IF NOT EXISTS eio_versions_bron_organisatie ON eio_versions (bron_organisatie);
CREATE INDEX IF NOT EXISTS eio_versions_creatie_datum ON eio_versions (creatie_datum);
CREATE INDEX IF NOT EXISTS eio_versions_informatieobject_type ON eio_versions (informatieobject_type);
CREATE INDEX IF NOT EXISTS eio_versions_identificatie ON eio_versions (identificatie);
CREATE INDEX IF NOT EXISTS eio_versions_begin_registratie ON eio_versions (begin_registratie);
