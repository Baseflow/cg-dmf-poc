-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V22: Create zgw_api_settings table for storing ZGW API connection profiles.

CREATE TABLE zgw_api_settings
(
    id                       UUID          NOT NULL,
    name                     VARCHAR(100)  NOT NULL,
    base_url                 TEXT          NOT NULL,
    client_id                TEXT          NOT NULL,
    client_secret_encrypted  TEXT,
    created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_zgw_api_settings PRIMARY KEY (id),
    CONSTRAINT uq_zgw_api_settings_name UNIQUE (name)
);
