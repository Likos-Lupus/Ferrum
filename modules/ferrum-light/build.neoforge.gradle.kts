plugins {
    id("ferrum.java-conventions")
    id("ferrum.java-test")
    id("ferrum.package-coverage")
    id("dev.kikugie.loom-back-compat")
}

val neoforgeVersion = if (sc.current.version.startsWith("26")) {
    libs.versions.neoforge2612.get()
} else {
    libs.versions.neoforge1211.get()
}

repositories {
    maven("https://maven.neoforged.net/releases/")
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()
    add("neoForge", "net.neoforged:neoforge:$neoforgeVersion")

    api(project(":java-shared:module-api"))
    api(project(":java-shared:core-runtime"))

    testImplementation(project(":java-shared:testkit"))
}

sourceSets.main {
    java.srcDir("src/neoforge/java")
}

java {
    withSourcesJar()
}

tasks.processResources {
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(
            "id" to "ferrum-light",
            "name" to "FerrumLight",
            "version" to project.version.toString(),
            "minecraft" to sc.current.version,
        )
    }
    exclude("fabric.mod.json")
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    description = "Builds the remapped mod jar and copies it into a stable directory."
    from(loomx.modJar, loomx.modSourcesJar)
    into(rootProject.layout.buildDirectory.dir("libs/${sc.current.project}"))
}

val nativeCrate = rootProject.layout.projectDirectory.dir("native/ferrum-native")

val nativeLibraryName = when {
    System.getProperty("os.name").lowercase().contains("win") -> "ferrum.dll"
    System.getProperty("os.name").lowercase().contains("mac") -> "libferrum.dylib"
    else -> "libferrum.so"
}

fun Test.nativeLibrary(path: String) {
    systemProperty(
        "ferrum.native.library",
        nativeCrate.dir(path).file(nativeLibraryName).asFile.absolutePath,
    )
}

tasks.register<Test>("nativeTest") {
    group = "verification"
    description = "Runs @Tag(\"native\") differential tests against the cargo-built library."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("native")
    }
    dependsOn(":java-shared:core-runtime:buildNative")
    nativeLibrary("target/debug")
}

val buildNativeRelease = tasks.register<Exec>("buildNativeRelease") {
    group = "build"
    description = "Builds the optimized libferrum for benchmarks."
    workingDir = nativeCrate.asFile

    commandLine("cargo", "build", "--locked", "--release")
}

tasks.register<Test>("lightBenchmark") {
    group = "verification"
    description = "Runs the block-light batch benchmark against the release library."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("benchmark")
    }
    dependsOn(buildNativeRelease)
    nativeLibrary("target/release")
    testLogging {
        showStandardStreams = true
    }
}
