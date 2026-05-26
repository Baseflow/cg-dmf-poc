-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V23: Consolidate blob storage configuration into blob_storage_repository_settings.
--      Adds the fields needed by BlobStorageRegistrar, drops the now-redundant
--      blob_storage_repositories table, and stores disable flags in extra_properties JSON
--      rather than as dedicated columns.

ALTER TABLE blob_storage_repository_settings
    ADD COLUMN region           VARCHAR(50),
    ADD COLUMN extra_properties TEXT      NOT NULL DEFAULT '{}',
    ADD COLUMN created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

INSERT INTO blob_storage_repository_settings (
    id, name, storage_type, url,
    access_key_encrypted, secret_key_encrypted,
    bucket, region,
    extra_properties,
    is_default, enabled,
    created_at, updated_at
)
SELECT
    id, name, storage_type, url,
    access_key_encrypted, secret_key_encrypted,
    bucket, region,
    extra_properties::jsonb
        || jsonb_build_object(
               'DISABLE_CHECKSUMS',        disable_checksums::text,
               'DISABLE_CHUNKED_ENCODING', disable_chunked_encoding::text
           ),
    is_default, TRUE,
    created_at, updated_at
FROM blob_storage_repositories
ON CONFLICT (name) DO NOTHING;

DROP TABLE IF EXISTS blob_storage_repositories;
