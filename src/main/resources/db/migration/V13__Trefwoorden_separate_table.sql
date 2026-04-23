CREATE TABLE IF NOT EXISTS eio_version_trefwoorden (
    id         uuid         PRIMARY KEY,
    version_id uuid         NOT NULL,
    trefwoord  VARCHAR(100) NOT NULL,
    CONSTRAINT fk_eio_version_trefwoorden_version_id__id
        FOREIGN KEY (version_id) REFERENCES eio_versions(id) ON DELETE CASCADE ON UPDATE RESTRICT
);

ALTER TABLE eio_versions
    DROP COLUMN trefwoorden;