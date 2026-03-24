// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.config.MinioConfig
import org.koin.core.annotation.Singleton
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.zip.ZipInputStream

/**
 * StorageService interacts with the MinIO storage backend using the configuration
 * provided by MinioConfigProvider.
 */
@Singleton
open class StorageService {

    private val logger = LoggerFactory.getLogger(StorageService::class.java)

    private val bucketName = MinioConfig.bucketName

    private val creds = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(MinioConfig.accessKey, MinioConfig.secretKey),
    )

    private val s3Config = S3Configuration.builder()
        .pathStyleAccessEnabled(true)
        .build()

    private val s3Client: S3AsyncClient = S3AsyncClient.builder()
        .region(MinioConfig.region)
        .endpointOverride(URI.create(MinioConfig.endpoint))
        .credentialsProvider(creds)
        .httpClientBuilder(NettyNioAsyncHttpClient.builder())
        .serviceConfiguration(s3Config)
        .build()

    init {
        logger.info("Created S3 client for bucket {}", bucketName)
    }

    fun uploadFile(objectName: String, content: ByteArray) {
        try {
            if (!s3Client.listBuckets().join().buckets()
                    .any { it.name() == bucketName }
            ) {
                logger.info("Bucket {} does not exist, creating it", bucketName)
                val createBucketResponse =
                    s3Client.createBucket { it.bucket(bucketName) }.join()
                logger.debug("Bucket created: {}", createBucketResponse)
            }

            logger.debug(
                "Uploading file {} to bucket {} (size: {} bytes)",
                objectName,
                bucketName,
                content.size,
            )
            val putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectName).build()
            val requestBody = AsyncRequestBody.fromBytes(content)
            val putObjectResponse =
                s3Client.putObject(putObjectRequest, requestBody).join()
            logger.info(
                "Successfully uploaded data to {}/{} (ETag: {})",
                bucketName,
                objectName,
                putObjectResponse.eTag(),
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to upload file {} to bucket {}: {}",
                objectName,
                bucketName,
                e.message,
                e,
            )
            throw e
        }
    }

    /**
     * Streams an object directly to the provided OutputStream without loading it fully into memory.
     */
    fun downloadFileTo(objectName: String, output: OutputStream): CompletableFuture<Void> {
        logger.debug(
            "Streaming download of {} from bucket {}",
            objectName,
            bucketName,
        )
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(objectName)
            .build()

        val result = CompletableFuture<Void>()

        s3Client
            .getObject(getObjectRequest, AsyncResponseTransformer.toPublisher())
            .whenComplete { responsePublisher, throwable ->
                if (throwable != null) {
                    result.completeExceptionally(throwable)
                    return@whenComplete
                }

                responsePublisher.subscribe(object : Subscriber<ByteBuffer> {
                    private lateinit var subscription: Subscription

                    override fun onSubscribe(s: Subscription) {
                        subscription = s
                        s.request(1)
                    }

                    override fun onNext(buffer: ByteBuffer) {
                        try {
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            output.write(bytes)
                            // Optionally flush to push data downstream promptly
                            output.flush()
                            subscription.request(1)
                        } catch (e: Exception) {
                            subscription.cancel()
                            result.completeExceptionally(e)
                        }
                    }

                    override fun onError(t: Throwable) {
                        result.completeExceptionally(t)
                    }

                    override fun onComplete() {
                        try {
                            output.flush()
                        } catch (_: Exception) {
                        }
                        result.complete(null)
                    }
                })
            }

        return result
    }

    companion object {
        /*
        Detecteert het formaat van een bestand op basis van de eerste bytes.
        Retourneert de MIME-type string als het formaat herkend wordt, anders null.
         */
        internal fun detectFileFormat(bytes: ByteArray): String? = when {
            bytes.isEmpty() -> null
            bytes.hasPrefix(0x25, 0x50, 0x44, 0x46) -> "application/pdf"
            // Legacy .office files
            bytes.hasPrefix(
                0xD0,
                0xCF,
                0x11,
                0xE0,
            ) -> "application/vnd.ms-office"
            // modern office files
            // word
            bytes.hasPrefix(0x50, 0x4B, 0x03, 0x04) && bytes.isDocxPackage() ->
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            // powerpoint
            bytes.hasPrefix(
                0x50,
                0x4B,
                0x03,
                0x04,
            ) &&
                bytes.isOpcPackageWithEntry("ppt/presentation.xml") ->
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            // excel
            bytes.hasPrefix(0x50, 0x4B, 0x03, 0x04) && bytes.isOpcPackageWithEntry("xl/workbook.xml") ->
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

            bytes.hasPrefix(0x50, 0x4B, 0x03, 0x04) -> "application/zip"
            bytes.hasPrefix(
                0x89,
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
            ) -> "image/png"

            bytes.hasPrefix(0xFF, 0xD8, 0xFF) -> "image/jpeg"
            bytes.hasPrefix(0x47, 0x49, 0x46, 0x38) -> "image/gif"
            bytes.hasPrefix(0x42, 0x4D) -> "image/bmp"
            bytes.hasPrefix(0x4F, 0x67, 0x67, 0x53) -> "application/ogg"
            bytes.hasPrefix(0x49, 0x44, 0x33) ||
                bytes.hasPrefix(
                    0xFF,
                    0xFB,
                ) -> "audio/mpeg"

            bytes.hasPrefix(0x66, 0x4C, 0x61, 0x43) -> "audio/flac"
            bytes.hasPrefix(0x1A, 0x45, 0xDF, 0xA3) -> "video/x-matroska"
            bytes.hasPrefix(0x1F, 0x8B, 0x08) -> "application/gzip"
            bytes.hasPrefix(0x42, 0x5A, 0x68) -> "application/x-bzip2"
            bytes.hasPrefix(
                0x37,
                0x7A,
                0xBC,
                0xAF,
                0x27,
                0x1C,
            ) -> "application/x-7z-compressed"

            bytes.hasPrefix(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00) ||
                bytes.hasPrefix(
                    0x52,
                    0x61,
                    0x72,
                    0x21,
                    0x1A,
                    0x07,
                    0x01,
                    0x00,
                ) -> "application/vnd.rar"

            bytes.hasPrefix(0x49, 0x49, 0x2A, 0x00) ||
                bytes.hasPrefix(
                    0x4D,
                    0x4D,
                    0x00,
                    0x2A,
                ) -> "image/tiff"

            bytes.hasIsoBmffBrand(
                "heic",
                "heif",
                "hevc",
                "mif1",
                "msf1",
            ) -> "image/heic"

            bytes.startsWithAscii("<svg") -> "image/svg+xml"
            bytes.hasIsoBmffBrand(
                "isom",
                "iso2",
                "mp41",
                "mp42",
                "avc1",
                "dash",
            ) -> "video/mp4"

            bytes.hasRiffType("AVI ") -> "video/x-msvideo"
            bytes.hasRiffType("WEBP") -> "image/webp"
            bytes.hasRiffType("WAVE") -> "audio/wav"

            bytes.hasTarMagic() -> "application/x-tar"

            bytes.hasPrefix(0x41, 0x43, 0x31, 0x30) -> "application/acad"
            bytes.startsWithAscii("0\nsection") -> "application/dxf"
            bytes.hasPrefix(
                0xD0,
                0xCF,
                0x11,
                0xE0,
            ) &&
                bytes.containsAscii("PowerPoint Document") ->
                "application/vnd.ms-powerpoint"

            else -> null
        }

        /*
        Controleert of de gegeven bytes een voorvoegsel hebben die overeenkomt met de opgegeven signatures.
        Retourneert true als alle signatures overeenkomen, anders false.
         */
        private fun ByteArray.hasPrefix(vararg signature: Int): Boolean {
            if (size < signature.size) {
                return false
            }
            signature.forEachIndexed { index, value ->
                if (this[index] != value.toByte()) return false
            }
            return true
        }

        /*
        Controleert of de gegeven bytes een RIFF-header hebben met het opgegeven type.
         */
        private fun ByteArray.hasRiffType(expected: String): Boolean {
            if (size < 12 || expected.length != 4) {
                return false
            }
            return hasPrefix(0x52, 0x49, 0x46, 0x46) &&
                copyOfRange(
                    8,
                    12,
                ).contentEquals(expected.toByteArray())
        }

        /*
        Controleert of de gegeven bytes een ISO/IEC 14496-12:2015 BMFF-header hebben met het opgegeven type.
         */
        private fun ByteArray.hasIsoBmffBrand(vararg brands: String): Boolean {
            if (size < 12) {
                return false
            }
            if (!copyOfRange(4, 8).contentEquals(
                    byteArrayOf(
                        0x66,
                        0x74,
                        0x79,
                        0x70,
                    ),
                )
            ) {
                return false
            }
            val brand = copyOfRange(8, 12).decodeToString().lowercase()
            return brands.any { brand == it.lowercase() }
        }

        /*
        Controleert of de gegeven bytes een tar-header hebben.
         */
        private fun ByteArray.hasTarMagic(): Boolean = size >= 262 &&
            copyOfRange(
                257,
                262,
            ).contentEquals("ustar".toByteArray())

        /*
        Controleert of de gegeven bytes een ASCII-tekst hebben met het opgegeven voorvoegsel.
         */
        private fun ByteArray.startsWithAscii(prefix: String): Boolean {
            if (isEmpty()) {
                return false
            }

            var offset =
                if (size >= 3 && this[0] == 0xEF.toByte() && this[1] == 0xBB.toByte() && this[2] == 0xBF.toByte()) {
                    3
                } else {
                    0
                }
            while (offset < size &&
                this[offset].toInt().toChar()
                    .isWhitespace()
            ) {
                offset++
            }
            if (size - offset < prefix.length) {
                return false
            }
            val head =
                copyOfRange(offset, offset + prefix.length).decodeToString()
                    .lowercase()
            return head == prefix.lowercase()
        }

        private fun ByteArray.isDocxPackage(): Boolean = try {
            ZipInputStream(ByteArrayInputStream(this)).use { zip ->
                var hasWordDoc = false
                var hasContentTypes = false
                var entry =
                    zip.nextEntry
                while (entry != null && !(hasWordDoc && hasContentTypes)) {
                    val name = entry.name.lowercase()
                    if (name == "[content_types].xml") hasContentTypes = true
                    if (name == "word/document.xml") hasWordDoc = true
                    entry = zip.nextEntry
                }
                hasWordDoc && hasContentTypes
            }
        } catch (_: Exception) {
            false
        }

        /*
        Controleert of de gegeven bytes een ASCII-tekst bevat met het opgegeven voorvoegsel.
        Zoeken wordt beperkt tot de eerste 1024 bytes voor efficiëntie.
         */
        private fun ByteArray.containsAscii(search: String, limit: Int = 1024): Boolean {
            val needle = search.toByteArray()
            if (needle.isEmpty() || this.isEmpty()) return false

            val max = minOf(this.size, limit)
            if (needle.size > max) return false

            // naive scan; fast enough for small limits
            for (i in 0..(max - needle.size)) {
                var j = 0
                while (j < needle.size && this[i + j] == needle[j]) j++
                if (j == needle.size) return true
            }
            return false
        }

        private fun ByteArray.isOpcPackageWithEntry(requiredEntry: String): Boolean = try {
            ZipInputStream(ByteArrayInputStream(this)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.equals(requiredEntry, ignoreCase = true)) {
                        return true
                    }
                    entry = zip.nextEntry
                }
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
