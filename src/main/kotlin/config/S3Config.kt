// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.config

import org.slf4j.LoggerFactory
import software.amazon.awssdk.regions.Region
import java.time.Duration

/**
 * S3ConfigProvider reads S3 configuration from environment variables
 * and provides it to services like StorageService.
 */
internal object S3Config : Config() {
    private val logger = LoggerFactory.getLogger(S3Config::class.java)

    val urlExpiry: Duration = Duration.parse(envOrSystem("S3_URL_EXPIRY", "PT15M"))
    val endpoint: String = envOrSystem("S3_ENDPOINT", "http://localhost:9000")
    val accessKey: String = envOrSystem("S3_ACCESS_KEY", "minioadmin")
    val secretKey: String = envOrThrow("S3_SECRET_KEY")
    val bucketName: String = envOrSystem("S3_BUCKET", "default-bucket")
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
}
