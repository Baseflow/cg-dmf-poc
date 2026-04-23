-- Create the trefwoorden lookup table
CREATE TABLE IF NOT EXISTS trefwoorden (
    id   uuid         PRIMARY KEY,
    woord VARCHAR(100) NOT NULL,
    CONSTRAINT uq_trefwoorden_woord UNIQUE (woord)
);

-- Drop any existing eio_version_trefwoorden rows so we can add a NOT NULL FK column
DELETE FROM eio_version_trefwoorden;

-- Add trefwoord_id FK column and constraint (NOT NULL is safe: table is empty)
ALTER TABLE eio_version_trefwoorden
    ADD COLUMN trefwoord_id uuid NOT NULL
        CONSTRAINT fk_eio_version_trefwoorden_trefwoord_id__id
            REFERENCES trefwoorden(id) ON DELETE CASCADE ON UPDATE RESTRICT;

-- Drop the old trefwoord text column
ALTER TABLE eio_version_trefwoorden
    DROP COLUMN trefwoord;

-- Enforce uniqueness and add indexes for efficient joins/filtering
ALTER TABLE eio_version_trefwoorden
    ADD CONSTRAINT eio_version_trefwoorden_version_id_trefwoord_id_unique
        UNIQUE (version_id, trefwoord_id);

CREATE INDEX eio_version_trefwoorden_version_id
    ON eio_version_trefwoorden (version_id);

CREATE INDEX eio_version_trefwoorden_trefwoord_id
    ON eio_version_trefwoorden (trefwoord_id);