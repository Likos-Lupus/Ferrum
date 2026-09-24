plugins {
    id("ferrum.java-conventions")
    id("ferrum.java-test")
    id("ferrum.jackson")
    id("ferrum.package-coverage")
}

dependencies {
    api(project(":java-shared:module-api"))

    testImplementation(libs.slf4j.api)
    testRuntimeOnly(libs.slf4j.simple)
}

val nativeCrate = rootProject.layout.projectDirectory.dir("native/ferrum-native")

val nativeLibraryName = when {
    System.getProperty("os.name").lowercase().contains("win") -> "ferrum.dll"
    System.getProperty("os.name").lowercase().contains("mac") -> "libferrum.dylib"
    else -> "libferrum.so"
}

val buildNative = tasks.register<Exec>("buildNative") {
    group = "build"
    description = "Builds libferrum with cargo for native-tagged tests."
    workingDir = nativeCrate.asFile

    commandLine("cargo", "build", "--locked", "--features", "test-hooks")
}

tasks.register<Test>("nativeTest") {
    group = "verification"
    description = "Runs @Tag(\"native\") tests against the cargo-built library."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("native")
    }
    dependsOn(buildNative)
    systemProperty(
        "ferrum.native.library",
        nativeCrate.dir("target/debug").file(nativeLibraryName).asFile.absolutePath,
    )
}
