-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V7: Create bestandsdelen table for chunked file upload support.
-- A row is created per expected chunk whenever an EnkelvoudigInformatieObject is
-- registered with a bestandsomvang that exceeds the configured trigger size (default 300 MB).

CREATE TABLE bestandsdelen
(
  id         UUID         NOT NULL,
  version_id UUID         NOT NULL REFERENCES eio_versions (id) ON DELETE CASCADE,
  volgnummer INTEGER      NOT NULL,
  omvang     BIGINT       NOT NULL,
  voltooid   BOOLEAN      NOT NULL DEFAULT FALSE,
  lock       VARCHAR(100) NOT NULL DEFAULT '',
  CONSTRAINT pk_bestandsdelen PRIMARY KEY (id),
  CONSTRAINT uq_bestandsdelen_version_volgnummer UNIQUE (version_id, volgnummer),
  CONSTRAINT chk_bestandsdelen_volgnummer CHECK (volgnummer > 0),
  CONSTRAINT chk_bestandsdelen_omvang CHECK (omvang > 0)
);

CREATE INDEX idx_bestandsdelen_version_id ON bestandsdelen (version_id);

