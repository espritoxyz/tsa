plugins {
    id("tsa.kotlin-conventions")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
}

dependencies {
    implementation(project(":tsa-core"))

    implementation(group = Packages.tonKotlin, name = "ton-kotlin-hashmap-tlb", version = Versions.tonKotlin)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinx_serialization}")
}
