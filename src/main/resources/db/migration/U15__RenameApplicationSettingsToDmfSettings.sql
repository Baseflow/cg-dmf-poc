-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht

ALTER TABLE dmf_settings RENAME CONSTRAINT pk_dmf_settings TO pk_application_settings;
ALTER TABLE dmf_settings RENAME TO application_settings;
