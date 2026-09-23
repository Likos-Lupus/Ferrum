plugins {
    id("ferrum.java-conventions")
    id("ferrum.java-test")
    id("ferrum.package-coverage")
    id("dev.kikugie.loom-back-compat")
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${libs.versions.fabric.loader.get()}")

    api(project(":java-shared:module-api"))
    api(project(":java-shared:core-runtime"))
}

java {
    withSourcesJar()
}

tasks.processResources {
    filesMatching("fabric.mod.json") {
        expand(
            "id" to "ferrum-core",
            "name" to "FerrumCore",
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
