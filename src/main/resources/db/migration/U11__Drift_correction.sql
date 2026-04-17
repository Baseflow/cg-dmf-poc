-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- U11: Undo drift correction.

-- 1. Revert informatieobject_type to nullable
ALTER TABLE eio_versions ALTER COLUMN informatieobject_type DROP NOT NULL;

-- 2. Revert bestands_locatie to nullable
ALTER TABLE eio_versions ALTER COLUMN bestands_locatie DROP NOT NULL;

-- 3. Revert FK constraint name
ALTER TABLE bestandsdelen DROP CONSTRAINT IF EXISTS fk_bestandsdelen_version_id__id;
ALTER TABLE bestandsdelen ADD CONSTRAINT bestandsdelen_version_id_fkey
    FOREIGN KEY (version_id) REFERENCES eio_versions(id) ON DELETE CASCADE;

