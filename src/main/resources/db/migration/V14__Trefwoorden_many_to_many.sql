-- Create the trefwoorden table with unique lowercase values
CREATE TABLE IF NOT EXISTS trefwoorden (
    id   uuid         PRIMARY KEY,
    woord VARCHAR(100) NOT NULL,
    CONSTRAINT uq_trefwoorden_woord UNIQUE (woord)
);

-- Populate trefwoorden from existing eio_version_trefwoorden rows (deduplicated, lowercase)
INSERT INTO trefwoorden (id, woord)
SELECT gen_random_uuid(), LOWER(trefwoord)
FROM eio_version_trefwoorden
GROUP BY LOWER(trefwoord);

-- Add trefwoord_id FK column to the join table
ALTER TABLE eio_version_trefwoorden
    ADD COLUMN trefwoord_id uuid;

-- Link each existing row to the matching trefwoorden record
UPDATE eio_version_trefwoorden jt
SET trefwoord_id = t.id
FROM trefwoorden t
WHERE LOWER(jt.trefwoord) = t.woord;

-- Make the column NOT NULL and add the FK constraint
ALTER TABLE eio_version_trefwoorden
    ALTER COLUMN trefwoord_id SET NOT NULL;

ALTER TABLE eio_version_trefwoorden
    ADD CONSTRAINT fk_eio_version_trefwoorden_trefwoord_id__id
        FOREIGN KEY (trefwoord_id) REFERENCES trefwoorden(id) ON DELETE CASCADE ON UPDATE RESTRICT;

-- Drop the old trefwoord text column
ALTER TABLE eio_version_trefwoorden
    DROP COLUMN trefwoord;

-- Remove duplicate (version_id, trefwoord_id) pairs that may result from lowercasing
DELETE FROM eio_version_trefwoorden a
    USING eio_version_trefwoorden b
WHERE a.ctid < b.ctid
  AND a.version_id = b.version_id
  AND a.trefwoord_id = b.trefwoord_id;

-- Enforce uniqueness and add indexes for efficient joins/filtering
ALTER TABLE eio_version_trefwoorden
    ADD CONSTRAINT eio_version_trefwoorden_version_id_trefwoord_id_unique
        UNIQUE (version_id, trefwoord_id);

CREATE INDEX eio_version_trefwoorden_version_id
    ON eio_version_trefwoorden (version_id);

CREATE INDEX eio_version_trefwoorden_trefwoord_id
    ON eio_version_trefwoorden (trefwoord_id);

