ALTER TABLE eio_versions ADD identificatie VARCHAR(40) DEFAULT '' NOT NULL;
ALTER TABLE eio_versions ALTER COLUMN bestandsnaam TYPE VARCHAR(255), ALTER COLUMN bestandsnaam SET NOT NULL, ALTER COLUMN bestandsnaam SET DEFAULT '';
ALTER TABLE eio_versions ALTER COLUMN trefwoorden TYPE VARCHAR(100)[], ALTER COLUMN trefwoorden SET DEFAULT ARRAY[]::varchar[];
