-- Rename hash columns to encrypted columns and extend their length to accommodate
-- AES-256-PBE-CBC ciphertext (Base64-encoded, up to 512 characters).
ALTER TABLE blob_storage_repositories
    RENAME COLUMN access_key_hash TO access_key_encrypted;

ALTER TABLE blob_storage_repositories
    RENAME COLUMN secret_key_hash TO secret_key_encrypted;

ALTER TABLE blob_storage_repositories
    ALTER COLUMN access_key_encrypted TYPE VARCHAR(512),
    ALTER COLUMN secret_key_encrypted TYPE VARCHAR(512);

