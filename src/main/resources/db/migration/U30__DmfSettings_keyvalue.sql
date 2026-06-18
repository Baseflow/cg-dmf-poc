-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- U30: Undo V30 — restore dmf_settings to the V21 UUID-keyed singleton table.

DROP TABLE dmf_settings;

CREATE TABLE dmf_settings
(
    id                 UUID      NOT NULL,
    trigger_size_bytes BIGINT    NOT NULL,
    chunk_size_bytes   BIGINT    NOT NULL,
    validation_enabled BOOLEAN   NOT NULL,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_dmf_settings PRIMARY KEY (id)
);

INSERT INTO dmf_settings (id, trigger_size_bytes, chunk_size_bytes, validation_enabled)
VALUES ('00000000-0000-0000-0000-000000000001', 4294967296, 3221225472, true);
