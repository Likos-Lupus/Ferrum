pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.architectury.dev") { name = "Architectury" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("dev.kikugie.loom-back-compat") version "0.4.2"
}

gradle.beforeProject {
    val loader = path.substringAfterLast('-')
    if (path.startsWith(":modules:") && (loader == "fabric" || loader == "neoforge")) {
        extensions.extraProperties.set("loom.platform", loader)
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev")
        maven("https://maven.neoforged.net/releases/")
    }
}

rootProject.name = "ferrum"

include(":java-shared:module-api")
include(":java-shared:core-runtime")
include(":java-shared:testkit")
include(":modules:ferrum-core")
include(":modules:ferrum-nbt")
include(":modules:ferrum-codec")
include(":modules:ferrum-palette")
include(":modules:ferrum-noise")
include(":modules:ferrum-light")

stonecutter {
    create(":modules:ferrum-core") {
        fun target(
            projectName: String,
            vararg loaders: String,
            version: String = projectName
        ) {
            loaders.forEach {
                version("$projectName-$it", version).buildscript("build.$it.gradle.kts")
            }
        }

        target("1.21.1", "fabric", "neoforge")
        target("26.1.2", "fabric", "neoforge")
        vcsVersion = "26.1.2-fabric"
    }

    create(":modules:ferrum-nbt") {
        fun target(
            projectName: String,
            vararg loaders: String,
            version: String = projectName
        ) {
            loaders.forEach {
                version("$projectName-$it", version).buildscript("build.$it.gradle.kts")
            }
        }

        target("1.21.1", "fabric", "neoforge")
        target("26.1.2", "fabric", "neoforge")
        vcsVersion = "26.1.2-fabric"
    }

    create(":modules:ferrum-codec") {
        fun target(
            projectName: String,
            vararg loaders: String,
            version: String = projectName
        ) {
            loaders.forEach {
                version("$projectName-$it", version).buildscript("build.$it.gradle.kts")
            }
        }

        target("1.21.1", "fabric", "neoforge")
        target("26.1.2", "fabric", "neoforge")
        vcsVersion = "26.1.2-fabric"
    }

    create(":modules:ferrum-palette") {
        fun target(
            projectName: String,
            vararg loaders: String,
            version: String = projectName
        ) {
            loaders.forEach {
                version("$projectName-$it", version).buildscript("build.$it.gradle.kts")
            }
        }

        target("1.21.1", "fabric", "neoforge")
        target("26.1.2", "fabric", "neoforge")
        vcsVersion = "26.1.2-fabric"
    }

    create(":modules:ferrum-noise") {
        fun target(
            projectName: String,
            vararg loaders: String,
            version: String = projectName
        ) {
            loaders.forEach {
                version("$projectName-$it", version).buildscript("build.$it.gradle.kts")
            }
        }

        target("1.21.1", "fabric", "neoforge")
        target("26.1.2", "fabric", "neoforge")
        vcsVersion = "26.1.2-fabric"
    }

    create(":modules:ferrum-light") {
        fun target(
            projectName: String,
            vararg loaders: String,
            version: String = projectName
        ) {
            loaders.forEach {
                version("$projectName-$it", version).buildscript("build.$it.gradle.kts")
            }
        }

        target("1.21.1", "fabric", "neoforge")
        target("26.1.2", "fabric", "neoforge")
        vcsVersion = "26.1.2-fabric"
    }
}
