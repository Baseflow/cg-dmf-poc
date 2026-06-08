-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
-- This file causes flyway to skip V1-V24 and apply this new baseline instead

CREATE TABLE application_settings (
    id                      UUID         NOT NULL,
    name                    VARCHAR(100) NOT NULL,
    client_id               TEXT         NOT NULL,
    client_secret_encrypted VARCHAR(512),
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_application_settings PRIMARY KEY (id),
    CONSTRAINT uq_application_settings_name UNIQUE (name)
);

CREATE TABLE audit_trails (
    id                  UUID          NOT NULL,
    bron                VARCHAR(50)   NOT NULL,
    applicatie_id       VARCHAR(100),
    applicatie_weergave VARCHAR(200),
    gebruikers_id       VARCHAR(255),
    gebruikers_weergave VARCHAR(255),
    actie               VARCHAR(50)   NOT NULL,
    actie_weergave      VARCHAR(200),
    resultaat           INTEGER,
    hoofd_object        VARCHAR(1000) NOT NULL,
    resource            VARCHAR(50)   NOT NULL,
    resource_url        VARCHAR(1000) NOT NULL,
    toelichting         TEXT,
    resource_weergave   VARCHAR(200),
    aanmaakdatum        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    wijzigingen         JSON          NOT NULL DEFAULT '{}',
    CONSTRAINT audit_trails_pkey PRIMARY KEY (id)
);

CREATE TABLE eio_records (
    id         UUID        NOT NULL,
    lock_token VARCHAR(100),
    CONSTRAINT eio_records_pkey PRIMARY KEY (id)
);

CREATE TABLE eio_versions (
    id                           UUID          NOT NULL,
    record_id                    UUID          NOT NULL,
    versie                       INTEGER       NOT NULL,
    taal                         VARCHAR(3)    NOT NULL,
    bestandsnaam                 VARCHAR(255)  NOT NULL DEFAULT '',
    formaat                      VARCHAR(255)  DEFAULT '',
    bestandsomvang               BIGINT,
    link                         VARCHAR(200)  NOT NULL DEFAULT '',
    integriteit_algoritme        VARCHAR(20)   NOT NULL DEFAULT '',
    integriteit_waarde           VARCHAR(128)  NOT NULL DEFAULT '',
    integriteits_datum           TIMESTAMP,
    begin_registratie            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verschijnings_vorm           TEXT          NOT NULL DEFAULT '',
    bron_organisatie             VARCHAR(9)    NOT NULL DEFAULT '',
    creatie_datum                DATE          NOT NULL DEFAULT CURRENT_DATE,
    titel                        VARCHAR(200)  NOT NULL,
    vertrouwlijkheids_aanduiding VARCHAR(20)   NOT NULL DEFAULT '',
    auteur                       VARCHAR(200)  NOT NULL,
    status                       VARCHAR(20)   NOT NULL DEFAULT '',
    beschrijving                 TEXT          NOT NULL DEFAULT '',
    indicatie_gebruiksrecht      BOOLEAN       NOT NULL DEFAULT FALSE,
    ondertekening_soort          VARCHAR(10)   NOT NULL DEFAULT '',
    ondertekenings_datum         TIMESTAMP,
    identificatie                VARCHAR(40)   NOT NULL DEFAULT '',
    informatieobject_type        VARCHAR(200)  NOT NULL DEFAULT '',
    bestands_locatie             VARCHAR(1000) NOT NULL DEFAULT '',
    bestands_repository          VARCHAR(100)  NOT NULL DEFAULT '',
    inhoud_is_vervallen          BOOLEAN,
    CONSTRAINT eio_versions_pkey PRIMARY KEY (id),
    CONSTRAINT eio_versions_record_id_versie_unique UNIQUE (record_id, versie),
    CONSTRAINT fk_eio_versions_record_id__id FOREIGN KEY (record_id) REFERENCES eio_records (id) ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE INDEX eio_versions_begin_registratie ON eio_versions (begin_registratie);
CREATE INDEX eio_versions_bron_organisatie ON eio_versions (bron_organisatie);
CREATE INDEX eio_versions_creatie_datum ON eio_versions (creatie_datum);
CREATE INDEX eio_versions_identificatie ON eio_versions (identificatie);
CREATE INDEX eio_versions_informatieobject_type ON eio_versions (informatieobject_type);

CREATE TABLE trefwoorden (
    id    UUID        NOT NULL,
    woord VARCHAR(100) NOT NULL,
    CONSTRAINT trefwoorden_pkey PRIMARY KEY (id),
    CONSTRAINT uq_trefwoorden_woord UNIQUE (woord)
);

CREATE TABLE eio_version_trefwoorden (
    id           UUID NOT NULL,
    version_id   UUID NOT NULL,
    trefwoord_id UUID NOT NULL,
    CONSTRAINT eio_version_trefwoorden_pkey PRIMARY KEY (id),
    CONSTRAINT eio_version_trefwoorden_version_id_trefwoord_id_unique UNIQUE (version_id, trefwoord_id),
    CONSTRAINT fk_eio_version_trefwoorden_version_id__id FOREIGN KEY (version_id) REFERENCES eio_versions (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_eio_version_trefwoorden_trefwoord_id__id FOREIGN KEY (trefwoord_id) REFERENCES trefwoorden (id) ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE INDEX eio_version_trefwoorden_trefwoord_id_version_id ON eio_version_trefwoorden (trefwoord_id, version_id);

CREATE TABLE bestandsdelen (
    id         UUID         NOT NULL,
    version_id UUID         NOT NULL,
    volgnummer INTEGER      NOT NULL,
    omvang     BIGINT       NOT NULL,
    voltooid   BOOLEAN      NOT NULL DEFAULT FALSE,
    lock       VARCHAR(100) NOT NULL DEFAULT '',
    CONSTRAINT pk_bestandsdelen PRIMARY KEY (id),
    CONSTRAINT uq_bestandsdelen_version_volgnummer UNIQUE (version_id, volgnummer),
    CONSTRAINT chk_bestandsdelen_volgnummer CHECK (volgnummer > 0),
    CONSTRAINT chk_bestandsdelen_omvang CHECK (omvang > 0),
    CONSTRAINT fk_bestandsdelen_version_id__id FOREIGN KEY (version_id) REFERENCES eio_versions (id) ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE INDEX idx_bestandsdelen_version_id ON bestandsdelen (version_id);

CREATE TABLE oio_records (
    id                      UUID          NOT NULL,
    informatieobject        UUID          NOT NULL,
    informatieobject_versie UUID          NOT NULL,
    subject_object          VARCHAR(1000) NOT NULL,
    subject_type            VARCHAR(20)   NOT NULL,
    created_at              TIMESTAMP,
    updated_at              TIMESTAMP,
    CONSTRAINT oio_records_pkey PRIMARY KEY (id),
    CONSTRAINT uq_oio_informatieobject_object UNIQUE (informatieobject, subject_object),
    CONSTRAINT fk_oio_records_informatieobject__id FOREIGN KEY (informatieobject) REFERENCES eio_records (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_oio_records_informatieobject_versie__id FOREIGN KEY (informatieobject_versie) REFERENCES eio_versions (id) ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE blob_storage_repository_settings (
    id                   UUID         NOT NULL,
    name                 VARCHAR(100) NOT NULL,
    storage_type         VARCHAR(50)  NOT NULL,
    url                  VARCHAR(500) NOT NULL,
    access_key_encrypted VARCHAR(512),
    secret_key_encrypted VARCHAR(512),
    bucket               VARCHAR(255) NOT NULL,
    is_default           BOOLEAN      NOT NULL DEFAULT FALSE,
    storage_account_name VARCHAR(255),
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    region               VARCHAR(50),
    extra_properties     TEXT         NOT NULL DEFAULT '{}',
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_blob_storage_repository_settings PRIMARY KEY (id),
    CONSTRAINT uq_blob_storage_repository_settings_name UNIQUE (name)
);

CREATE TABLE oidc_provider_settings (
    id                      UUID         NOT NULL,
    name                    VARCHAR(100) NOT NULL,
    issuer                  TEXT         NOT NULL,
    client_id               TEXT         NOT NULL,
    client_secret_encrypted VARCHAR(512),
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_oidc_provider_settings PRIMARY KEY (id),
    CONSTRAINT uq_oidc_provider_settings_name UNIQUE (name)
);

CREATE TABLE zgw_api_settings (
    id                      UUID         NOT NULL,
    name                    VARCHAR(100) NOT NULL,
    base_url                TEXT         NOT NULL,
    client_id               TEXT         NOT NULL,
    client_secret_encrypted VARCHAR(512),
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_zgw_api_settings PRIMARY KEY (id),
    CONSTRAINT uq_zgw_api_settings_name UNIQUE (name)
);

CREATE TABLE dmf_settings (
    id                 UUID      NOT NULL,
    trigger_size_bytes BIGINT    NOT NULL,
    chunk_size_bytes   BIGINT    NOT NULL,
    validation_enabled BOOLEAN   NOT NULL,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_dmf_settings PRIMARY KEY (id)
);