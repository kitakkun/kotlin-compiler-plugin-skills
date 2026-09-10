plugins {
    kotlin("jvm") version "2.4.20"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.20")
}

// Deliberately no `-Xcontext-parameters`: context parameters are stable since Kotlin 2.4.0,
// so the context-parameter `check` override below must compile without the flag.
