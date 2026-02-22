plugins {
    kotlin("jvm") version "2.3.0"
}

group = "eg.zaidi.onboarding"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

    implementation("com.github.ajalt.clikt:clikt:5.0.1")

    // optional support for rendering markdown in help messages
    implementation("com.github.ajalt.clikt:clikt-markdown:5.0.1")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("io.kotest:kotest-assertions-core:6.1.3")
    testImplementation("io.kotest:kotest-property:6.1.3")

}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}