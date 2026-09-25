import net.ltgt.gradle.errorprone.errorprone
import top.likoslupus.ferrum.buildlogic.catalogLibrary

plugins {
    `java-library`
    id("net.ltgt.errorprone")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:all",
            "-Xlint:-restricted",
            "-Werror",
            "-parameters"
        )
    )
    options.errorprone {
        error("NullAway")
        option("NullAway:OnlyNullMarked", "true")
        error("AddNullMarkedToPackageInfo")
        disableWarningsInGeneratedCode.set(true)
    }
}

dependencies {
    add(
        "api",
        catalogLibrary("jspecify").get()
    )
    add(
        "errorprone",
        catalogLibrary("errorprone-core").get()
    )
    add(
        "errorprone",
        catalogLibrary("nullaway").get()
    )
    add(
        "compileOnly",
        catalogLibrary("errorprone-annotations").get()
    )
    add(
        "testCompileOnly",
        catalogLibrary("errorprone-annotations").get()
    )
    add(
        "compileOnlyApi",
        catalogLibrary("slf4j-api").get()
    )
}
