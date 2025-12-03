-- V1__Create_EIO_tables.sql
-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2025 Gemeente Utrecht

CREATE TABLE IF NOT EXISTS eio_records (
    id uuid PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS eio_versions (
    id uuid PRIMARY KEY,
    record_id uuid NOT NULL,
    versie INT NOT NULL,
    taal VARCHAR(3),
    bestandsnaam VARCHAR(255),
    CONSTRAINT fk_eio_versions_record_id__id FOREIGN KEY (record_id)
        REFERENCES eio_records(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
);
