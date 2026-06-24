-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- U33: Undo readonly column from blob_storage_repository_settings table.

ALTER TABLE blob_storage_repository_settings
    DROP COLUMN readonly;