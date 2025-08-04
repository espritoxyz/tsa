plugins {
    id("tsa.kotlin-conventions")
}

dependencies {
    implementation(project(":tsa-core"))
    implementation(project(":tsa-test-gen"))

    implementation(group = Packages.tvmDisasm, name = "tvm-opcodes", version = Versions.tvmDisasm)

    implementation(group = Packages.tonKotlin, name = "ton-kotlin-tvm", version = Versions.tonKotlin)
    implementation(group = Packages.tonKotlin, name = "ton-kotlin-bigint", version = Versions.tonKotlin)

    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")

    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    implementation(kotlin("test"))
}
