import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.internal.builtins.StandardNames.FqNames.target

plugins {
    kotlin("jvm") version "2.3.20"
    application
    kotlin("plugin.serialization") version "2.3.20"
    id("com.github.ben-manes.versions") version "0.53.0"
    // KSP plugin for annotation processing (required by koin-annotations)
    id("com.google.devtools.ksp") version "2.3.6"
    // Code formatting with Spotless and ktlint
    id("com.diffplug.spotless") version "8.4.0"
}

group = "com.baseflow"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

ksp {
    arg("KOIN_DEFAULT_MODULE", "true")
}

dependencies {
    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation("com.h2database:h2:2.4.240")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("io.ktor:ktor-client-mock:3.4.1")
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.4.1")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.4.1")

    // Ktor server and client
    implementation("io.ktor:ktor-server-core-jvm:3.4.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.4.1")
    implementation("io.ktor:ktor-client-core:3.4.1")
    implementation("io.ktor:ktor-client-cio:3.4.1")
    implementation("io.ktor:ktor-server-content-negotiation:3.4.1")
    implementation("io.ktor:ktor-server-conditional-headers:3.4.1")
    implementation("io.ktor:ktor-server-status-pages:3.4.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.1")
    implementation("io.ktor:ktor-server-auth:3.4.1")
    implementation("io.ktor:ktor-server-auth-jwt:3.4.1")

    // Database - Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:1.1.1")
    implementation("org.jetbrains.exposed:exposed-dao:1.1.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.1.1")
    implementation("org.jetbrains.exposed:exposed-migration-core:1.1.1")
    implementation("org.jetbrains.exposed:exposed-migration-jdbc:1.1.1")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:1.1.1")
    implementation("org.jetbrains.exposed:exposed-json:1.1.1")
    implementation("org.postgresql:postgresql:42.7.10")

    // Database migrations
    implementation("org.flywaydb:flyway-core:12.1.1")
    implementation("org.flywaydb:flyway-database-postgresql:12.1.1")

    // Kotlin coroutines and datetime
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")

    // Authentication
    implementation("com.auth0:jwks-rsa:0.23.0")

    // AWS S3 storage
    implementation("software.amazon.awssdk:s3:2.42.17")
    implementation("software.amazon.awssdk:netty-nio-client:2.42.17")

    // Utilities
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")

    // Koin for dependency injection - use koin-ktor3 for Ktor 3.x compatibility
    implementation("io.insert-koin:koin-core:4.2.0")
    implementation("io.insert-koin:koin-ktor:4.2.0")
    implementation("io.insert-koin:koin-logger-slf4j:4.2.0")
    implementation("io.insert-koin:koin-annotations:4.2.0")
    ksp("io.insert-koin:koin-ksp-compiler:4.2.0")

    // Security. override to secure versions to fix CVEs in transitive dependencies
    constraints {
        // dependency of flyway-core and ktor-server-auth-jwt
        implementation("tools.jackson.core:jackson-core:3.1.0") {
            because("Fixes CVE GHSA-72hv-8253-57qq - Number Length Constraint Bypass in Async Parser")
        }
        // dependency of ktor-server-auth-jwt
        implementation("com.fasterxml.jackson.core:jackson-core:2.21.1") {
            because("Minimum version from transitive dependencies")
        }
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.6.0")
            .editorConfigOverride(
                mapOf(
                    "ktlint_standard_no-wildcard-imports" to "disabled",
                    "ktlint_standard_package-name" to "disabled",
                    "ktlint_standard_property-naming" to "disabled",
                    "ktlint_standard_filename" to "disabled",
                    "max_line_length" to "140",
                ),
            )
        licenseHeader(
            """
            // SPDX-License-Identifier: EUPL-1.2
            // Copyright (C) ${'$'}YEAR Gemeente Utrecht
            """.trimIndent(),
        )
    }
    kotlinGradle {
        target("*.gradle.kts", "gradle/**/*.gradle.kts")
        ktlint("1.6.0")
    }
}

application {
    mainClass.set("com.baseflow.MainKt")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.baseflow.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

// Flyway migration tasks
tasks.register<JavaExec>("flywayMigrate") {
    group = "flyway"
    description = "Migrates the database"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.baseflow.tooling.FlywayMigrationKt")
    args("migrate")
}

tasks.register<JavaExec>("flywayInfo") {
    group = "flyway"
    description = "Prints the details and status information about all migrations"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.baseflow.tooling.FlywayMigrationKt")
    args("info")
}

tasks.register<JavaExec>("flywayUndo") {
    group = "flyway"
    description = "Undoes the most recently applied versioned migration"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.baseflow.tooling.FlywayMigrationKt")
    args("undo")
}

tasks.register<JavaExec>("flywayClean") {
    group = "flyway"
    description = "Drops all objects in the configured schemas"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.baseflow.tooling.FlywayMigrationKt")
    args("clean")
}

tasks.register<JavaExec>("flywayValidate") {
    group = "flyway"
    description = "Validates the applied migrations against the available ones"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.baseflow.tooling.FlywayMigrationKt")
    args("validate")
}

tasks.register<JavaExec>("generateMigration") {
    group = "flyway"
    description = "Generate a Flyway migration script from Exposed table definitions"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.baseflow.tooling.MigrationGeneratorKt")
    // Pass command-line args through
    if (project.hasProperty("args")) {
        args((project.property("args") as String).split("\\s+".toRegex()))
    }
}

tasks.register<JavaExec>("addStubData") {
    group = "database"
    description = "Load stub/seed data into the database for development and testing"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.baseflow.tooling.StubDataLoaderKt")
}

// Prefer stable versions
fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.contains(it, true) }
    val nonStableRegex = Regex("(alpha|beta|rc|preview|snapshot)", RegexOption.IGNORE_CASE)
    val isStable = stableKeyword || !nonStableRegex.containsMatchIn(version)
    return !isStable
}

tasks.withType<DependencyUpdatesTask>().named("dependencyUpdates").configure {
    rejectVersionIf {
        isNonStable(candidate.version) && !isNonStable(currentVersion)
    }
    checkConstraints = true
    outputFormatter = "plain,json,html"
    outputDir = "build/reports"
    reportfileName = "dependency-updates-report"
}
