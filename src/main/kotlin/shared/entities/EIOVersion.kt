// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.shared.entities

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import java.util.UUID
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object EIOVersions : UUIDTable("eio_versions") {
    val recordId =
        reference("record_id", EIORecords, onDelete = ReferenceOption.CASCADE)
    val versie = integer("versie")
    val taal = varchar("taal", 3)
    val bestandsnaam = varchar("bestandsnaam", 255).default("")
    val formaat = varchar("formaat", 255).nullable().default("")
    val bestandsomvang = long("bestandsomvang").nullable()
    val link = varchar("link", 200).default("")
    val integriteitAlgoritme = varchar("integriteit_algoritme", 20).default("")
    val integriteitWaarde = varchar("integriteit_waarde", 128).default("")

    val integriteitsDatum = datetime("integriteits_datum").nullable()
    val beginRegistratie = datetime("begin_registratie").defaultExpression(CurrentDateTime)
    val verschijningsVorm = text("verschijnings_vorm").default("")

    val bronOrganisatie = varchar("bron_organisatie", 9).default("")
    val creatieDatum = date("creatie_datum").defaultExpression(CurrentDate)
    val titel = varchar("titel", 200)
    val vertrouwlijkheidsAanduiding = varchar("vertrouwlijkheids_aanduiding", 20).default("")
    val auteur = varchar("auteur", 200)
    val status = varchar("status", 20).default("")
    val beschrijving = text("beschrijving").default("")
    val indicatieGebruiksrecht = bool("indicatie_gebruiksrecht").default(false)
    val ondertekening_soort = varchar("ondertekening_soort", 10).default("")
    val ondertekenings_datum = datetime("ondertekenings_datum").nullable()
    val identificatie = varchar("identificatie", 40).default("")
    val informatieobject_type = varchar("informatieobject_type", 200).default("")
    val bestandsLocatie = varchar("bestands_locatie", 1000).default("")
    val bestandsRepository = varchar("bestands_repository", 100).default("")
    val inhoudIsVervallen = bool("inhoud_is_vervallen").nullable()

    init {
        // Unique composite index: a record cannot have two rows with the same version number.
        // Also speeds up the correlated MAX(versie) subquery used when listing EIOs.
        index(true, recordId, versie)

        // Indexes for the most common filter columns
        index(false, bronOrganisatie)
        index(false, identificatie)
        index(false, informatieobject_type)
        index(false, beginRegistratie)
        index(false, creatieDatum)
    }
}

@OptIn(ExperimentalTime::class)
class EIOVersionEntity(id: EntityID<UUID>) :
    UUIDEntity(id),
    IAuditContext {
    companion object : UUIDEntityClass<EIOVersionEntity>(EIOVersions)

    var recordId by EIORecordEntity referencedOn EIOVersions.recordId
    var versie by EIOVersions.versie
    var taal by EIOVersions.taal
    var bestandsnaam by EIOVersions.bestandsnaam
    var formaat by EIOVersions.formaat
    var bestandsomvang by EIOVersions.bestandsomvang
    var link by EIOVersions.link
    var integriteitAlgoritme by EIOVersions.integriteitAlgoritme
    var integriteitWaarde by EIOVersions.integriteitWaarde
    var integriteitsDatum by EIOVersions.integriteitsDatum
    var beginRegistratie by EIOVersions.beginRegistratie
    var verschijningsVorm by EIOVersions.verschijningsVorm
    override var bronOrganisatie by EIOVersions.bronOrganisatie
    var creatieDatum by EIOVersions.creatieDatum
    var titel by EIOVersions.titel
    override var vertrouwlijkheidsAanduiding by EIOVersions.vertrouwlijkheidsAanduiding
    var auteur by EIOVersions.auteur
    var status by EIOVersions.status
    var beschrijving by EIOVersions.beschrijving
    var indicatieGebruiksrecht by EIOVersions.indicatieGebruiksrecht
    var ondertekening_soort by EIOVersions.ondertekening_soort
    var ondertekenings_datum by EIOVersions.ondertekenings_datum
    override var identificatie by EIOVersions.identificatie
    override var informatieobject_type by EIOVersions.informatieobject_type
    var bestandsLocatie by EIOVersions.bestandsLocatie
    var bestandsRepository by EIOVersions.bestandsRepository
    var inhoudIsVervallen by EIOVersions.inhoudIsVervallen
}

/** Returns the highest-versie version for this record using a single indexed DB lookup. */
fun EIORecordEntity.latestVersion(): EIOVersionEntity? = EIOVersionEntity
    .find { EIOVersions.recordId eq this.id }
    .orderBy(EIOVersions.versie to SortOrder.DESC)
    .limit(1)
    .firstOrNull()
