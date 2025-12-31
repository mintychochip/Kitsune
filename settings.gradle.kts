pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
    }
}

rootProject.name = "Kitsune"

include("api")
include("common")
include("bukkit")
include("fabric")
