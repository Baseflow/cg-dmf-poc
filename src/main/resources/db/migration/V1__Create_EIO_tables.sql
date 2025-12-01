-- V1__Create_EIO_tables.sql
-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2025 Gemeente Utrecht

CREATE TABLE eio_records (
    id UUID PRIMARY KEY
);

CREATE TABLE eio_versions (
    id UUID PRIMARY KEY,
    record_id UUID REFERENCES eio_records(id)
);
