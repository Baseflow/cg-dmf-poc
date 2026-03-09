// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.entities

interface IAuditContext {
    var bronOrganisatie: String
    var vertrouwlijkheidsAanduiding: String
    var identificatie: String
    var informatieobject_type: String
}
