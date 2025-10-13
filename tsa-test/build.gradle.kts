plugins {
    id("tsa.kotlin-conventions")
    id("org.jmailen.kotlinter") version Versions.kotlinterPluginVersion
}

dependencies {
    implementation(project(":tsa-core"))
    implementation(project(":tsa-test-gen"))

    implementation(group = Packages.tvmDisasm, name = "tvm-opcodes", version = Versions.tvmDisasm)

    implementation(group = Packages.tonKotlin, name = "ton-kotlin-tvm", version = Versions.tonKotlin)
    implementation(group = Packages.tonKotlin, name = "ton-kotlin-bigint", version = Versions.tonKotlin)

    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")

    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("tools.profiler:async-profiler:4.1")

    implementation(kotlin("test"))
}

tasks.test {
    maxHeapSize = "2048m"
}

tasks.register("formatAndLintAll") {
    group = "formatting"

    dependsOn(tasks.findByName("formatKotlin"))
    dependsOn(tasks.findByName("lintKotlin"))
}
