package com.baseflow.api.models.wopi

import kotlinx.serialization.Serializable

@Serializable
data class CheckFileInfoResponse (
    val BaseFileName: String,
    val Size: Long
)
