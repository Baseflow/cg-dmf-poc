CREATE TABLE IF NOT EXISTS eio_version_trefwoorden (
    id         uuid         PRIMARY KEY,
    version_id uuid         NOT NULL,
    trefwoord  VARCHAR(100) NOT NULL,
    CONSTRAINT fk_eio_version_trefwoorden_version_id__id
        FOREIGN KEY (version_id) REFERENCES eio_versions(id) ON DELETE CASCADE ON UPDATE RESTRICT
);

INSERT INTO eio_version_trefwoorden (id, version_id, trefwoord)
SELECT gen_random_uuid(), v.id, t.trefwoord
FROM eio_versions v
         CROSS JOIN UNNEST(v.trefwoorden) AS t(trefwoord)
WHERE array_length(v.trefwoorden, 1) > 0;

ALTER TABLE eio_versions
    DROP COLUMN trefwoorden;

