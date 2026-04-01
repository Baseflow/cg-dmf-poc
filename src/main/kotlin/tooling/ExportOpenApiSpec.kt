// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.tooling

import com.baseflow.api.DOCUMENTEN_API_VERSION
import com.baseflow.api.documentenApiModule
import com.baseflow.api.openApiModule
import com.baseflow.config.OpenZaakConfig
import com.baseflow.config.appModule
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import org.koin.ksp.generated.defaultModule
import org.koin.ktor.plugin.Koin
import java.io.File
import java.net.ServerSocket

/**
 * Exports the generated OpenAPI spec as YAML to docs/baseflow-documenten-<version>.yaml.
 *
 * Run via: ./gradlew exportOpenApiSpec
 */
fun main() {
    val outputFile = File("docs/baseflow-documenten-$DOCUMENTEN_API_VERSION.yaml")

    val port = findFreePort()
    val server = embeddedServer(Netty, port = port) {
        install(Koin) {
            modules(appModule)
            modules(defaultModule)
        }
        val openZaakConfig = OpenZaakConfig(validationEnabled = false)
        documentenApiModule(useAuthentication = false, openZaakConfig = openZaakConfig)
        openApiModule()
    }

    server.start(wait = false)

    try {
        val json = runBlocking {
            HttpClient(CIO).use { client ->
                client.get("http://localhost:$port/docs/openapi/documenten-api.json").bodyAsText()
            }
        }

        val yaml = convertJsonToYaml(json)
        outputFile.parentFile.mkdirs()
        outputFile.writeText(yaml)
        println("OpenAPI spec written to ${outputFile.path}")
    } finally {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 500)
    }
}

private fun findFreePort(): Int = ServerSocket(0).use { it.localPort }

private fun convertJsonToYaml(json: String): String {
    val jsonMapper = ObjectMapper()
    val tree = jsonMapper.readTree(json)

    val yamlFactory =
        YAMLFactory.builder()
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .build()
    val yamlMapper = ObjectMapper(yamlFactory)
    return yamlMapper.writeValueAsString(tree)
}
