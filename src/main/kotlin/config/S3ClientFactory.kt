// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.config

import org.koin.core.annotation.Singleton
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI
import java.time.Duration

/**
 * Centralised factory for the S3AsyncClient used by all services.
 *
 * Keeping client construction in a single place ensures that settings such as
 * credentials, endpoint, region, path-style access and HTTP timeouts are
 * always consistent across [com.baseflow.services.StorageService] and
 * [com.baseflow.services.HealthCheckService].
 */
@Singleton
class S3ClientFactory {

    companion object {
        /** Maximum time for connection establishment and individual read operations. */
        val S3_OPERATION_TIMEOUT: Duration = Duration.ofSeconds(5)
    }

    /**
     * Creates and returns a fully-configured [S3AsyncClient].
     *
     * The caller is responsible for closing the client when it is no longer needed.
     */
    fun create(): S3AsyncClient {
        val creds = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(MinioConfig.accessKey, MinioConfig.secretKey),
        )

        val s3Config = S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .build()

        val httpClientBuilder = NettyNioAsyncHttpClient.builder()
            .connectionTimeout(S3_OPERATION_TIMEOUT)
            .readTimeout(S3_OPERATION_TIMEOUT)

        return S3AsyncClient.builder()
            .region(MinioConfig.region)
            .endpointOverride(URI.create(MinioConfig.endpoint))
            .credentialsProvider(creds)
            .httpClientBuilder(httpClientBuilder)
            .serviceConfiguration(s3Config)
            .build()
    }
}

