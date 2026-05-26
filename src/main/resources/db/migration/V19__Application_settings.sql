-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V19: Create application_settings table for storing application credentials

CREATE TABLE application_settings
(
    id                       UUID          NOT NULL,
    name                     VARCHAR(100)  NOT NULL,
    client_id                TEXT          NOT NULL,
    client_secret_encrypted  VARCHAR(512),                 -- NULL = no client secret configured
    created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_application_settings PRIMARY KEY (id),
    CONSTRAINT uq_application_settings_name UNIQUE (name)
);
