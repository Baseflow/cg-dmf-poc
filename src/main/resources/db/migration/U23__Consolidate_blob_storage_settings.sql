-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- U23: Undo V23 — restore blob_storage_repositories, migrate data back, and remove the added columns.

CREATE TABLE blob_storage_repositories
(
    id                       UUID         NOT NULL,
    name                     VARCHAR(100) NOT NULL,
    storage_type             VARCHAR(50)  NOT NULL,
    url                      VARCHAR(500) NOT NULL,
    access_key_encrypted     VARCHAR(512) NOT NULL,
    secret_key_encrypted     VARCHAR(512) NOT NULL,
    bucket                   VARCHAR(255) NOT NULL,
    region                   VARCHAR(50),
    disable_checksums        BOOLEAN      NOT NULL DEFAULT FALSE,
    disable_chunked_encoding BOOLEAN      NOT NULL DEFAULT FALSE,
    extra_properties         TEXT         NOT NULL DEFAULT '{}',
    is_default               BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_blob_storage_repositories PRIMARY KEY (id),
    CONSTRAINT uq_blob_storage_repositories_name UNIQUE (name)
);

INSERT INTO blob_storage_repositories (
    id, name, storage_type, url,
    access_key_encrypted, secret_key_encrypted,
    bucket, region,
    disable_checksums, disable_chunked_encoding,
    extra_properties,
    is_default, created_at, updated_at
)
SELECT
    id, name, storage_type, url,
    COALESCE(access_key_encrypted, ''), COALESCE(secret_key_encrypted, ''),
    bucket, region,
    COALESCE((extra_properties::jsonb ->> 'DISABLE_CHECKSUMS')::boolean,  FALSE),
    COALESCE((extra_properties::jsonb ->> 'DISABLE_CHUNKED_ENCODING')::boolean, FALSE),
    extra_properties::jsonb
        - 'DISABLE_CHECKSUMS'
        - 'DISABLE_CHUNKED_ENCODING',
    is_default, created_at, updated_at
FROM blob_storage_repository_settings
ON CONFLICT (name) DO NOTHING;

ALTER TABLE blob_storage_repository_settings
    DROP COLUMN IF EXISTS region,
    DROP COLUMN IF EXISTS extra_properties,
    DROP COLUMN IF EXISTS created_at;
