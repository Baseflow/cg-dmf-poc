ALTER TABLE blob_storage_repositories
  ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;

