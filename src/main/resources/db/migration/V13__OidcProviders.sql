-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V12: Create OIDC providers table for storing multiple OIDC provider configurations

CREATE TABLE oidc_providers
(
    id                       UUID          NOT NULL,
    name                     VARCHAR(100)  NOT NULL,
    issuer                   TEXT          NOT NULL,
    client_id                TEXT          NOT NULL,
    client_secret_encrypted  TEXT,                        -- NULL = no client secret configured
    updated_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_oidc_providers PRIMARY KEY (id),
    CONSTRAINT uq_oidc_providers_name UNIQUE (name)
);
