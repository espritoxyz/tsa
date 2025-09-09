plugins {
    id("tsa.kotlin-conventions")
    id("org.jmailen.kotlinter") version Versions.kotlinterPluginVersion
}

dependencies {
    implementation(project(":tsa-core"))

    implementation(group = Packages.tvmDisasm, name = "tvm-opcodes", version = Versions.tvmDisasm)

    implementation("org.ton:ton-kotlin-crypto:0.3.1")
    implementation("org.ton:ton-kotlin-tvm:0.3.1")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation(group = "org.slf4j", name = "slf4j-simple", version = Versions.slf4j)
}

tasks.register("checkMetrics") {
    doLast {
        javaexec {
            classpath = sourceSets.main.get().runtimeClasspath
            mainClass = "org.ton.DumpTsaUnsupportedInstructionsKt"
        }
    }
}

tasks.register("formatAndLintAll") {
    group = "formatting"

    dependsOn(tasks.findByName("formatKotlin"))
    dependsOn(tasks.findByName("lintKotlin"))
}
