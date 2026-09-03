import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.io.File
import java.util.zip.ZipFile

plugins {
    kotlin("jvm") version "2.4.0"
    application
    kotlin("plugin.serialization") version "2.4.0"
    id("com.github.ben-manes.versions") version "0.54.0"

    // Code formatting with Spotless and ktlint
    id("com.diffplug.spotless") version "8.9.0"
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
    testImplementation("io.ktor:ktor-client-mock:3.5.1")
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.5.1")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.5.1")
    testImplementation("io.insert-koin:koin-test")
    testImplementation("io.insert-koin:koin-test-junit5")

    // Ktor server and client
    implementation("io.ktor:ktor-server-core-jvm:3.5.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.5.1")
    implementation("io.ktor:ktor-client-core:3.5.1")
    implementation("io.ktor:ktor-client-cio:3.5.1")
    implementation("io.ktor:ktor-server-content-negotiation:3.5.1")
    implementation("io.ktor:ktor-server-conditional-headers:3.5.1")
    implementation("io.ktor:ktor-server-status-pages:3.5.1")
    implementation("io.ktor:ktor-server-html-builder:3.5.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.1")
    implementation("io.ktor:ktor-server-auth:3.5.1")
    implementation("io.ktor:ktor-server-auth-jwt:3.5.1")

    // Connection pool
    implementation("com.zaxxer:HikariCP:7.1.0")

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
    implementation("org.flywaydb:flyway-core:12.9.0")
    implementation("org.flywaydb:flyway-database-postgresql:12.9.0")

    // Kotlin coroutines and datetime
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.12.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.36")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")

    // Authentication
    implementation("com.auth0:jwks-rsa:0.24.1")

    // AWS S3 storage
    implementation("software.amazon.awssdk:s3:2.46.17")
    implementation("software.amazon.awssdk:netty-nio-client:2.46.17")

    // Azure Blob Storage
    implementation("com.azure:azure-storage-blob:12.35.0")
    implementation("com.azure:azure-storage-blob-batch:12.31.0")

    // Utilities
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.22.0")

    // Koin for dependency injection - use koin-ktor3 for Ktor 3.x compatibility
    implementation(platform("io.insert-koin:koin-bom:4.2.2"))
    implementation("io.insert-koin:koin-core")
    implementation("io.insert-koin:koin-ktor")
    implementation("io.insert-koin:koin-logger-slf4j")
    implementation("io.insert-koin:koin-annotations")

    // Open-API specification generation + routing annotations
    implementation("io.ktor:ktor-server-routing-openapi:3.5.1")

    // To override a transitive dependency version to fix a CVE, use a constraint block like this:
    // constraints {
    //     implementation("tools.jackson.core:jackson-core:3.1.4") {
    //         because("Fixes CVE GHSA-72hv-8253-57qq - Number Length Constraint Bypass in Async Parser")
    //     }
    // }
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

// ── Frontend npm dependencies ─────────────────────────────────────────────────
// Wrapped in a ValueSource so that the ProcessBuilder calls are configuration-
// cache safe. Tries the current PATH first, then falls back to spawning a login
// shell (-l) to obtain the PATH set in a terminal session — covering version
// managers like nvm, volta, and fnm that only add npm to PATH interactively.
abstract class ResolveNpmSource : ValueSource<String, ValueSourceParameters.None> {
    private fun npmInPath(path: String): String? =
        path
            .split(":")
            .map { File(it, "npm") }
            .firstOrNull { it.canExecute() }
            ?.absolutePath

    override fun obtain(): String? {
        npmInPath(System.getenv("PATH") ?: "")?.let { return it }
        val shell = System.getenv("SHELL") ?: return null
        val loginPath =
            runCatching {
                val proc = ProcessBuilder(shell, "-l", "-c", "echo \$PATH").start()
                proc.inputStream
                    .bufferedReader()
                    .readLine()
                    .also { proc.waitFor() }
            }.getOrNull() ?: return null
        return npmInPath(loginPath)
    }
}

val npmPath = providers.of(ResolveNpmSource::class) {}

val npmInstall =
    tasks.register<Exec>("npmInstall") {
        group = "frontend"
        description = "Install frontend npm dependencies"
        workingDir = file("frontend")
        inputs.file("frontend/package-lock.json")
        outputs.file("frontend/node_modules/.package-lock.json")
        val npm = npmPath.orNull
        val nodeModulesPath =
            layout.projectDirectory
                .dir("frontend/node_modules")
                .asFile.absolutePath
        commandLine(npm ?: "npm", "ci")
        onlyIf("npm is available") {
            if (npm == null) {
                if (!File(nodeModulesPath).exists()) {
                    throw GradleException(
                        "'npm' not found and 'frontend/node_modules' does not exist.\n" +
                            "Install Node.js, ensure 'npm' is on your PATH, then run 'npm ci' in the frontend/ directory.",
                    )
                }
                it.logger.warn("npmInstall skipped: 'npm' not found on PATH or login shell PATH. Using existing node_modules.")
            }
            npm != null
        }
    }
// ─────────────────────────────────────────────────────────────────────────────

// ── Swagger UI ────────────────────────────────────────────────────────────────
// Copy swagger-ui-dist assets into the build resources directory,
// so they are bundled with the server JAR.

val swaggerUiSrc = layout.projectDirectory.dir("frontend/node_modules/swagger-ui-dist")
val swaggerUiDest = layout.buildDirectory.dir("generated/swagger-ui/static/swagger-ui")

val copySwaggerUi =
    tasks.register<Copy>("copySwaggerUi") {
        group = "swagger-ui"
        description = "Copy swagger-ui-dist assets into the build resources directory"
        dependsOn(npmInstall)
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
val copySwaggerUiIndex =
    tasks.register<Copy>("copySwaggerUiIndex") {
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

val copyOpenApiSpecs =
    tasks.register<Copy>("copyOpenApiSpecs") {
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

// ── Docs Viewer (docsify) ──────────────────────────────────────────────────
// Copy docsify.min.js and hand-written viewer files into build resources so
// they are bundled with the JAR and served at /docs/<filename>.

val docsViewerDest = layout.buildDirectory.dir("generated/docs-viewer/static/docs-viewer")

val copyDocsViewerAssets =
    tasks.register<Copy>("copyDocsViewerAssets") {
        group = "documentation"
        description = "Copy docsify.min.js and viewer config files into build resources"
        dependsOn(npmInstall)
        from(layout.projectDirectory.dir("frontend/node_modules/docsify/lib")) {
            include("docsify.min.js")
        }
        from(layout.projectDirectory.dir("frontend/docs-viewer"))
        into(docsViewerDest)
    }

val copyDocsMarkdown =
    tasks.register<Copy>("copyDocsMarkdown") {
        group = "documentation"
        description = "Copy docs/*.md files into build resources for docsify"
        from(layout.projectDirectory.dir("docs")) {
            include("*.md", "wopi/*.md", "images/*")
        }
        into(docsViewerDest)
    }

sourceSets["main"].resources.srcDir(layout.buildDirectory.dir("generated/docs-viewer"))

tasks.named("processResources") {
    dependsOn(copyDocsViewerAssets, copyDocsMarkdown)
}
// ─────────────────────────────────────────────────────────────────────────────

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
    // Run in an empty working directory so a developer's local .env file can't leak into unit
    // tests: Config loads .env via dotenv-kotlin relative to the process's working directory,
    // independently of the environment(...) overrides below, so a local .env (e.g. setting
    // BESTANDSDELEN_TRIGGER_SIZE) would otherwise make settings look env-managed in tests.
    val isolatedWorkingDir = layout.buildDirectory.dir("test-workdir").get().asFile
    doFirst { isolatedWorkingDir.mkdirs() }
    workingDir = isolatedWorkingDir
    // Provide default encryption keys for unit tests so that the lazy Encryptor
    // in BlobStorageRepositories can initialise when encrypted columns are used.
    environment("ENCRYPTION_SECRET_KEY", System.getenv("ENCRYPTION_SECRET_KEY") ?: "test-secret-key-for-unit-tests")
    environment("ENCRYPTION_SALT", System.getenv("ENCRYPTION_SALT") ?: "deadbeefcafe0123456789abcdef0123")
    environment("CLIENT_SECRET_ENCRYPTION_KEY", System.getenv("CLIENT_SECRET_ENCRYPTION_KEY") ?: "test-client-secret-key-for-unit-tests")
}

// Multiple dependencies (e.g. flyway-core and flyway-database-postgresql) ship a
// META-INF/services/* file at the same path. With DuplicatesStrategy.EXCLUDE below,
// only the first copy encountered survives in the fat JAR, silently dropping the
// other dependency's ServiceLoader providers (e.g. Flyway's dry-run plugin stub,
// which Flyway looks up unconditionally on startup and NPEs on when missing).
// This task merges those files line-by-line so all providers are preserved.
val mergedServiceFiles = layout.buildDirectory.dir("generated/merged-services")

val mergeServiceFiles by tasks.registering {
    description = "Merges META-INF/services/* entries from all runtime dependencies for the fat JAR"
    // The doLast action below reads jar contents directly (via ZipFile) and writes merged output,
    // which closes over build-script state in a way the configuration cache can't serialize.
    notCompatibleWithConfigurationCache("Reads jar entries directly and writes merged files in doLast")
    val runtimeJarFiles = configurations.runtimeClasspath.get().filter { it.isFile && it.extension == "jar" }
    inputs.files(runtimeJarFiles)
    outputs.dir(mergedServiceFiles)
    doLast {
        val merged = mutableMapOf<String, LinkedHashSet<String>>()
        runtimeJarFiles.forEach { jarFile ->
            ZipFile(jarFile).use { zip ->
                zip
                    .entries()
                    .asSequence()
                    .filter { !it.isDirectory && it.name.startsWith("META-INF/services/") }
                    .forEach { entry ->
                        val providers =
                            zip
                                .getInputStream(entry)
                                .bufferedReader()
                                .readLines()
                                .filter { it.isNotBlank() && !it.startsWith("#") }
                        merged.getOrPut(entry.name) { LinkedHashSet() }.addAll(providers)
                    }
            }
        }
        val dir = mergedServiceFiles.get().asFile
        dir.deleteRecursively()
        merged.forEach { (path, providers) ->
            File(dir, path).apply {
                parentFile.mkdirs()
                writeText(providers.joinToString("\n"))
            }
        }
    }
}

tasks.jar {
    dependsOn(mergeServiceFiles)
    manifest {
        attributes["Main-Class"] = "com.baseflow.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(mergedServiceFiles)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/services/*")
    }
    // Exclude signature files from signed dependency JARs; merging them into a
    // fat JAR invalidates their digests and causes a SecurityException at startup.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
}

// Flyway migration tasks
tasks.register<JavaExec>("flywayMigrate") {
    group = "flyway"
    description = "Migrates the database"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.baseflow.shared.tooling.FlywayMigrationKt")
    args("migrate")
}

tasks.register<JavaExec>("flywayInfo") {
    group = "flyway"
    description = "Prints the details and status information about all migrations"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.baseflow.shared.tooling.FlywayMigrationKt")
    args("info")
}

tasks.register<JavaExec>("flywayUndo") {
    group = "flyway"
    description = "Undoes the most recently applied versioned migration (or -Pargs=<version>)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.baseflow.shared.tooling.FlywayMigrationKt")
    args("undo")
    if (project.hasProperty("args")) {
        args((project.property("args") as String).split("\\s+".toRegex()))
    }
    standardInput = System.`in`
}

tasks.register<JavaExec>("flywayClean") {
    group = "flyway"
    description = "Drops all objects in the configured schemas"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.baseflow.shared.tooling.FlywayMigrationKt")
    args("clean")
    if (project.hasProperty("args")) {
        args((project.property("args") as String).split("\\s+".toRegex()))
    }
    standardInput = System.`in`
}

tasks.register<JavaExec>("flywayValidate") {
    group = "flyway"
    description = "Validates the applied migrations against the available ones"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.baseflow.shared.tooling.FlywayMigrationKt")
    args("validate")
}

tasks.register<JavaExec>("flywayRepair") {
    group = "flyway"
    description = "Repairs the Flyway schema history (updates checksums, removes failed migrations)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.baseflow.shared.tooling.FlywayMigrationKt")
    args("repair")
    if (project.hasProperty("args")) {
        args((project.property("args") as String).split("\\s+".toRegex()))
    }
    standardInput = System.`in`
}

tasks.register<JavaExec>("generateMigration") {
    group = "flyway"
    description = "Generate a Flyway migration script from Exposed table definitions"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.baseflow.shared.tooling.MigrationGeneratorKt")
    // Pass command-line args through
    if (project.hasProperty("args")) {
        args((project.property("args") as String).split("\\s+".toRegex()))
    }
}

tasks.register<JavaExec>("addStubData") {
    group = "database"
    description = "Load stub/seed data into the database for development and testing"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.baseflow.shared.tooling.StubDataLoaderKt")
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
