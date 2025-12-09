CREATE TABLE IF NOT EXISTS eio_records (
    id uuid PRIMARY KEY,
    lock_token VARCHAR(100) NULL
);

CREATE TABLE IF NOT EXISTS eio_versions (
    id uuid PRIMARY KEY,
    record_id uuid NOT NULL,
    versie INT NOT NULL,
    taal VARCHAR(3) NOT NULL,
    bestandsnaam VARCHAR(255) DEFAULT '' NOT NULL,
    formaat VARCHAR(255) DEFAULT '' NULL,
    bestandsomvang BIGINT NULL,
    link VARCHAR(200) DEFAULT '' NOT NULL,
    integriteit_algoritme VARCHAR(20) DEFAULT '' NOT NULL,
    integriteit_waarde VARCHAR(128) DEFAULT '' NOT NULL,
    integriteits_datum TIMESTAMP NULL,
    begin_registratie TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    verschijnings_vorm TEXT DEFAULT '' NOT NULL,
    trefwoorden VARCHAR(100)[] DEFAULT ARRAY[]::varchar[] NOT NULL,
    bron_organisatie VARCHAR(9) DEFAULT '' NOT NULL,
    creatie_datum DATE DEFAULT CURRENT_DATE NOT NULL,
    titel VARCHAR(200) NOT NULL,
    vertrouwlijkheids_aanduiding VARCHAR(20) DEFAULT '' NOT NULL,
    auteur VARCHAR(200) NOT NULL,
    status VARCHAR(20) DEFAULT '' NOT NULL,
    beschrijving TEXT DEFAULT '' NOT NULL,
    indicatie_gebruiksrecht BOOLEAN DEFAULT FALSE NOT NULL,
    ondertekening_soort VARCHAR(10) DEFAULT '' NOT NULL,
    ondertekenings_datum TIMESTAMP NULL,
    identificatie VARCHAR(40) DEFAULT '' NOT NULL, CONSTRAINT
    fk_eio_versions_record_id__id FOREIGN KEY (record_id) REFERENCES eio_records(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
