import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.21"
}

kotlin {
    jvmToolchain(21)
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
}
