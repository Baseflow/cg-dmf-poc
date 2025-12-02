// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.config.MinioConfigProvider

/**
 * StorageService interacts with the MinIO storage backend using the configuration
 * provided by MinioConfigProvider.
 */
class StorageService {
    private val endpoint = MinioConfigProvider.endpoint
    private val accessKey = MinioConfigProvider.accessKey
    private val secretKey = MinioConfigProvider.secretKey
    private val bucketName = MinioConfigProvider.bucketName

    fun printConfig() {
        println("MinIO Config:")
        println("Endpoint: $endpoint")
        println("Access Key: $accessKey")
        println("Secret Key: $secretKey")
        println("Bucket Name: $bucketName")
    }
}
