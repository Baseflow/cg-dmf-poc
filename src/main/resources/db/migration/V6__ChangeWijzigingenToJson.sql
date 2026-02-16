ALTER TABLE eio_versions
    ALTER COLUMN trefwoorden TYPE VARCHAR(100)[],
    ALTER COLUMN trefwoorden SET DEFAULT ARRAY[]::varchar[];

ALTER TABLE audit_trails
    ALTER COLUMN wijzigingen DROP DEFAULT;

ALTER TABLE audit_trails
    ALTER COLUMN wijzigingen TYPE JSON USING (NULLIF(wijzigingen, '')::json);

ALTER TABLE audit_trails
    ALTER COLUMN wijzigingen SET DEFAULT '{}'::json;