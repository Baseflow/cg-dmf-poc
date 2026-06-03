-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V24: Add inhoud_is_vervallen column to eio_versions.
-- Tracks whether the content of an INFORMATIEOBJECT is no longer valid (vervallen).
-- NULL means the value has not been explicitly set; false means not vervallen; true means vervallen.

ALTER TABLE eio_versions ADD COLUMN inhoud_is_vervallen BOOLEAN;
