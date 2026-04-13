CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE blob_storage_repositories
(
  id                       UUID         NOT NULL DEFAULT gen_random_uuid(),
  name                     VARCHAR(100) NOT NULL,
  storage_type             VARCHAR(50)  NOT NULL,
  url                      VARCHAR(500) NOT NULL,
  access_key_hash          VARCHAR(64)  NOT NULL,
  secret_key_hash          VARCHAR(64)  NOT NULL,
  bucket                   VARCHAR(255) NOT NULL,
  region                   VARCHAR(50),
  disable_checksums        BOOLEAN      NOT NULL DEFAULT FALSE,
  disable_chunked_encoding BOOLEAN      NOT NULL DEFAULT FALSE,
  extra_properties         TEXT         NOT NULL DEFAULT '{}',
  created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_blob_storage_repositories PRIMARY KEY (id),
  CONSTRAINT uq_blob_storage_repositories_name UNIQUE (name)
);

