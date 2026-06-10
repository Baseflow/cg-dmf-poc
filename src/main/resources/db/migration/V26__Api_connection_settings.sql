-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V26: Rename zgw_api_settings to api_connection_settings and add api_type, auth_type,
--      validation_enabled, readonly, enabled columns.

ALTER TABLE zgw_api_settings RENAME TO api_connection_settings;

ALTER TABLE api_connection_settings
    RENAME CONSTRAINT pk_zgw_api_settings TO pk_api_connection_settings;

ALTER TABLE api_connection_settings
    RENAME CONSTRAINT uq_zgw_api_settings_name TO uq_api_connection_settings_name;

ALTER TABLE api_connection_settings
    ADD COLUMN api_type           VARCHAR(10) NOT NULL DEFAULT 'orc',
    ADD COLUMN auth_type          VARCHAR(20) NOT NULL DEFAULT 'zgw-auth',
    ADD COLUMN validation_enabled BOOLEAN     NOT NULL DEFAULT true,
    ADD COLUMN readonly           BOOLEAN     NOT NULL DEFAULT false,
    ADD COLUMN enabled            BOOLEAN     NOT NULL DEFAULT true;
