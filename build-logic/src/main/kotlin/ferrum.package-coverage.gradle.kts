plugins {
    java
}

val sourceRoots = listOf(
    "src/main/java",
    "src/test/java",
    "src/testFixtures/java"
)
val projectRoot = project.projectDir

val packageCoverage = tasks.register("ferrumPackageCoverage") {
    group = "verification"
    description = "Verifies every project-owned package has an @NullMarked package-info.java."

    val roots = sourceRoots
            .map { File(projectRoot, it) }
            .filter { it.isDirectory }

    doLast {
        val errors = mutableListOf<String>()
        for (root in roots) {
            root.walkTopDown()
                    .filter { it.isFile && it.extension == "java" }
                    .forEach { file ->
                        val packagePath = file.parentFile
                                .relativeTo(root).path
                                .replace(File.separatorChar, '/')
                        if (!packagePath.startsWith("top/likoslupus/ferrum")) {
                            return@forEach
                        }

                        val packageInfo = File(file.parentFile, "package-info.java")
                        when {
                            !packageInfo.isFile ->
                                errors += "missing package-info.java for package '$packagePath' (${
                                    file.relativeTo(projectRoot)
                                })"

                            !packageInfo.readText().contains("@NullMarked") ->
                                errors += "package-info.java without @NullMarked: ${
                                    packageInfo.relativeTo(projectRoot)
                                }"
                        }
                    }
        }

        if (errors.isNotEmpty()) {
            throw GradleException(
                "package-info/@NullMarked coverage failed:\n${
                    errors.joinToString("\n")
                }"
            )
        }
    }
}

tasks.named("check") {
    dependsOn(packageCoverage)
}
