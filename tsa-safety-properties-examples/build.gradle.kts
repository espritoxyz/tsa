plugins {
    id("tsa.kotlin-conventions")
    id("org.jmailen.kotlinter") version Versions.kotlinterPluginVersion
}

dependencies {
    testImplementation(project(":tsa-core"))

    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")
    testImplementation(kotlin("test"))
}

tasks.register("formatAndLintAll") {
    group = "formatting"

    dependsOn(tasks.findByName("formatKotlin"))
    dependsOn(tasks.findByName("lintKotlin"))
}
