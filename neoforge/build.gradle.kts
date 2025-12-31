plugins {
    id("java")
    id("net.neoforged.gradle.userdev") version "7.0.120"
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":common"))
    implementation("net.neoforged:neoforge:20.6.119")
}

runs {
    configureEach {
        workingDirectory(project.file("run"))
    }
    create("client") { }
    create("server") { }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
