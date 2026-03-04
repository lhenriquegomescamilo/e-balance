plugins {
    kotlin("multiplatform")
}

group = "com.ebalance"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    macosArm64 {
        compilations.getByName("main") {
            cinterops {
                val jni by creating {
                    defFile(project.file("src/nativeInterop/cinterop/jni.def"))
                    // Resolve JAVA_HOME at configuration time; fall back to the known GraalVM path
                    val javaHome = System.getenv("JAVA_HOME")
                        ?: "/Users/lcamilo/Library/Java/JavaVirtualMachines/graalvm-ce-21.0.2/Contents/Home"
                    compilerOpts(
                        "-I$javaHome/include",
                        "-I$javaHome/include/darwin"
                    )
                }
                val metal by creating {
                    defFile(project.file("src/nativeInterop/cinterop/metal.def"))
                }
            }
        }
        binaries {
            sharedLib {
                // Produces libmetal_bridge.dylib
                baseName = "metal_bridge"
            }
        }
    }
}
