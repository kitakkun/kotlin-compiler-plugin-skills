plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "12-cross-module-ir-visibility"

include("plugin", "module-a", "module-b")
