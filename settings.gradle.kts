rootProject.name = "ServerManager"

extra["minecraftBase"] = "1.21"
extra["minecraftPatch"] = "${extra["minecraftBase"]}.10"
extra["projectDescription"] = "A Minecraft plugin to allow server admins to manage servers within containerized environments."

gradle.beforeProject {
    if (this == rootProject) {
        extra["minecraftBase"] = settings.extra["minecraftBase"] as String
        extra["minecraftPatch"] = settings.extra["minecraftPatch"] as String
        extra["projectDescription"] = settings.extra["projectDescription"] as String
    }
}