CREATE TABLE IF NOT EXISTS oio_records (
    id uuid PRIMARY KEY,
    informatieobject uuid NOT NULL,
    informatieobject_versie uuid NOT NULL,
    subject_object VARCHAR(1000) NOT NULL,
    subject_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    CONSTRAINT uq_oio_informatieobject_object UNIQUE (informatieobject, subject_object),
    CONSTRAINT fk_oio_records_informatieobject__id FOREIGN KEY (informatieobject) REFERENCES eio_records(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_oio_records_informatieobject_versie__id FOREIGN KEY (informatieobject_versie) REFERENCES eio_versions(id) ON DELETE CASCADE ON UPDATE RESTRICT
);