plugins {
    id("java")
    id("fabric-loom") version "1.9-SNAPSHOT"
    id("com.gradleup.shadow")
}

val minecraftVersion = "1.20.1"
val yarnMappings = "1.20.1+build.10"
val fabricLoaderVersion = "0.15.11"
val fabricApiVersion = "0.92.2+1.20.1"
val clothConfigVersion = "11.1.118"
val modMenuVersion = "7.2.2"

repositories {
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/releases/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // Cloth Config for config screens
    modImplementation("me.shedaniel.cloth:cloth-config-fabric:$clothConfigVersion") {
        exclude(group = "net.fabricmc.fabric-api")
    }

    // ModMenu integration
    modImplementation("com.terraformersmc:modmenu:$modMenuVersion")

    // Project dependencies
    implementation(project(":api"))
    implementation(project(":common"))

    // Include common deps in JAR
    include(project(":api"))
    include(project(":common"))
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("chestfind") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
    archiveBaseName.set("ChestFind-Fabric")
}

tasks.shadowJar {
    configurations = listOf(project.configurations.getByName("shadow"))
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    mergeServiceFiles()
    // Merge DJL and ONNX properties files
    append("ai/djl/util/djl-util.properties")
    append("ai/djl/huggingface/tokenizers/tokenizers.properties")
    append("native/lib.properties")
    archiveClassifier.set("dev-shadow")
}

tasks.remapJar {
    dependsOn(tasks.shadowJar)
    inputFile.set(tasks.shadowJar.get().archiveFile)
    archiveBaseName.set("ChestFind-Fabric")
}
