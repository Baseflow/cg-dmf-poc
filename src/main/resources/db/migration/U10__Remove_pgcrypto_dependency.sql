-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- U10: Undo removal of pgcrypto dependency.
-- Restores gen_random_uuid() default on blob_storage_repositories.id.

ALTER TABLE blob_storage_repositories ALTER COLUMN id SET DEFAULT gen_random_uuid();

