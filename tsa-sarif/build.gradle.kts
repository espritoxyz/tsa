plugins {
    id("tsa.kotlin-conventions")
}

dependencies {
    implementation(group = Packages.tvmDisasm, name = "tvm-opcodes", version = Versions.tvmDisasm)
    implementation(group = Packages.tvmDisasm, name = "tvm-disasm", version = Versions.tvmDisasm)

    implementation(project(":tsa-core"))

    // https://mvnrepository.com/artifact/io.github.detekt.sarif4k/sarif4k
    implementation("io.github.detekt.sarif4k:sarif4k:0.6.0")
}
