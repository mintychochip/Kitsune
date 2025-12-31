pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases")
        gradlePluginPortal()
    }
}

rootProject.name = "Kitsune"

include("api")
include("common")
include("bukkit")
include("fabric")
include("neoforge")
