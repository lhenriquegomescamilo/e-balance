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
    
    // Enable zip64 for large JARs (Google API dependencies)
    isZip64 = true
}
repositories {
    mavenCentral()
    google()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
}

dependencies {
    // Project modules
    implementation(project(":classification"))

    // Google Sheets API
    implementation("com.google.api-client:google-api-client:1.34.1")
    implementation("com.google.apis:google-api-services-sheets:v4-rev612-1.25.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.16.0")
    implementation("com.google.http-client:google-http-client-jackson2:1.43.3")

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

    implementation(libs.kotlinxCollectionImmutable)

    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestProperty)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsArrow)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")

}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}