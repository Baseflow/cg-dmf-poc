// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow

import org.jetbrains.exposed.v1.core.ArrayColumnType
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
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
    val beginRegistratie = datetime("begin_registratie").defaultExpression(
        CurrentDateTime )
    val verschijningsVorm = text("verschijnings_vorm").default("")
    val trefwoorden = registerColumn<List<String>>(
        "trefwoorden", ArrayColumnType(VarCharColumnType(100))
    ).default(emptyList())

    val bronOrganisatie = varchar("bron_organisatie", 9).default("")
    val creatieDatum = date("creatie_datum").defaultExpression(CurrentDate )
    val titel = varchar("titel", 200)
    val vertrouwlijkheidsAanduiding = varchar("vertrouwlijkheids_aanduiding", 20).default("")
    val auteur = varchar("auteur", 200)
    val status = varchar("status", 20).default("")
    val beschrijving = text("beschrijving").default("")
    val indicatieGebruiksrecht = bool("indicatie_gebruiksrecht").default(false)
    val ondertekening_soort = varchar("ondertekening_soort", 10).default("")
    val ondertekenings_datum = datetime("ondertekenings_datum").nullable()
    val identificatie = varchar("identificatie", 40).default("")
}

@OptIn(ExperimentalTime::class)
class EIOVersionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
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
    var trefwoorden by EIOVersions.trefwoorden
    var bronOrganisatie by EIOVersions.bronOrganisatie
    var creatieDatum by EIOVersions.creatieDatum
    var titel by EIOVersions.titel
    var vertrouwlijkheidsAanduiding by EIOVersions.vertrouwlijkheidsAanduiding
    var auteur by EIOVersions.auteur
    var status by EIOVersions.status
    var beschrijving by EIOVersions.beschrijving
    var indicatieGebruiksrecht by EIOVersions.indicatieGebruiksrecht
    var ondertekening_soort by EIOVersions.ondertekening_soort
    var ondertekenings_datum by EIOVersions.ondertekenings_datum
    var identificatie by EIOVersions.identificatie
}
