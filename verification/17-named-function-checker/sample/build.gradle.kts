import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.20"
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
    // Render the factory name (NO_SHOUTING) next to the message so the PASS grep can match it.
    compilerOptions.freeCompilerArgs.add("-Xrender-internal-diagnostic-names")
}
