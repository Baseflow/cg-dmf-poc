ALTER TABLE eio_versions ALTER COLUMN bestandsnaam TYPE VARCHAR(255), ALTER COLUMN bestandsnaam SET NOT NULL, ALTER COLUMN bestandsnaam SET DEFAULT '';
ALTER TABLE eio_versions ALTER COLUMN trefwoorden TYPE VARCHAR(100)[], ALTER COLUMN trefwoorden SET DEFAULT ARRAY[]::varchar[];
ALTER TABLE eio_versions DROP COLUMN "locked";
