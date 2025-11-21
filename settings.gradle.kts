rootProject.name = "tsa"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

include("tsa-core")
include("tsa-cli")
include("tsa-sarif")
include("tsa-test-gen")
include("tsa-jettons")
include("tsa-test")
include("tsa-metrics")
include("tsa-benchmark")

// TODO: fix this module (https://github.com/explyt/tsa/issues/118)
// include("tsa-intellij")
include("tsa-networking")
include("tsa-safety-properties-examples")
