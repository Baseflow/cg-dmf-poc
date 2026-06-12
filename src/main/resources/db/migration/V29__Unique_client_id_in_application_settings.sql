-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V29: Add unique constraint on client_id in application_settings.
--      client_id identifies an OAuth2 client credential and must be unique;
--      the missing constraint allowed concurrent startups to create duplicate rows.

ALTER TABLE application_settings
    ADD CONSTRAINT application_settings_client_id_unique UNIQUE (client_id);
