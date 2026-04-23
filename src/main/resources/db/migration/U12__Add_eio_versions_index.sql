-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
ALTER TABLE eio_versions DROP CONSTRAINT IF EXISTS eio_versions_record_id_versie_unique;
DROP INDEX IF EXISTS eio_versions_bron_organisatie;
DROP INDEX IF EXISTS eio_versions_creatie_datum;
DROP INDEX IF EXISTS eio_versions_informatieobject_type;
DROP INDEX IF EXISTS eio_versions_identificatie;
DROP INDEX IF EXISTS eio_versions_begin_registratie;

