package com.baseflow.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal object OpenZaakConfig : Config {
    val endpoint: String = System.getenv("OPENZAAK_ENDPOINT") ?: "https://openzaak.dev.baseflow.com"
    val clientId: String = System.getenv("OPENZAAK_CLIENT_ID") ?: "cg-dmf"
    val clientSecret:  String = System.getenv("OPENZAAK_CLIENT_SECRET") ?: "baseflow"

    override fun printConfig() {
        println("OpenZaakConfig:")
        println("  endpoint: $endpoint")
        println("  clientId: $clientId")
        println("  clientSecret: $clientSecret")
    }

    @OptIn(ExperimentalTime::class)
    fun generateJwtToken(): String {
        val now = Clock.System.now().epochSeconds
        return JWT.create()
            .withIssuer(clientId) // iss
            .withClaim("client_id", clientId)
            .withClaim("iat", now) // seconds
            .sign(Algorithm.HMAC256(clientSecret))
    }
}