-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- U24: Undo V24 – drop the inhoud_is_vervallen column from eio_versions.

ALTER TABLE eio_versions DROP COLUMN IF EXISTS inhoud_is_vervallen;
