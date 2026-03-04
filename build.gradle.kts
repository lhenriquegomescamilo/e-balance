import java.io.File

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

// ---------------------------------------------------------------------------
// Metal GPU bridge distribution support
// ---------------------------------------------------------------------------

// If the dylib was already built (e.g. after running :metal-bridge:linkReleaseSharedMacosArm64),
// automatically copy it into the distribution's lib/ directory.
// This is a no-op on non-Apple machines or when the dylib has not been built yet.
tasks.named("installDist") {
    doLast {
        val dylib = file("metal-bridge/build/bin/macosArm64/releaseShared/libmetal_bridge.dylib")
        if (dylib.exists()) {
            copy {
                from(dylib)
                into(layout.buildDirectory.dir("install/e-balance/lib"))
            }
            logger.lifecycle("Metal GPU bridge included in distribution: libmetal_bridge.dylib → lib/")
        }
    }
}

// When both installDist and the Metal bridge build are in the task graph together,
// installDist must run after the dylib is ready so its doLast block can copy it.
tasks.named("installDist").configure {
    mustRunAfter(":metal-bridge:linkReleaseSharedMacosArm64")
}

// Creates a minimal xcodebuild shim at the CLT path so Kotlin/Native cinterop
// succeeds on machines that have only Command Line Tools (no full Xcode.app).
// The shim satisfies `xcrun xcodebuild -version` with a hardcoded Xcode 16.2 response.
// It is a no-op if a real (or previously installed) xcodebuild is already present.
tasks.register("setupXcodebuildShim") {
    group = "setup"
    description = "Creates a minimal xcodebuild shim so Kotlin/Native cinterop works without full Xcode.app (sudo required)"
    doLast {
        val shimPath = "/Library/Developer/CommandLineTools/usr/bin/xcodebuild"
        if (file(shimPath).exists()) {
            logger.lifecycle("xcodebuild already present at $shimPath — shim not needed.")
            return@doLast
        }
        logger.lifecycle("Creating xcodebuild shim at $shimPath (requires sudo)...")
        val shimScript = """
            #!/bin/bash
            if [[ "${'$'}*" == *"-version"* ]]; then
                echo "Xcode 16.2"
                echo "Build version 16C5032a"
                exit 0
            fi
        """.trimIndent()
        val tmp = File.createTempFile("xcodebuild_shim", ".sh")
        try {
            tmp.writeText(shimScript)
            project.exec { commandLine("sudo", "cp", tmp.absolutePath, shimPath) }
            project.exec { commandLine("sudo", "chmod", "+x", shimPath) }
        } finally {
            tmp.delete()
        }
        logger.lifecycle("Verifying shim: xcrun xcodebuild -version")
        project.exec { commandLine("xcrun", "xcodebuild", "-version") }
        logger.lifecycle("xcodebuild shim installed successfully.")
    }
}

// Make every task in the metal-bridge subproject run after the shim is ready,
// so cinterop never fires before xcodebuild is available.
project(":metal-bridge").tasks.configureEach {
    mustRunAfter("setupXcodebuildShim")
}

// One-command convenience task: install the xcodebuild shim if needed, build the
// Metal bridge, then install the full distribution.
// Usage: ./gradlew installDistWithMetal
// Requires: Apple Silicon Mac with Command Line Tools (sudo access for shim creation).
tasks.register("installDistWithMetal") {
    group = "distribution"
    description = "Installs xcodebuild shim if needed, builds the Metal GPU bridge, and installs the full distribution (Apple Silicon)"
    dependsOn("setupXcodebuildShim", ":metal-bridge:linkReleaseSharedMacosArm64", "installDist")
}