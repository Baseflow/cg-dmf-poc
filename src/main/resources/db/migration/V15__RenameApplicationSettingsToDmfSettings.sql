-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V15: Rename application_settings to dmf_settings.

ALTER TABLE application_settings RENAME TO dmf_settings;
ALTER TABLE dmf_settings RENAME CONSTRAINT pk_application_settings TO pk_dmf_settings;
