-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V12: Create OIDC settings table for storing Keycloak configuration

CREATE TABLE oidc_settings
(
    id                       UUID    NOT NULL,
    issuer                   TEXT    NOT NULL,
    client_id                TEXT    NOT NULL,
    client_secret_encrypted  TEXT,
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_oidc_settings PRIMARY KEY (id)
);
