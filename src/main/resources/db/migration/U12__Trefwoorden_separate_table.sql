ALTER TABLE eio_versions
    ADD COLUMN trefwoorden VARCHAR(100)[] DEFAULT ARRAY[]::varchar[] NOT NULL;

UPDATE eio_versions v
SET trefwoorden = (
    SELECT ARRAY_AGG(t.trefwoord ORDER BY t.trefwoord)
    FROM eio_version_trefwoorden t
    WHERE t.version_id = v.id
)
WHERE EXISTS (
    SELECT 1 FROM eio_version_trefwoorden t WHERE t.version_id = v.id
);

DROP TABLE IF EXISTS eio_version_trefwoorden;

