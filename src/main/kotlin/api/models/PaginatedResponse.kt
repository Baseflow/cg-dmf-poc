package com.baseflow.api.models

import kotlinx.serialization.Serializable

@Serializable
internal class PaginatedResponse<T>(
    val count: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T>
)


