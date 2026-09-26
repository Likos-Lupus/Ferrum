plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2-fabric" /* [SC] DO NOT EDIT */

tasks.register("chiseledCheck") {
    group = "verification"
    description = "Compiles, statically analyzes, and tests every target node."
    dependsOn(stonecutter.tasks.named("check").map { it.values })
}

tasks.register("chiseledBuildAndCollect") {
    group = "build"
    description = "Builds and collects every target node."
    dependsOn(stonecutter.tasks.named("buildAndCollect").map { it.values })
}
