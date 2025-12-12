// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.config.MinioConfig
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.net.URI

/**
 * StorageService interacts with the MinIO storage backend using the configuration
 * provided by MinioConfigProvider.
 */
class StorageService {

    private val bucketName = MinioConfig.bucketName

    private val creds = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(MinioConfig.accessKey, MinioConfig.secretKey)
    )

    private val s3Config = S3Configuration.builder()
        .pathStyleAccessEnabled(true)
        .build()

    private val s3Client: S3AsyncClient = S3AsyncClient.builder()
        .region(Region.EU_WEST_1)
        .endpointOverride(URI.create(MinioConfig.endpoint))
        .credentialsProvider(creds)
        .httpClientBuilder(NettyNioAsyncHttpClient.builder())
        .serviceConfiguration(s3Config)
        .build()

    private val presigner: S3Presigner = S3Presigner.builder()
        .region(Region.EU_WEST_1)
        .endpointOverride(URI.create(MinioConfig.endpoint))
        .credentialsProvider(creds)
        .serviceConfiguration(s3Config)
        .build()

    init {
        println("Created S3 client for bucket $bucketName")
    }

    fun uploadFile(objectName: String, content: ByteArray) {
        if (!s3Client.listBuckets().join().buckets().any { it.name() == bucketName }) {
            println("Bucket $bucketName does not exist, creating it")
            val createBucketResponse = s3Client.createBucket { it.bucket(bucketName) }.join()
            println("Bucket created: $createBucketResponse")
        }

        println("Uploading file $objectName to bucket $bucketName as $objectName")
        try {

            val putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectName).build()
            val requestBody = AsyncRequestBody.fromBytes(content)
            val putObjectResponse = s3Client.putObject(putObjectRequest, requestBody).join()
            println("Successfully uploaded data to $bucketName/$objectName")
            println("ETag: ${putObjectResponse.eTag()}")
        }
        catch (e: Exception) {
            println("Error uploading file: $e")
        }
    }

    fun downloadFile(objectName: String): ByteArray {
        println("Downloading file $objectName from bucket $bucketName")
        // return byte array of file content
        val getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(objectName).build()
        val response = s3Client
            .getObject(getObjectRequest, AsyncResponseTransformer.toBytes())
            .join()

        return response.asByteArray()
    }

    fun getDownloadUrl(objectName: String): String {
        // get token url
        val getReq = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(objectName)
            .build()

        val presignReq = GetObjectPresignRequest.builder()
            .signatureDuration(MinioConfig.urlExpiry)
            .getObjectRequest(getReq)
            .build()

        return presigner.presignGetObject(presignReq).url().toString()
    }
}
