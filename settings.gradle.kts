pluginManagement {
    repositories {
        maven("https://maven.neoforged.net/releases")
        gradlePluginPortal()
    }
}

rootProject.name = "ChestFind"

include("common")
include("bukkit")
include("neoforge")
