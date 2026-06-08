-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- U26: Undo V26 — restore zgw_api_settings table and drop added columns.

ALTER TABLE api_connection_settings
    DROP COLUMN enabled,
    DROP COLUMN readonly,
    DROP COLUMN validation_enabled,
    DROP COLUMN auth_type,
    DROP COLUMN api_type;

ALTER TABLE api_connection_settings
    RENAME CONSTRAINT uq_api_connection_settings_name TO uq_zgw_api_settings_name;

ALTER TABLE api_connection_settings
    RENAME CONSTRAINT pk_api_connection_settings TO pk_zgw_api_settings;

ALTER TABLE api_connection_settings RENAME TO zgw_api_settings;
