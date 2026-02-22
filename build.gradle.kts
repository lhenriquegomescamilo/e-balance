plugins {
    kotlin("jvm") version "2.3.0"
    application
}

group = "com.ebalance"
version = "1.0-SNAPSHOT"


application {
    mainClass.set("com.ebalance.MainKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.ebalance.MainKt"
    }
    // This line collects all dependencies and adds them to the JAR
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
repositories {
    mavenCentral()
    google()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
}

dependencies {
    // Project modules
    implementation(project(":classification"))

    implementation("com.github.ajalt.clikt:clikt:5.0.1")

    // optional support for rendering markdown in help messages
    implementation("com.github.ajalt.clikt:clikt-markdown:5.0.1")

    // Apache POI for Excel processing
    implementation("org.apache.poi:poi:5.5.1")
    implementation("org.apache.poi:poi-scratchpad:5.5.1")

    // SQLite
    implementation("org.xerial:sqlite-jdbc:3.45.2.0")

    // Flyway for database migrations
    implementation("org.flywaydb:flyway-core:10.10.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Arrow for functional error handling
    implementation("io.arrow-kt:arrow-core:2.0.1")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("io.kotest:kotest-assertions-core:6.1.3")
    testImplementation("io.kotest:kotest-property:6.1.3")
    testImplementation("io.kotest:kotest-runner-junit5:6.1.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("io.kotest.extensions:kotest-assertions-arrow:2.0.0")

}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}