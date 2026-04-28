-- Revert encrypted columns back to hash columns (64-char SHA-256 hex).
-- NOTE: existing encrypted values cannot be automatically converted back to SHA-256 hashes.
-- Existing rows will have their credential columns truncated/emptied.
ALTER TABLE blob_storage_repositories
    RENAME COLUMN access_key_encrypted TO access_key_hash;

ALTER TABLE blob_storage_repositories
    RENAME COLUMN secret_key_encrypted TO secret_key_hash;

ALTER TABLE blob_storage_repositories
    ALTER COLUMN access_key_hash TYPE VARCHAR(64) USING '',
    ALTER COLUMN secret_key_hash TYPE VARCHAR(64) USING '';

