-- U2__Strip_EIO_tables.sql
-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2025 Gemeente Utrecht

ALTER TABLE eio_versions DROP COLUMN formaat;
ALTER TABLE eio_versions DROP COLUMN bestandsomvang;
ALTER TABLE eio_versions DROP COLUMN link;
ALTER TABLE eio_versions DROP COLUMN integriteit_algoritme;
ALTER TABLE eio_versions DROP COLUMN integriteit_waarde;
ALTER TABLE eio_versions DROP COLUMN integriteits_datum;
ALTER TABLE eio_versions DROP COLUMN begin_registratie;
ALTER TABLE eio_versions DROP COLUMN verschijnings_vorm;
ALTER TABLE eio_versions DROP COLUMN trefwoorden;
ALTER TABLE eio_versions DROP COLUMN "locked";
ALTER TABLE eio_versions DROP COLUMN bron_organisatie;
ALTER TABLE eio_versions DROP COLUMN creatie_datum;
ALTER TABLE eio_versions DROP COLUMN titel;
ALTER TABLE eio_versions DROP COLUMN vertrouwlijkheids_aanduiding;
ALTER TABLE eio_versions DROP COLUMN auteur;
ALTER TABLE eio_versions DROP COLUMN status;
ALTER TABLE eio_versions DROP COLUMN beschrijving;
ALTER TABLE eio_versions DROP COLUMN indicatie_gebruiksrecht;
ALTER TABLE eio_versions DROP COLUMN ondertekening_soort;
ALTER TABLE eio_versions DROP COLUMN ondertekenings_datum;
ALTER TABLE eio_versions ALTER COLUMN taal DROP NOT NULL;
ALTER TABLE eio_versions ALTER COLUMN bestandsnaam DROP NOT NULL, ALTER COLUMN bestandsnaam
ALTER COLUMN bestandsnaam DROP DEFAULT;
