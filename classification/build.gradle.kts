plugins {
    kotlin("jvm")
}

group = "com.ebalance"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
}

dependencies {

    val dl4jVersion = "1.0.0-M2.1"

    implementation(libs.commonText)

    // Core NLP and DL4J
    implementation("org.deeplearning4j:deeplearning4j-nlp:$dl4jVersion")

    // The main engine (API)
    implementation("org.nd4j:nd4j-native:$dl4jVersion")

    // Native binaries for macOS Apple Silicon
    implementation("org.nd4j:nd4j-native:$dl4jVersion:macosx-arm64")
    implementation("org.bytedeco:openblas:0.3.19-1.5.7:macosx-arm64")

    // Logging (required to see what's happening)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}