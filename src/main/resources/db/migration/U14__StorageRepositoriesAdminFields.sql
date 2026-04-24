-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht

ALTER TABLE blob_storage_repositories
    DROP COLUMN IF EXISTS access_key_encrypted,
    DROP COLUMN IF EXISTS secret_key_encrypted,
    DROP COLUMN IF EXISTS storage_account_name,
    DROP COLUMN IF EXISTS enabled;
