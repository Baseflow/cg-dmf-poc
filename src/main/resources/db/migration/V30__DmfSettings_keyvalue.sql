-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V30: Replace dmf_settings singleton row with a generic typed key/value table.
--      No data migration: there is no production data to preserve.

DROP TABLE dmf_settings;

CREATE TABLE dmf_settings
(
    key        VARCHAR(100) NOT NULL,
    type       VARCHAR(20)  NOT NULL DEFAULT 'string',
    value      TEXT         NOT NULL,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_dmf_settings PRIMARY KEY (key)
);

INSERT INTO dmf_settings (key, type, value) VALUES ('trigger_size_bytes', 'int', '4294967296');
INSERT INTO dmf_settings (key, type, value) VALUES ('chunk_size_bytes', 'int', '3221225472');
INSERT INTO dmf_settings (key, type, value) VALUES ('validation_enabled', 'boolean', 'true');
