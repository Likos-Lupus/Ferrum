package top.likoslupus.ferrum.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal fun Project.catalogLibrary(alias: String): Provider<MinimalExternalModuleDependency> {
    val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
    return catalog
            .findLibrary(alias)
            .orElseThrow {
                IllegalStateException("Missing version catalog library alias '$alias'")
            }
}
