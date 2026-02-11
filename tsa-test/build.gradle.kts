import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("tsa.kotlin-conventions")
    id("org.jmailen.kotlinter") version Versions.kotlinterPluginVersion
}

dependencies {
    implementation(project(":tsa-core"))
    implementation(project(":tsa-test-gen"))

    implementation(group = Packages.tvmDisasm, name = "tvm-disasm", version = Versions.tvmDisasm)
    implementation(group = Packages.tvmDisasm, name = "tvm-opcodes", version = Versions.tvmDisasm)

    implementation(group = Packages.tonKotlin, name = "ton-kotlin-tvm", version = Versions.tonKotlin)
    implementation(group = Packages.tonKotlin, name = "ton-kotlin-bigint", version = Versions.tonKotlin)

    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")

    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("tools.profiler:async-profiler:${Versions.asyncProfiler}")

    implementation(kotlin("test"))
}

tasks.test {
    maxHeapSize = "2048m"
    testLogging {
        showStandardStreams = true
        events = setOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED, TestLogEvent.STARTED)
    }
}

tasks.register<Test>("intercontractTests") {
    group = "verification"
    description = "Run intercontract tests (fast, as no sandbox is used at the moment)"
    useJUnitPlatform {
        includeTags("intercontract")
    }
}

tasks.register("formatAndLintAll") {
    group = "formatting"

    dependsOn(tasks.findByName("formatKotlin"))
    dependsOn(tasks.findByName("lintKotlin"))
}
