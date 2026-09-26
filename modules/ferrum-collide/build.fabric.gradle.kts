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
            "id" to "ferrum-collide",
            "name" to "FerrumCollide",
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

tasks.register<Test>("collideBenchmark") {
    group = "verification"
    description = "Runs the collide kernel benchmark against the release library."
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

tasks.register<Delete>("cleanCollideGolden") {
    delete(
        rootProject.layout.projectDirectory.dir(
            "native/ferrum-native/tests/golden/collide"
        )
    )
}

tasks.register<Test>("generateCollideGolden") {
    group = "verification"
    description = "Regenerates the collide golden corpus from the vanilla engines."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform()
    filter {
        includeTestsMatching("top.likoslupus.ferrum.collide.CollideGoldenGeneratorTest")
    }
    dependsOn("cleanCollideGolden")
    systemProperty("ferrum.generateCollideGolden", "true")
    systemProperty(
        "ferrum.collide.golden.dir",
        rootProject.layout.projectDirectory
                .dir("native/ferrum-native/tests/golden/collide")
                .asFile.absolutePath,
    )
    outputs.upToDateWhen { false }
}
