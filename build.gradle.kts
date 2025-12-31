import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

plugins {
    alias(libs.plugins.shadow)
    java
}

group = "org.winlogon.servermanager"


fun getTime(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HHmmss'Z'").apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return sdf.format(Date()).toString()
}

val shortVersion: String? = if (project.hasProperty("ver")) {
    val ver: String = project.property("ver") as String
    if (ver.startsWith("v")) {
        ver.substring(1).uppercase()
    } else {
        ver.uppercase()
    }
} else {
    null
}

version = when {
    shortVersion.isNullOrBlank() -> "${getTime()}-SNAPSHOT"
    shortVersion.contains("-RC-") -> shortVersion.substringBefore("-RC-") + "-SNAPSHOT"
    else -> shortVersion
}

val minecraftBase = rootProject.extra["minecraftBase"] as String
val minecraftPatch = rootProject.extra["minecraftPatch"] as String
val projectDescription = rootProject.extra["projectDescription"] as String

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

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

dependencies {
    compileOnly(libs.paper.api)

    compileOnly(libs.jresult)
    compileOnly(libs.configlib.paper)
    compileOnly(libs.oshi.core)
    compileOnly(libs.quartz)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testImplementation(libs.paper.api)
    testImplementation(libs.adventure.api)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("**/paper-plugin.yml") {
        expand(mapOf(
            "NAME" to rootProject.name,
            "VERSION" to version,
            "PACKAGE" to project.group.toString(),
            "DESCRIPTION" to projectDescription,
            "MCVERSION" to minecraftBase,
        ))
    }
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    minimize()
}

tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.register("printProjectName") {
    doLast {
        println(rootProject.name)
    }
}

tasks.register("release") {
    dependsOn(tasks.build)

    doLast {
        if (!version.toString().endsWith("-SNAPSHOT")) {
            val shadowJarFile = tasks.shadowJar.get().archiveFile.get().asFile
            val newFile = layout.buildDirectory.file("libs/${rootProject.name}.jar").get().asFile
            shadowJarFile.renameTo(newFile)
        }
    }
}
