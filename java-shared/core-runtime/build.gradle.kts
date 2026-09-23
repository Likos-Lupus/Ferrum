plugins {
    id("ferrum.java-conventions")
    id("ferrum.java-test")
    id("ferrum.jackson")
    id("ferrum.package-coverage")
}

dependencies {
    api(project(":java-shared:module-api"))
}
