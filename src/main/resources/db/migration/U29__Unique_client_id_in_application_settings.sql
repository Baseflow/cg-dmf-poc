-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- U29: Drop unique constraint on client_id in application_settings.

ALTER TABLE application_settings
    DROP CONSTRAINT application_settings_client_id_unique;
