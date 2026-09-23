import top.likoslupus.ferrum.buildlogic.catalogLibrary

plugins {
    java
}

val forbiddenModules = setOf(
    "com.google.code.gson:gson",
    "org.yaml:snakeyaml",
    "org.tomlj:tomlj",
    "com.fasterxml.jackson.core:jackson-databind",
    "com.fasterxml.jackson.core:jackson-core",
    "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml",
    "com.fasterxml.jackson.dataformat:jackson-dataformat-toml",
)

dependencies {
    add(
        "implementation",
        platform(catalogLibrary("jackson-bom").get())
    )
    add(
        "implementation",
        catalogLibrary("jackson-databind").get()
    )
    add(
        "implementation",
        catalogLibrary("jackson-dataformat-yaml").get()
    )
    add(
        "implementation",
        catalogLibrary("jackson-dataformat-toml").get()
    )
}

val dataStackGuard = tasks.register("ferrumDataStackGuard") {
    group = "verification"
    description = "Fails if a forbidden second data-binding stack is present on the runtime classpath."

    val runtimeClasspath = configurations.named("runtimeClasspath")

    doLast {
        val found = runtimeClasspath.get().incoming.resolutionResult.allComponents
                .mapNotNull { component ->
                    val id = component.id
                    if (id is ModuleComponentIdentifier) {
                        "${id.group}:${id.module}"
                    } else {
                        null
                    }
                }
                .filter { it in forbiddenModules }
                .distinct()

        if (found.isNotEmpty()) {
            throw GradleException(
                "Forbidden data-binding dependencies on runtimeClasspath: $found. Ferrum structured data must go through the Jackson 3 DataFormats facade.",
            )
        }
    }
}

tasks.named("check") {
    dependsOn(dataStackGuard)
}
