package com.baseflow.api.models

import com.baseflow.api.ApiUrlBuilder
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.serialization.Serializable

@Serializable
data class PaginatedResponse<T>(
    val count: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T>
): ApiResponse {
    companion object {
        /**
         * Helper to create a paginated response with next/previous links.
         */
        fun <T> from(
            call: RoutingCall,
            resourceSegment: String,
            items: List<T>,
            totalCount: Long,
            page: Int,
            pageSize: Int
        ): PaginatedResponse<T> {
            val next = if (items.size == pageSize && totalCount > page * pageSize) {
                val params = call.request.queryParameters.toMap().mapValues { it.value.first() }.toMutableMap()
                params["page"] = (page + 1).toString()
                params["pageSize"] = pageSize.toString()
                ApiUrlBuilder.absolute(resourceSegment, queryParameters = params)
            } else null

            val previous = if (page > 1) {
                val params = call.request.queryParameters.toMap().mapValues { it.value.first() }.toMutableMap()
                params["page"] = (page - 1).toString()
                params["pageSize"] = pageSize.toString()
                ApiUrlBuilder.absolute(resourceSegment, queryParameters = params)
            } else null

            return PaginatedResponse(
                count = totalCount.toInt(),
                next = next,
                previous = previous,
                results = items
            )
        }
    }
}


