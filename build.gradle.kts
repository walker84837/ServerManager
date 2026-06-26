import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.shadow)
    java
}

group = "org.winlogon.servermanager"

extra["projectDescription"] = "A server management utility"
extra["minecraftBase"] = "1.21.11"

val providers = project.providers

val projectNameProvider = providers.provider { project.rootProject.name }
val projectVersionProvider = providers.provider { project.version.toString() }
val projectGroupProvider = providers.provider { project.group.toString() }
val projectDescriptionExtraProvider = providers.provider { project.rootProject.extra["projectDescription"] as String }
val minecraftBaseExtraProvider = providers.provider { project.rootProject.extra["minecraftBase"] as String }

val timestampProvider = providers.provider {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HHmmss'Z'").apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    sdf.format(Date())
}

val shortVersionProvider = providers.provider {
    if (!project.hasProperty("ver")) {
        return@provider null
    }

    val ver: String = project.property("ver") as String
    if (ver.startsWith("v")) {
        ver.substring(1).uppercase()
    } else {
        ver.uppercase()
    }
}

version = if (project.hasProperty("ver")) {
    val ver = project.property("ver") as String
    val shortVer = if (ver.startsWith("v")) ver.substring(1) else ver
    if (shortVer.contains("-RC-")) {
        shortVer.substringBefore("-RC-") + "-SNAPSHOT"
    } else {
        shortVer.uppercase()
    }
} else {
    timestampProvider.get() + "-SNAPSHOT"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    compileOnly(libs.paper.api)

    compileOnly(libs.jresult)
    compileOnly(libs.configlib.paper)
    compileOnly(libs.oshi.core)
    compileOnly(libs.quartz)
    compileOnly(libs.lombok)
    compileOnly(libs.asynccraftr)
    compileOnly(libs.jackson.jq)

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

val downloadGplLicense by tasks.registering {
    description = "Downloads the GPL license to the root of the final JAR (compliance with LGPL-3.0)"

    val outputFile: File = layout.buildDirectory.file("gplv3/LICENSE-GPLv3").get().asFile
    outputs.file(outputFile)

    doLast {
        outputFile.parentFile.mkdirs()

        var lastError: Exception? = null

        repeat(3) { attempt ->
            try {
                ant.withGroovyBuilder {
                    "get"(
                        "src" to "https://www.gnu.org/licenses/gpl-3.0.txt",
                        "dest" to outputFile,
                        "skipexisting" to true
                    )
                }
                return@doLast
            } catch (e: Exception) {
                lastError = e
                if (attempt < 2) {
                    logger.warn(
                        "Failed to download GPL license (attempt ${attempt + 1}/3), " +
                            "retrying..."
                    )
                    Thread.sleep(1000)
                }
            }
        }

        throw lastError ?: RuntimeException("Failed to download GPL license")
    }
}

tasks.processResources {
    dependsOn(downloadGplLicense)

    from(rootProject.file("LICENSE")) {
        into(".")
    }

    from(layout.buildDirectory.dir("gplv3")) {
        include("LICENSE-GPLv3")
    }

    val taskProjectNameProvider = providers.provider { project.rootProject.name }
    val taskProjectVersionProvider = providers.provider { project.version.toString() }
    val taskProjectGroupProvider = providers.provider { project.group.toString() }
    val taskProjectDescriptionExtraProvider = providers.provider { project.rootProject.extra["projectDescription"] as String }
    val taskMinecraftBaseExtraProvider = providers.provider { project.rootProject.extra["minecraftBase"] as String }

    filesMatching("**/paper-plugin.yml") {
        expand(mapOf(
            "NAME" to taskProjectNameProvider.get(),
            "VERSION" to taskProjectVersionProvider.get(),
            "PACKAGE" to taskProjectGroupProvider.get(),
            "DESCRIPTION" to taskProjectDescriptionExtraProvider.get(),
            "MCVERSION" to taskMinecraftBaseExtraProvider.get(),
        ))
    }
}

tasks.named<ShadowJar>("shadowJar") {
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
