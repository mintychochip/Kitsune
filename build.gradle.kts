plugins {
    id("com.gradleup.shadow") version "8.3.5" apply false
    id("xyz.jpenilla.run-paper") version "2.3.1" apply false
}

group = "org.aincraft"
version = "1.0-SNAPSHOT"

subprojects {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    pluginManager.withPlugin("java") {
        configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        }

        tasks.withType<JavaCompile> {
            options.encoding = "UTF-8"
            options.release.set(21)
        }

        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }
}
