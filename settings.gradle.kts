rootProject.name = "ServerManager"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven {
            name = "papermc"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
        maven {
            name = "minecraft"
            url = uri("https://libraries.minecraft.net")
            content {
                includeModule("com.mojang", "brigadier")
            }
        }
        maven("https://maven.winlogon.org/releases")
        maven("https://repo.codemc.org/repository/maven-public/")
        maven("https://jitpack.io")
    }
}

extra["minecraftBase"] = "1.21"
extra["minecraftPatch"] = "${extra["minecraftBase"]}.11"
extra["projectDescription"] = "A Minecraft plugin to allow server admins to manage servers within containerized environments."

gradle.beforeProject {
    if (this == rootProject) {
        extra["minecraftBase"] = settings.extra["minecraftBase"] as String
        extra["minecraftPatch"] = settings.extra["minecraftPatch"] as String
        extra["projectDescription"] = settings.extra["projectDescription"] as String
    }
}