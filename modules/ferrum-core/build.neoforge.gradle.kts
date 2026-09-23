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
}

java {
    withSourcesJar()
}

tasks.processResources {
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(
            "id" to "ferrum-core",
            "name" to "FerrumCore",
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
