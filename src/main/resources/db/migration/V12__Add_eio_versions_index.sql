-- Add composite index on eio_versions(record_id, versie) to speed up the correlated subquery
-- used in getAll() to filter only the latest version per record.
CREATE INDEX IF NOT EXISTS idx_eio_versions_record_versie ON eio_versions (record_id, versie DESC);

