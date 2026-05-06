-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V20: Add blob_storage_repository_settings table.

CREATE TABLE blob_storage_repository_settings
(
  id                       UUID         NOT NULL,
  name                     VARCHAR(100) NOT NULL,
  storage_type             VARCHAR(50)  NOT NULL,
  url                      VARCHAR(500) NOT NULL,
  access_key_encrypted     VARCHAR(64)  NOT NULL,
  secret_key_encrypted     VARCHAR(64)  NOT NULL,
  bucket                   VARCHAR(255) NOT NULL,
  is_default               BOOLEAN      NOT NULL DEFAULT FALSE,
  storage_account_name     VARCHAR(255) NOT NULL,
  enabled                  BOOLEAN      NOT NULL DEFAULT TRUE,
  updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  
  CONSTRAINT pk_blob_storage_repository_settings PRIMARY KEY (id),
  CONSTRAINT uq_blob_storage_repository_settings_name UNIQUE (name)
);
