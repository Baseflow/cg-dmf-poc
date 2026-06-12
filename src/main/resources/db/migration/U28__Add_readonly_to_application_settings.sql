-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- U28: Undo readonly column from application_settings table.

ALTER TABLE application_settings
    DROP COLUMN readonly;

