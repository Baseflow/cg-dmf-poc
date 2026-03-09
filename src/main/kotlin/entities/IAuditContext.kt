package com.baseflow.entities

interface IAuditContext {
    var bronOrganisatie: String
    var vertrouwlijkheidsAanduiding: String
    var identificatie: String
    var informatieobject_type: String
}