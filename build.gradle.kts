import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    kotlin("jvm") version "2.4.0"
    application
    kotlin("plugin.serialization") version "2.4.0"
    id("com.github.ben-manes.versions") version "0.54.0"

    // Code formatting with Spotless and ktlint
    id("com.diffplug.spotless") version "8.6.0"
}

group = "com.baseflow"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

dependencies {
    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation("com.h2database:h2:2.4.240")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("io.ktor:ktor-client-mock:3.5.0")
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.5.0")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.5.0")
    testImplementation("io.insert-koin:koin-test")
    testImplementation("io.insert-koin:koin-test-junit5")

    // Ktor server and client
    implementation("io.ktor:ktor-server-core-jvm:3.5.0")
    implementation("io.ktor:ktor-server-netty-jvm:3.5.0")
    implementation("io.ktor:ktor-client-core:3.5.0")
    implementation("io.ktor:ktor-client-cio:3.5.0")
    implementation("io.ktor:ktor-server-content-negotiation:3.5.0")
    implementation("io.ktor:ktor-server-conditional-headers:3.5.0")
    implementation("io.ktor:ktor-server-status-pages:3.5.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")
    implementation("io.ktor:ktor-server-auth:3.5.0")
    implementation("io.ktor:ktor-server-auth-jwt:3.5.0")

    // Connection pool
    implementation("com.zaxxer:HikariCP:7.0.2")

    // Database - Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:1.3.0")
    implementation("org.jetbrains.exposed:exposed-dao:1.3.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.3.0")
    implementation("org.jetbrains.exposed:exposed-migration-core:1.3.0")
    implementation("org.jetbrains.exposed:exposed-migration-jdbc:1.3.0")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:1.3.0")
    implementation("org.jetbrains.exposed:exposed-json:1.3.0")
    implementation("org.jetbrains.exposed:exposed-crypt:1.3.0")
    implementation("org.postgresql:postgresql:42.7.11")

    // Database migrations
    implementation("org.flywaydb:flyway-core:12.8.1")
    implementation("org.flywaydb:flyway-database-postgresql:12.8.1")

    // Kotlin coroutines and datetime
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.34")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")

    // Authentication
    implementation("com.auth0:jwks-rsa:0.24.1")

    // AWS S3 storage
    implementation("software.amazon.awssdk:s3:2.46.4")
    implementation("software.amazon.awssdk:netty-nio-client:2.46.4")

    // Azure Blob Storage
    implementation("com.azure:azure-storage-blob:12.34.0")
    implementation("com.azure:azure-storage-blob-batch:12.30.0")

    // Utilities
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.22.0")

    // Koin for dependency injection - use koin-ktor3 for Ktor 3.x compatibility
    implementation(platform("io.insert-koin:koin-bom:4.2.1"))
    implementation("io.insert-koin:koin-core")
    implementation("io.insert-koin:koin-ktor")
    implementation("io.insert-koin:koin-logger-slf4j")
    implementation("io.insert-koin:koin-annotations")

    // Open-API specification generation + routing annotations
    implementation("io.ktor:ktor-server-routing-openapi:3.5.0")

    // Security. override to secure versions to fix CVEs in transitive dependencies
    constraints {
        // dependency of flyway-core and ktor-server-auth-jwt
        implementation("tools.jackson.core:jackson-core:3.1.4") {
            because("Fixes CVE GHSA-72hv-8253-57qq - Number Length Constraint Bypass in Async Parser")
        }
        // dependency of ktor-server-auth-jwt
        implementation("com.fasterxml.jackson.core:jackson-core:2.22.0") {
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

// Also copy our hand-written *.html Swagger files into the same build directory
val copySwaggerUiIndex by tasks.registering(Copy::class) {
    group = "swagger-ui"
    description = "Copy the Swagger UI documenten-api.html into the build resources directory"
    from(layout.projectDirectory.dir("frontend/swagger-ui")) { include("*.html") }
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
            "documenten-1.6.0.yaml",
            "documenten-1.7.0-rc.yaml",
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
    // Provide default encryption keys for unit tests so that the lazy Encryptor
    // in BlobStorageRepositories can initialise when encrypted columns are used.
    environment("ENCRYPTION_SECRET_KEY", System.getenv("ENCRYPTION_SECRET_KEY") ?: "test-secret-key-for-unit-tests")
    environment("ENCRYPTION_SALT", System.getenv("ENCRYPTION_SALT") ?: "deadbeefcafe0123456789abcdef0123")
    environment("CLIENT_SECRET_ENCRYPTION_KEY", System.getenv("CLIENT_SECRET_ENCRYPTION_KEY") ?: "test-client-secret-key-for-unit-tests")
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
