// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.config

import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * MinioConfigProvider reads MinIO configuration from environment variables
 * and provides it to services like StorageService.
 */
internal object MinioConfig : Config {
    private val logger = LoggerFactory.getLogger(MinioConfig::class.java)

    val urlExpiry: Duration =  Duration.parse(System.getenv("MINIO_URL_EXPIRY") ?: "PT15M")
    val endpoint: String = System.getenv("MINIO_ENDPOINT") ?: "http://localhost:9000"
    val accessKey: String = System.getenv("MINIO_ACCESS_KEY") ?: "minioadmin"
    val secretKey: String = System.getenv("MINIO_SECRET_KEY") ?: "minioadmin"
    val bucketName: String = System.getenv("MINIO_BUCKET") ?: "default-bucket"

    override fun printConfig() {
        logger.info("MinioConfig: endpoint={}, accessKey={}, bucketName={}, urlExpiry={}",
            endpoint, accessKey, bucketName, urlExpiry)
        logger.debug("MinioConfig: secretKey is set: {}", secretKey.isNotEmpty())
    }
}
