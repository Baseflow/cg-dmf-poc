import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    kotlin("jvm") version "2.2.21"
    application
    kotlin("plugin.serialization") version "2.2.21"
    id("com.github.ben-manes.versions") version "0.53.0"
}

group = "com.baseflow"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("com.h2database:h2:2.4.240")
    testImplementation("io.mockk:mockk:1.14.7")
    implementation("io.ktor:ktor-server-core-jvm:3.3.3")
    implementation("io.ktor:ktor-server-netty-jvm:3.3.3")
    implementation("io.ktor:ktor-client-core:3.3.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.3.3")
    implementation("io.ktor:ktor-server-conditional-headers:3.3.3")
    implementation("io.ktor:ktor-server-status-pages:3.3.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.3")
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.3.3")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.3.3")
    implementation("org.jetbrains.exposed:exposed-core:1.0.0-rc-4")
    implementation("org.jetbrains.exposed:exposed-dao:1.0.0-rc-4")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.0.0-rc-4")
    implementation("org.jetbrains.exposed:exposed-migration-core:1.0.0-rc-4")
    implementation("org.jetbrains.exposed:exposed-migration-jdbc:1.0.0-rc-4")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:1.0.0-rc-4")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.flywaydb:flyway-core:11.19.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.19.0")
    implementation("ch.qos.logback:logback-classic:1.5.21")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("io.ktor:ktor-server-auth:3.3.3")
    implementation("io.ktor:ktor-server-auth-jwt:3.3.3")
    implementation("com.auth0:jwks-rsa:0.22.1")
    implementation("software.amazon.awssdk:s3:2.32.+")
    implementation("software.amazon.awssdk:netty-nio-client:2.32.+")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.8.+")
    implementation("com.auth0:jwks-rsa:0.23.0")
}


kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
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
    val t = this as DependencyUpdatesTask
    t.rejectVersionIf {
        isNonStable(candidate.version) && !isNonStable(currentVersion)
    }
    t.checkConstraints = true
    t.outputFormatter = "plain,json,html"
    t.outputDir = "build/reports"
    t.reportfileName = "dependency-updates-report"
}