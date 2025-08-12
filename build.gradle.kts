allprojects {
    version = "1.0"
    group = "org.usvm"
}

subprojects {
    // Apply the Java plugin to every subproject.
    plugins.apply("java")

    // Configure Java settings using the JavaPluginExtension.
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        withSourcesJar()
        withJavadocJar()
    }

    // Conditionally apply the maven-publish plugin and configure publication only for selected modules.
    if (project.name in listOf("tsa-core", "tsa-jettons", "tsa-metrics", "tsa-networking", "tsa-sarif", "tsa-test-gen", "tsa-test")) {
        plugins.apply("maven-publish")
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    groupId = project.group.toString()
                    artifactId = project.name
                    version = project.version.toString()
                    from(components["java"])
                }
            }
        }
    }
}