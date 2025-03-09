plugins {
    id("tsa.kotlin-conventions")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
}

dependencies {
    implementation(group = Packages.tvmDisasm, name = "tvm-opcodes", version = Versions.tvmDisasm)

    implementation(group = Packages.tonKotlin, name = "ton-kotlin-crypto", version = Versions.tonKotlin)
    implementation(group = Packages.tonKotlin, name = "ton-kotlin-tvm", version = Versions.tonKotlin)
    implementation(group = Packages.tonKotlin, name = "ton-kotlin-tonapi-tl", version = Versions.tonKotlin)
    implementation(group = Packages.tonKotlin, name = "ton-kotlin-tlb", version = Versions.tonKotlin)
    implementation(group = Packages.tonKotlin, name = "ton-kotlin-tl", version = Versions.tonKotlin)
    implementation(group = Packages.tonKotlin, name = "ton-kotlin-hashmap-tlb", version = Versions.tonKotlin)
    implementation(group = Packages.tonKotlin, name = "ton-kotlin-contract", version = Versions.tonKotlin)
    implementation(group = Packages.tonKotlin, name = "ton-kotlin-block-tlb", version = Versions.tonKotlin)
    implementation(group = Packages.tonKotlin, name = "ton-kotlin-bitstring", version = Versions.tonKotlin)
    implementation(group = Packages.tonKotlin, name = "ton-kotlin-bigint", version = Versions.tonKotlin)

    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:${Versions.collections}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinx_serialization}")

    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")

    api(group = Packages.ksmtBv2Int, name = "ksmt-core", version = Versions.ksmtBv2Int)
    implementation(group = Packages.ksmtBv2Int, name = "ksmt-bv2int", version = Versions.ksmtBv2Int)
    implementation(group = Packages.ksmtBv2Int, name = "ksmt-yices", version = Versions.ksmtBv2Int)
    implementation(group = Packages.ksmtBv2Int, name = "ksmt-z3", version = Versions.ksmtBv2Int)

    // todo: remove ksmt-core exclude after upgrading ksmt version in USVM
    api(group = Packages.usvm, name = "usvm-core", version = Versions.usvm) {
        exclude(group = "io.ksmt")
    }
}

val pathToSpec = File(rootProject.projectDir, "tvm-spec/cp0.json")

tasks.processResources {
    from(pathToSpec)
}
