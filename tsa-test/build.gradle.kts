plugins {
    id("tsa.kotlin-conventions")
}

dependencies {
    testImplementation(project(":tsa-core"))
    testImplementation(project(":tsa-test-gen"))

    testImplementation(group = Packages.tvmDisasm, name = "tvm-opcodes", version = Versions.tvmDisasm)

    testImplementation(group = Packages.tonKotlin, name = "ton-kotlin-tvm", version = Versions.tonKotlin)
    testImplementation(group = Packages.tonKotlin, name = "ton-kotlin-bigint", version = Versions.tonKotlin)

    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")

    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    testImplementation(kotlin("test"))
}
