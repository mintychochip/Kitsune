pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
    }
}

rootProject.name = "ChestFind"

include("api")
include("common")
include("bukkit")
include("fabric")
