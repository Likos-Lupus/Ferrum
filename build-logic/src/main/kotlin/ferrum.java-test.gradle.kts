import top.likoslupus.ferrum.buildlogic.catalogLibrary

plugins {
    java
}

dependencies {
    add(
        "testImplementation",
        platform(catalogLibrary("junit-bom").get())
    )
    add(
        "testImplementation",
        catalogLibrary("junit-jupiter").get()
    )
    add(
        "testRuntimeOnly",
        platform(catalogLibrary("junit-bom").get())
    )
    add(
        "testRuntimeOnly",
        catalogLibrary("junit-platform-launcher").get()
    )
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("native")
    }
}
