-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V11: Drift correction — align database schema with Exposed entity definitions.
--
-- Fixes:
--   1. eio_versions.informatieobject_type: make NOT NULL (was nullable from V3 ALTER TABLE ADD COLUMN)
--   2. eio_versions.bestands_locatie: make NOT NULL (was nullable from V4 ALTER TABLE ADD COLUMN)
--   3. bestandsdelen FK constraint: rename to match Exposed naming convention and add ON UPDATE RESTRICT

-- 1. informatieobject_type: backfill any NULLs, then set NOT NULL
UPDATE eio_versions SET informatieobject_type = '' WHERE informatieobject_type IS NULL;
ALTER TABLE eio_versions ALTER COLUMN informatieobject_type SET NOT NULL;

-- 2. bestands_locatie: backfill any NULLs, then set NOT NULL
UPDATE eio_versions SET bestands_locatie = '' WHERE bestands_locatie IS NULL;
ALTER TABLE eio_versions ALTER COLUMN bestands_locatie SET NOT NULL;

-- 3. Rename FK constraint on bestandsdelen to match Exposed convention
ALTER TABLE bestandsdelen DROP CONSTRAINT IF EXISTS bestandsdelen_version_id_fkey;
ALTER TABLE bestandsdelen ADD CONSTRAINT fk_bestandsdelen_version_id__id
    FOREIGN KEY (version_id) REFERENCES eio_versions(id) ON DELETE CASCADE ON UPDATE RESTRICT;

