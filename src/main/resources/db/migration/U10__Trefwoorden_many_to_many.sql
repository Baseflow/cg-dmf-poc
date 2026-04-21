-- Restore the trefwoord text column on the join table
ALTER TABLE eio_version_trefwoorden
    ADD COLUMN trefwoord VARCHAR(100);

-- Populate it from the trefwoorden lookup table
UPDATE eio_version_trefwoorden jt
SET trefwoord = t.woord
FROM trefwoorden t
WHERE jt.trefwoord_id = t.id;

-- Make the column NOT NULL
ALTER TABLE eio_version_trefwoorden
    ALTER COLUMN trefwoord SET NOT NULL;

-- Drop the FK constraint and trefwoord_id column
ALTER TABLE eio_version_trefwoorden
    DROP CONSTRAINT fk_eio_version_trefwoorden_trefwoord_id__id;

ALTER TABLE eio_version_trefwoorden
    DROP COLUMN trefwoord_id;

-- Drop the trefwoorden table
DROP TABLE IF EXISTS trefwoorden;

