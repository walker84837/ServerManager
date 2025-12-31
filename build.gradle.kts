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
extra["minecraftBase"] = "1.21.10"

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

version = shortVersionProvider.flatMap { shortVer ->
    if (shortVer.isNullOrBlank()) {
        return@flatMap timestampProvider.map { timestamp -> "$timestamp-SNAPSHOT" }
    }

    project.providers.provider {
        if (shortVer.contains("-RC-")) {
            shortVer.substringBefore("-RC-") + "-SNAPSHOT"
        } else {
            shortVer
        }
    }
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
