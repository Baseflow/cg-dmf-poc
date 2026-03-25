// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.config

import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * S3onfigProvider reads S3 configuration from environment variables
 * and provides it to services like StorageService.
 */
internal object S3Config : Config() {
    private val logger = LoggerFactory.getLogger(S3Config::class.java)

    val urlExpiry: Duration =  Duration.parse(envOrSystem("S3_URL_EXPIRY", "PT15M"))
    val endpoint: String = envOrSystem("S3_ENDPOINT", "http://localhost:9000")
    val accessKey: String = envOrSystem("S3_ACCESS_KEY", "minioadmin")
    val secretKey: String = envOrSystem("S3_SECRET_KEY", "minioadmin")
    val bucketName: String = envOrSystem("S3_BUCKET", "default-bucket")

    override fun printConfig() {
        logger.info("S3 Config: endpoint={}, accessKey={}, bucketName={}, urlExpiry={}",
            endpoint, accessKey, bucketName, urlExpiry)
        logger.debug("S3 Config: secretKey is set: {}", secretKey.isNotEmpty())
    }
}
