plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.kotlinx.rpc.plugin)
}

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.kotlinx.rpc.krpc.ktor.server)
    implementation(libs.kotlinx.rpc.krpc.ktor.client)
    implementation(libs.ktor.server.velocity)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.cors)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.arrow.core)
    implementation(libs.arrow.fx.stm)
    implementation(libs.lettuce.core)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.poi)
    implementation(libs.graphql.kotlin.ktor.server)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.arrow)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.testcontainers.redis)
    testImplementation(libs.testcontainers.junit)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // Testcontainers' default strategies probe a hard-coded list of socket
    // locations and don't see Docker Desktop's per-user socket on macOS
    // (~/.docker/run/docker.sock). When DOCKER_HOST isn't already set in the
    // environment, point the test JVM at that socket if it exists. On
    // Linux/CI both branches are no-ops so /var/run/docker.sock is used.
    val explicit = System.getenv("DOCKER_HOST")
    val desktopSock = file("${System.getProperty("user.home")}/.docker/run/docker.sock")
    val dockerHost = explicit ?: if (desktopSock.exists()) "unix://${desktopSock.absolutePath}" else null
    if (dockerHost != null) {
        environment("DOCKER_HOST", dockerHost)
        // Testcontainers' EnvironmentAndSystemPropertyClientProviderStrategy
        // reads the lowercase 'docker.host' system property — not 'DOCKER_HOST'.
        systemProperty("docker.host", dockerHost)
        // Surface the test JVM's view in stdout for diagnosis.
        testLogging { showStandardStreams = true }
    }
}


