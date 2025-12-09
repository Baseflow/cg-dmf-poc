package com.baseflow.api.models

class QueryEnkelvoudigeInformatieObjectenFilter(
    val bronOrganisatie: String? = null,
    val trefwoorden: List<String> = emptyList(),
    val identificatie: String? = null,
    val expand: List<String> = emptyList(),
    val page: Int = 0
)