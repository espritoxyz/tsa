plugins {
    id("tsa.kotlin-conventions")
}

dependencies {
    testImplementation(project(":tsa-core"))

    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")
    testImplementation(kotlin("test"))
}
