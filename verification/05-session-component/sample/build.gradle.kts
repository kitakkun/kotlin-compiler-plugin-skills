import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.21"
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("MainKt")
}

val compilerPlugin: Configuration by configurations.creating

dependencies {
    compilerPlugin(project(":plugin"))
}

tasks.withType<KotlinCompile>().configureEach {
    inputs.files(compilerPlugin)
    compilerOptions.freeCompilerArgs.add(
        compilerPlugin.elements.map { files ->
            "-Xplugin=${files.first().asFile.absolutePath}"
        }
    )
    compilerOptions.freeCompilerArgs.add("-Xrender-internal-diagnostic-names")
}
