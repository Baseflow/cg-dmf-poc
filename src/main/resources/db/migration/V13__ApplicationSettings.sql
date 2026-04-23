-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V13: Create application_settings table (singleton row) for runtime-configurable settings.
-- The single row is seeded with sensible defaults matching the previous environment-variable defaults.

CREATE TABLE application_settings
(
    id                  UUID      NOT NULL,
    trigger_size_bytes  BIGINT    NOT NULL,
    chunk_size_bytes    BIGINT    NOT NULL,
    validation_enabled  BOOLEAN   NOT NULL,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_application_settings PRIMARY KEY (id)
);

INSERT INTO application_settings (id, trigger_size_bytes, chunk_size_bytes, validation_enabled)
VALUES ('00000000-0000-0000-0000-000000000001', 4294967296, 3221225472, true);
