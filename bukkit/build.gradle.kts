plugins {
    id("java")
    id("com.gradleup.shadow")
    id("xyz.jpenilla.run-paper")
}

version = rootProject.version

dependencies {
    implementation(project(":api"))
    implementation(project(":common"))

    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    archiveFileName.set("ChestFind-${archiveVersion.get()}.jar")
    manifest {
        attributes("Manifest-Version" to "1.0")
    }
}

tasks.runServer {
    minecraftVersion("1.21.11")
}

tasks.shadowJar {
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")

    // Merge service files
    mergeServiceFiles()

    // Exclude DJL - loaded via Paper library loader
    exclude("ai/djl/**")
}
