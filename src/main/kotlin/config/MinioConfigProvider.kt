// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.config

/**
 * MinioConfigProvider reads MinIO configuration from environment variables
 * and provides it to services like StorageService.
 */
object MinioConfigProvider {
    val endpoint: String = System.getenv("MINIO_ENDPOINT") ?: "http://localhost:9000"
    val accessKey: String = System.getenv("MINIO_ACCESS_KEY") ?: "minioadmin"
    val secretKey: String = System.getenv("MINIO_SECRET_KEY") ?: "minioadmin"
    val bucketName: String = System.getenv("MINIO_BUCKET") ?: "default-bucket"
}
