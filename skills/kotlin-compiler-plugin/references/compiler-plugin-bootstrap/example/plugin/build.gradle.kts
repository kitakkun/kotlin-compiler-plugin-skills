plugins {
    kotlin("jvm") version "2.4.10"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.10")
}
