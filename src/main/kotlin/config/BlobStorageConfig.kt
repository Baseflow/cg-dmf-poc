// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.config

import org.slf4j.LoggerFactory

/**
 * Parsed configuration for a single blob storage repository,
 * read from environment variables with a numeric suffix (e.g. BLOB_STORAGE_URL1).
 */
data class BlobStorageRepoConfig(
    val index: Int,
    val name: String,
    val type: BlobStorageType,
    val url: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
    val region: String? = null,
    val disableChecksums: Boolean = false,
    val disableChunkedEncoding: Boolean = false,
    /** Any additional custom env values keyed by their suffix (e.g. "CONTAINER_NAME" → value). */
    val extraProperties: Map<String, String> = emptyMap(),
)

/**
 * Discovers blob storage repository configurations from environment variables.
 *
 * Expected env var pattern (1-based index):
 * ```
 * BLOB_STORAGE_TYPE1=S3
 * BLOB_STORAGE_URL1=http://localhost:9000
 * BLOB_STORAGE_ACCESS_KEY1=minioadmin
 * BLOB_STORAGE_SECRET_KEY1=minioadmin
 * BLOB_STORAGE_BUCKET1=documenten
 * BLOB_STORAGE_REGION1=eu-west-1          # optional, type-dependent
 * BLOB_STORAGE_DISABLE_CHECKSUMS1=false   # optional
 * BLOB_STORAGE_DISABLE_CHUNKED_ENCODING1=false # optional
 * BLOB_STORAGE_NAME1=my-repo             # optional human-readable name
 * ```
 *
 * Any env var matching `BLOB_STORAGE_*<index>` that is not one of the known
 * keys is collected into [BlobStorageRepoConfig.extraProperties].
 */
object BlobStorageConfig : Config() {
    private val logger = LoggerFactory.getLogger(BlobStorageConfig::class.java)

    private val KNOWN_SUFFIXES = setOf(
        "TYPE", "URL", "ACCESS_KEY", "SECRET_KEY", "BUCKET",
        "REGION", "DISABLE_CHECKSUMS", "DISABLE_CHUNKED_ENCODING", "NAME",
    )

    /**
     * All discovered repository configurations, lazily parsed once.
     */
    val repositories: List<BlobStorageRepoConfig> by lazy { discover() }

    private fun discover(): List<BlobStorageRepoConfig> {
        val repos = mutableListOf<BlobStorageRepoConfig>()
        var index = 1
        while (true) {
            val typeRaw = envOrNull("BLOB_STORAGE_TYPE$index") ?: break
            val type = BlobStorageType.fromLabel(typeRaw)
            val url = envOrThrow("BLOB_STORAGE_URL$index")
            val accessKey = envOrThrow("BLOB_STORAGE_ACCESS_KEY$index")
            val secretKey = envOrThrow("BLOB_STORAGE_SECRET_KEY$index")
            val bucket = envOrNull("BLOB_STORAGE_BUCKET$index") ?: "documenten"
            val region = envOrNull("BLOB_STORAGE_REGION$index")
            val disableChecksums = envOrNull("BLOB_STORAGE_DISABLE_CHECKSUMS$index")?.toBoolean() ?: false
            val disableChunkedEncoding = envOrNull("BLOB_STORAGE_DISABLE_CHUNKED_ENCODING$index")?.toBoolean() ?: false
            val name = envOrNull("BLOB_STORAGE_NAME$index") ?: "repo-$index"

            // Collect any extra env vars for this index
            val extra = collectExtraProperties(index)

            repos += BlobStorageRepoConfig(
                index = index,
                name = name,
                type = type,
                url = url,
                accessKey = accessKey,
                secretKey = secretKey,
                bucket = bucket,
                region = region,
                disableChecksums = disableChecksums,
                disableChunkedEncoding = disableChunkedEncoding,
                extraProperties = extra,
            )
            logger.info(
                "Discovered blob storage repository [{}]: type={}, url={}, bucket={}",
                name,
                type.label,
                url,
                bucket,
            )
            index++
        }
        if (repos.isEmpty()) {
            logger.info("No blob storage repositories configured via BLOB_STORAGE_* env vars.")
        }
        return repos
    }

    /**
     * Scans all environment variables (both `.env` file and process environment) for
     * keys matching `BLOB_STORAGE_*<index>` that are not one of the known suffixes,
     * and returns them as a map.
     */
    private fun collectExtraProperties(index: Int): Map<String, String> {
        val prefix = "BLOB_STORAGE_"
        val suffix = "$index"
        val extras = mutableMapOf<String, String>()

        envEntries().forEach { (key, value) ->
            if (key.startsWith(prefix) && key.endsWith(suffix)) {
                val middle = key.removePrefix(prefix).removeSuffix(suffix)
                if (middle !in KNOWN_SUFFIXES) {
                    extras[middle] = value
                }
            }
        }
        return extras
    }

    private fun envOrNull(key: String): String? = try {
        envOrSystem(key, "__UNSET__").takeIf { it != "__UNSET__" }
    } catch (_: Exception) {
        null
    }

    override fun printConfig() {
        repositories.forEachIndexed { i, repo ->
            logger.info(
                "BlobStorageConfig[{}]: name={}, type={}, url={}, bucket={}, region={}, disableChecksums={}, disableChunkedEncoding={}, extraKeys={}",
                i + 1, repo.name, repo.type.label, repo.url, repo.bucket, repo.region,
                repo.disableChecksums, repo.disableChunkedEncoding, repo.extraProperties.keys,
            )
        }
    }
}
