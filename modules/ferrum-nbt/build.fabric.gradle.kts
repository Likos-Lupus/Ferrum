plugins {
    id("ferrum.java-conventions")
    id("ferrum.java-test")
    id("ferrum.package-coverage")
    id("dev.kikugie.loom-back-compat")
}

val fabricApiVersion = if (sc.current.version.startsWith("26")) {
    libs.versions.fabricApi2612.get()
} else {
    libs.versions.fabricApi1211.get()
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${libs.versions.fabric.loader.get()}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    api(project(":java-shared:module-api"))
    api(project(":java-shared:core-runtime"))

    testImplementation(project(":java-shared:testkit"))
    testImplementation(platform(libs.jackson.bom))
    testImplementation(libs.jackson.databind)
}

sourceSets.main {
    java.srcDir("src/fabric/java")
}

java {
    withSourcesJar()
}

tasks.processResources {
    filesMatching("fabric.mod.json") {
        expand(
            "id" to "ferrum-nbt",
            "name" to "FerrumNbt",
            "version" to project.version.toString(),
            "minecraft" to sc.current.version,
        )
    }
    exclude("META-INF/neoforge.mods.toml")
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

tasks.register<Test>("nativeTest") {
    group = "verification"
    description = "Runs @Tag(\"native\") differential tests against the cargo-built library."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("native")
    }
    dependsOn(":java-shared:core-runtime:buildNative")
    systemProperty(
        "ferrum.native.library",
        nativeCrate.dir("target/debug").file(nativeLibraryName).asFile.absolutePath,
    )
}

val buildNativeRelease = tasks.register<Exec>("buildNativeRelease") {
    group = "build"
    description = "Builds the optimized libferrum for benchmarks."
    workingDir = nativeCrate.asFile

    commandLine("cargo", "build", "--locked", "--release")
}

tasks.register<Test>("nbtBenchmark") {
    group = "verification"
    description = "Runs the NBT kernel crossover benchmark against the release library (not part of check)."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("benchmark")
    }
    dependsOn(buildNativeRelease)
    systemProperty(
        "ferrum.native.library",
        nativeCrate.dir("target/release").file(nativeLibraryName).asFile.absolutePath,
    )
    testLogging {
        showStandardStreams = true
    }
}

tasks.register<Test>("generateNbtGolden") {
    group = "verification"
    description = "Regenerates the MUTF-8 golden corpus from Java writeUTF."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform()
    filter {
        includeTestsMatching("top.likoslupus.ferrum.nbt.NbtGoldenGeneratorTest")
    }
    systemProperty("ferrum.generateNbtGolden", "true")
    systemProperty(
        "ferrum.golden.dir",
        rootProject.layout.projectDirectory
                .dir("native/ferrum-native/tests/golden/nbt")
                .asFile.absolutePath,
    )
    outputs.upToDateWhen { false }
}
