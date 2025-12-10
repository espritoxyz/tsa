plugins {
    id("tsa.kotlin-conventions")
    id("org.jmailen.kotlinter") version Versions.kotlinterPluginVersion
    kotlin("plugin.serialization") version "2.2.21"
}

dependencies {
    implementation(project(":tsa-core"))
    implementation(project(":tsa-test-gen"))

    implementation(group = Packages.tvmDisasm, name = "tvm-disasm", version = Versions.tvmDisasm)
    implementation(group = Packages.tvmDisasm, name = "tvm-opcodes", version = Versions.tvmDisasm)
    implementation(group = Packages.tonKotlin, name = "ton-kotlin-tvm", version = Versions.tonKotlin)
    implementation(group = Packages.tonKotlin, name = "ton-kotlin-bigint", version = Versions.tonKotlin)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("tools.profiler:async-profiler:${Versions.asyncProfiler}")
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
