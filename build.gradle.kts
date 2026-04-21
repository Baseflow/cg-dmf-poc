import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

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
    testImplementation("io.ktor:ktor-client-mock:3.4.2")
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.4.2")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.4.2")

    // Ktor server and client
    implementation("io.ktor:ktor-server-core-jvm:3.4.2")
    implementation("io.ktor:ktor-server-netty-jvm:3.4.2")
    implementation("io.ktor:ktor-client-core:3.4.2")
    implementation("io.ktor:ktor-client-cio:3.4.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.4.2")
    implementation("io.ktor:ktor-server-conditional-headers:3.4.2")
    implementation("io.ktor:ktor-server-status-pages:3.4.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.2")
    implementation("io.ktor:ktor-server-auth:3.4.2")
    implementation("io.ktor:ktor-server-auth-jwt:3.4.2")

    // Database - Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:1.2.0")
    implementation("org.jetbrains.exposed:exposed-dao:1.2.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.2.0")
    implementation("org.jetbrains.exposed:exposed-migration-core:1.2.0")
    implementation("org.jetbrains.exposed:exposed-migration-jdbc:1.2.0")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:1.2.0")
    implementation("org.jetbrains.exposed:exposed-json:1.2.0")
    implementation("org.postgresql:postgresql:42.7.10")

    // Database migrations
    implementation("org.flywaydb:flyway-core:12.4.0")
    implementation("org.flywaydb:flyway-database-postgresql:12.4.0")

    // Kotlin coroutines and datetime
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")

    // Authentication
    implementation("com.auth0:jwks-rsa:0.23.0")

    // AWS S3 storage
    implementation("software.amazon.awssdk:s3:2.42.35")
    implementation("software.amazon.awssdk:netty-nio-client:2.42.35")

    // Azure Blob Storage
    implementation("com.azure:azure-storage-blob:12.33.3")
    implementation("com.azure:azure-storage-blob-batch:12.29.3")

    // Utilities
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.2")

    // Koin for dependency injection - use koin-ktor3 for Ktor 3.x compatibility
    implementation("io.insert-koin:koin-core:4.2.1")
    implementation("io.insert-koin:koin-ktor:4.2.1")
    implementation("io.insert-koin:koin-logger-slf4j:4.2.1")
    implementation("io.insert-koin:koin-annotations:2.3.2-Beta1")
    ksp("io.insert-koin:koin-ksp-compiler:2.3.2-Beta1")

    // Open-API specification generation + routing annotations
    implementation("io.ktor:ktor-server-routing-openapi:3.4.2")
    implementation("io.ktor:ktor-server-openapi:3.4.2")

    // Security. override to secure versions to fix CVEs in transitive dependencies
    constraints {
        // dependency of flyway-core and ktor-server-auth-jwt
        implementation("tools.jackson.core:jackson-core:3.1.2") {
            because("Fixes CVE GHSA-72hv-8253-57qq - Number Length Constraint Bypass in Async Parser")
        }
        // dependency of ktor-server-auth-jwt
        implementation("com.fasterxml.jackson.core:jackson-core:2.21.2") {
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
    // Netty uses System::loadLibrary (a restricted method) to load native libs for
    // better I/O performance. Without this flag the JVM emits warnings today and will
    // block the call entirely in a future release.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

// ── Swagger UI ────────────────────────────────────────────────────────────────
// Copy swagger-ui-dist assets (committed to git) into the build resources directory,
// so they are bundled with the server JAR.

val swaggerUiSrc = layout.projectDirectory.dir("frontend/node_modules/swagger-ui-dist")
val swaggerUiDest = layout.buildDirectory.dir("generated/swagger-ui/static/swagger-ui")

val copySwaggerUi by tasks.registering(Copy::class) {
    group = "swagger-ui"
    description = "Copy swagger-ui-dist assets into the build resources directory"
    from(swaggerUiSrc) {
        include(
            "swagger-ui-bundle.js",
            "swagger-ui-bundle.js.map",
            "swagger-ui.css",
            "swagger-ui.css.map",
            "favicon-16x16.png",
            "favicon-32x32.png",
            "oauth2-redirect.html",
            "oauth2-redirect.js",
        )
    }
    into(swaggerUiDest)
}

// Also copy our hand-written index.html into the same build directory
val copySwaggerUiIndex by tasks.registering(Copy::class) {
    group = "swagger-ui"
    description = "Copy the Swagger UI index.html into the build resources directory"
    from(layout.projectDirectory.file("frontend/swagger-ui/index.html"))
    into(swaggerUiDest)
}

// Add the generated directory as an extra resource source so it ends up on the classpath
sourceSets["main"].resources.srcDir(layout.buildDirectory.dir("generated/swagger-ui"))

tasks.named("processResources") {
    dependsOn(copySwaggerUi, copySwaggerUiIndex)
}
// ─────────────────────────────────────────────────────────────────────────────

// ── Static OpenAPI specs ───────────────────────────────────────────────────
// Copy the reference YAML specs from docs/ into the build resources so they
// are bundled with the JAR and served at /docs/openapi/<filename>.

val openApiSpecsDest = layout.buildDirectory.dir("generated/openapi-specs/static/openapi-specs")

val copyOpenApiSpecs by tasks.registering(Copy::class) {
    group = "documentation"
    description = "Copy reference OpenAPI YAML specs from docs/ into build resources"
    from(layout.projectDirectory.dir("docs")) {
        include(
            "documenten-1.5.0.yaml",
            "maykin-documenten-1.5.0.yaml",
        )
    }
    into(openApiSpecsDest)
}

sourceSets["main"].resources.srcDir(layout.buildDirectory.dir("generated/openapi-specs"))

tasks.named("processResources") {
    dependsOn(copyOpenApiSpecs)
}
// ─────────────────────────────────────────────────────────────────────────────

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
    // Exclude signature files from signed dependency JARs; merging them into a
    // fat JAR invalidates their digests and causes a SecurityException at startup.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
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
