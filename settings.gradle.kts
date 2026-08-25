pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "LevelledMobs-Parent"

include(":levelledmobs-plugin")
include(":levelledmobs-api")
include(":levelledmobs-fabric")
include(":levelledmobs-apotheosis")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
