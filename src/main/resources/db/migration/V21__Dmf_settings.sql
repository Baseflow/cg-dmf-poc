-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V21: Create dmf_settings table (singleton row) for runtime-configurable DMF settings.
-- The single row is seeded with sensible defaults matching the previous environment-variable defaults.

CREATE TABLE dmf_settings
(
    id                  UUID      NOT NULL,
    trigger_size_bytes  BIGINT    NOT NULL,
    chunk_size_bytes    BIGINT    NOT NULL,
    validation_enabled  BOOLEAN   NOT NULL,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_dmf_settings PRIMARY KEY (id)
);
