-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V18: Create OIDC provider settings table for storing multiple OIDC provider configurations

CREATE TABLE oidc_provider_settings
(
    id                       UUID          NOT NULL,
    name                     VARCHAR(100)  NOT NULL,
    issuer                   TEXT          NOT NULL,
    client_id                TEXT          NOT NULL,
    client_secret_encrypted  TEXT,                        -- NULL = no client secret configured
    created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_oidc_provider_settings PRIMARY KEY (id),
    CONSTRAINT uq_oidc_provider_settings_name UNIQUE (name)
);
