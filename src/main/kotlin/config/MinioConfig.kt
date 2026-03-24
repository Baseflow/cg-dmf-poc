// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.config

import org.slf4j.LoggerFactory
import software.amazon.awssdk.regions.Region
import java.time.Duration

/**
 * MinioConfigProvider reads MinIO configuration from environment variables
 * and provides it to services like StorageService.
 */
internal object MinioConfig : Config() {
    private val logger = LoggerFactory.getLogger(MinioConfig::class.java)

    val urlExpiry: Duration = Duration.parse(envOrSystem("MINIO_URL_EXPIRY", "PT15M"))
    val endpoint: String = envOrSystem("MINIO_ENDPOINT", "http://localhost:9000")
    val accessKey: String = envOrSystem("MINIO_ACCESS_KEY", "minioadmin")
    val secretKey: String = envOrThrow("MINIO_SECRET_KEY")
    val bucketName: String = envOrSystem("MINIO_BUCKET", "default-bucket")
    val region: Region = Region.of(envOrSystem("MINIO_REGION", "eu-west-1"))
    val disableChecksums: Boolean = envOrSystem("S3_DISABLE_CHECKSUMS", "false").toBoolean()
    val disableChunkedEncoding: Boolean = envOrSystem("S3_DISABLE_CHUNKED_ENCODING", "false").toBoolean()

    override fun printConfig() {
        logger.info(
            "MinioConfig: endpoint={}, accessKey={}, bucketName={}, urlExpiry={}, region={}, disableChecksums={}, disableChunkedEncoding={}",
            endpoint,
            accessKey,
            bucketName,
            urlExpiry,
            region,
            disableChecksums,
            disableChunkedEncoding,
        )
        logger.debug("MinioConfig: secretKey is set: {}", secretKey.isNotEmpty())
    }
}
