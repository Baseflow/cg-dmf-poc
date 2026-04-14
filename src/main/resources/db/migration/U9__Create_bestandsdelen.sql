-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- U7: Undo V7 – drop the bestandsdelen table.

DROP INDEX IF EXISTS idx_bestandsdelen_version_id;
DROP TABLE IF EXISTS bestandsdelen;

