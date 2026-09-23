plugins {
    id("ferrum.java-conventions")
    id("ferrum.java-test")
    id("ferrum.package-coverage")
}

dependencies {
    api(platform(libs.junit.bom.get()))
    api(libs.junit.jupiter.get())
}
