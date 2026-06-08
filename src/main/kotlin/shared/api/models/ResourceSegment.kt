// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.api.models

enum class ResourceSegments(val value: String, val title: String = value) {
    ENKELVOUDIG_INFORMATIE_OBJECTEN("enkelvoudiginformatieobjecten", "EnkelvoudigInformatieObjecten"),
    OBJECT_INFORMATIE_OBJECTEN("objectinformatieobjecten", "ObjectInformatieObject"),
    SUBJECT_INFORMATIE_OBJECTEN("subjectinformatieobjecten", "SubjectInformatieObject"),
    UNKNOWN("unknown"),
    ;

    override fun toString(): String = value
}

/**
 * Annotation to define the resource segment (URL path) for an API entity response.
 * This is used by AuditTrailService and NotificationService to construct resource URLs.
 *
 * @property value The resource segment enum
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ResourceSegment(val value: ResourceSegments)

/**
 * Extension function to get the resource segment from an ApiEntityResponse.
 * Returns the value from the @ResourceSegment annotation if present, or a default fallback.
 */
fun ApiEntityResponse.getResourceSegment(): ResourceSegments {
    val annotation = this.javaClass.getAnnotation(ResourceSegment::class.java)
    return annotation?.value ?: ResourceSegments.UNKNOWN
}
