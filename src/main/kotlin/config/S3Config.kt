// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.config

import org.slf4j.LoggerFactory
import software.amazon.awssdk.regions.Region
import java.time.Duration

/**
 * S3Config reads S3 configuration from environment variables
 * and provides it to services like StorageService.
 */
internal object S3Config : Config() {
    private val logger = LoggerFactory.getLogger(S3Config::class.java)

    val urlExpiry: Duration = Duration.parse(envOrSystem("S3_URL_EXPIRY", "PT15M"))
    val endpoint: String = envOrSystemWithLegacy("S3_ENDPOINT", "MINIO_ENDPOINT", "http://localhost:9000")
    val accessKey: String = envOrSystemWithLegacy("S3_ACCESS_KEY", "MINIO_ACCESS_KEY", "minioadmin")
    val secretKey: String = envOrThrowWithLegacy("S3_SECRET_KEY", "MINIO_SECRET_KEY")
    val bucketName: String = envOrSystemWithLegacy("S3_BUCKET", "MINIO_BUCKET", "default-bucket")
    val region: Region = Region.of(envOrSystem("S3_REGION", "eu-west-1"))
    val disableChecksums: Boolean = envOrSystem("S3_DISABLE_CHECKSUMS", "false").toBoolean()
    val disableChunkedEncoding: Boolean = envOrSystem("S3_DISABLE_CHUNKED_ENCODING", "false").toBoolean()

    override fun printConfig() {
        logger.info(
            "S3Config: endpoint={}, accessKey={}, bucketName={}, urlExpiry={}, region={}, disableChecksums={}, disableChunkedEncoding={}",
            endpoint,
            accessKey,
            bucketName,
            urlExpiry,
            region,
            disableChecksums,
            disableChunkedEncoding,
        )
        logger.debug("S3Config: secretKey is set: {}", secretKey.isNotEmpty())
    }

    fun isComplete(): Boolean = endpoint.isNotEmpty() && accessKey.isNotEmpty() && secretKey.isNotEmpty() && bucketName.isNotEmpty()

    fun toLegacyRepoConfig(): BlobStorageRepoConfig? {
        if (!isComplete()) return null
        return BlobStorageRepoConfig(
            index = 0,
            name = "legacy-s3",
            type = BlobStorageType.S3,
            url = endpoint,
            accessKey = accessKey,
            secretKey = secretKey,
            bucket = bucketName,
            region = region.id(),
            disableChecksums = disableChecksums,
            disableChunkedEncoding = disableChunkedEncoding,
        )
    }
}
