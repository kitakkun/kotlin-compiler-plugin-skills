# Evidence Dossier: gradle-plugin-integration

Citations against `/Users/kitakkun/Documents/GitHub/kotlin-lang/`. Paths use `kotlin/<path>:line` form.

## Interface and Core Types

### Claim: `KotlinCompilerPluginSupportPlugin` is an interface that extends `Plugin<Project>`, declaring 4 abstract members and inheriting `apply(target: Project)` from `Plugin<Project>` (with a default `Unit` implementation provided in the interface body).
**File**: [`kotlin/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt:192`](https://github.com/JetBrains/kotlin/blob/v2.3.21/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt#L192)
**Snippet**:
```
interface KotlinCompilerPluginSupportPlugin : Plugin<Project> {
    override fun apply(target: Project) = Unit
    fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean
    fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>>
    fun getCompilerPluginId(): String
    fun getPluginArtifact(): SubpluginArtifact
}
```

### Claim: `getCompilerPluginId(): String` signature.
**File**: [`kotlin/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt:226`](https://github.com/JetBrains/kotlin/blob/v2.3.21/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt#L226)

### Claim: `getPluginArtifact(): SubpluginArtifact` signature.
**File**: [`kotlin/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt:234`](https://github.com/JetBrains/kotlin/blob/v2.3.21/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt#L234)

### Claim: `isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean` signature.
**File**: [`kotlin/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt:206`](https://github.com/JetBrains/kotlin/blob/v2.3.21/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt#L206)

### Claim: `applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>>` signature.
**File**: [`kotlin/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt:216`](https://github.com/JetBrains/kotlin/blob/v2.3.21/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt#L216)

### Claim: `SubpluginArtifact(groupId: String, artifactId: String, version: String? = null)` constructor — `version` defaults to `null`.
**File**: [`kotlin/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt:263`](https://github.com/JetBrains/kotlin/blob/v2.3.21/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt#L263)
**Snippet**:
```
open class SubpluginArtifact(val groupId: String, val artifactId: String, val version: String? = null)
```

### Claim: `SubpluginOption(key: String, value: String)` is a secondary constructor wrapping a `Lazy<String>`.
**File**: [`kotlin/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt:44`](https://github.com/JetBrains/kotlin/blob/v2.3.21/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt#L44)
**Snippet**:
```
open class SubpluginOption(val key: String, private val lazyValue: Lazy<String>) {
    constructor(key: String, value: String) : this(key, lazyOf(value))
    ...
}
```

## Real Plugins Universally Use the Embeddable JAR via `getPluginArtifact()`

Every real-world reference plugin returns a single `SubpluginArtifact` from `getPluginArtifact()` for all targets (JVM, Native, JS, Wasm). There is no per-platform branching.

### Claim: `AllOpenSubplugin` returns a single `SubpluginArtifact` (`JetBrainsSubpluginArtifact` extends `SubpluginArtifact`).
**File**: [`kotlin/libraries/tools/kotlin-allopen/src/common/kotlin/org/jetbrains/kotlin/allopen/gradle/AllOpenSubplugin.kt:68`](https://github.com/JetBrains/kotlin/blob/v2.3.21/libraries/tools/kotlin-allopen/src/common/kotlin/org/jetbrains/kotlin/allopen/gradle/AllOpenSubplugin.kt#L68)
**Snippet**:
```
override fun getPluginArtifact(): SubpluginArtifact =
    JetBrainsSubpluginArtifact(artifactId = ALLOPEN_ARTIFACT_NAME)
```

### Claim: `NoArgSubplugin` returns a single `SubpluginArtifact` for all targets.
**File**: [`kotlin/libraries/tools/kotlin-noarg/src/common/kotlin/org/jetbrains/kotlin/noarg/gradle/NoArgSubplugin.kt:72`](https://github.com/JetBrains/kotlin/blob/v2.3.21/libraries/tools/kotlin-noarg/src/common/kotlin/org/jetbrains/kotlin/noarg/gradle/NoArgSubplugin.kt#L72)
**Snippet**:
```
override fun getPluginArtifact(): SubpluginArtifact =
    JetBrainsSubpluginArtifact(artifactId = NOARG_ARTIFACT_NAME)
```

### Claim: `PowerAssertGradlePlugin` returns a single `SubpluginArtifact` for all targets.
**File**: [`kotlin/libraries/tools/kotlin-power-assert/src/common/kotlin/org/jetbrains/kotlin/powerassert/gradle/PowerAssertGradlePlugin.kt:65`](https://github.com/JetBrains/kotlin/blob/v2.3.21/libraries/tools/kotlin-power-assert/src/common/kotlin/org/jetbrains/kotlin/powerassert/gradle/PowerAssertGradlePlugin.kt#L65)
**Snippet**:
```
override fun getPluginArtifact(): SubpluginArtifact =
    JetBrainsSubpluginArtifact(POWER_ASSERT_ARTIFACT_NAME)
```

### Claim: `SerializationSubplugin` returns a single `SubpluginArtifact` for all targets.
**File**: [`kotlin/libraries/tools/kotlin-serialization/src/common/kotlin/org/jetbrains/kotlinx/serialization/gradle/SerializationSubplugin.kt:37`](https://github.com/JetBrains/kotlin/blob/v2.3.21/libraries/tools/kotlin-serialization/src/common/kotlin/org/jetbrains/kotlinx/serialization/gradle/SerializationSubplugin.kt#L37)
**Snippet**:
```
override fun getPluginArtifact(): SubpluginArtifact =
    SubpluginArtifact(SERIALIZATION_GROUP_NAME, SERIALIZATION_ARTIFACT_NAME)
```

## Gradle Plugin Id ≠ Compiler Plugin Id

### Claim: For `kotlin-allopen`, the Gradle plugin id is `org.jetbrains.kotlin.plugin.allopen` (registered in `gradlePlugin { plugins { create(...) { id = ... } } }`).
**File**: [`kotlin/libraries/tools/kotlin-allopen/build.gradle.kts:14`](https://github.com/JetBrains/kotlin/blob/v2.3.21/libraries/tools/kotlin-allopen/build.gradle.kts#L14)
**Snippet**:
```
create("kotlinAllopenPlugin") {
    id = "org.jetbrains.kotlin.plugin.allopen"
    ...
    implementationClass = "org.jetbrains.kotlin.allopen.gradle.AllOpenGradleSubplugin"
```

### Claim: For `kotlin-allopen`, the compiler plugin id (returned by `getCompilerPluginId()`) is `org.jetbrains.kotlin.allopen` — different from the Gradle plugin id above.
**File**: [`kotlin/libraries/tools/kotlin-allopen/src/common/kotlin/org/jetbrains/kotlin/allopen/gradle/AllOpenSubplugin.kt:67`](https://github.com/JetBrains/kotlin/blob/v2.3.21/libraries/tools/kotlin-allopen/src/common/kotlin/org/jetbrains/kotlin/allopen/gradle/AllOpenSubplugin.kt#L67)
**Snippet**:
```
override fun getCompilerPluginId() = "org.jetbrains.kotlin.allopen"
```

The two ids differ by the `.plugin.` infix segment — a JetBrains naming convention. They are distinct and must not be conflated.

## `com.intellij` → `org.jetbrains.kotlin.com.intellij` Relocation

### Claim: The Compose compiler plugin's embeddable build relocates `com.intellij` to `org.jetbrains.kotlin.com.intellij` via the shadow-style `runtimeJarWithRelocation` block.
**File**: [`kotlin/plugins/compose/compiler/build.gradle.kts:26`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/compose/compiler/build.gradle.kts#L26)
**Snippet**:
```
runtimeJarWithRelocation {
    relocate("com.intellij", "org.jetbrains.kotlin.com.intellij")
}
```

This confirms the relocation rule used by the Kotlin team for embeddable compiler-plugin JARs.

## Third-Party Fact (No kotlin-lang Citation)

### Claim: `pluginBundle { ... }` was the metadata block for `com.gradle.plugin-publish` 0.x and is **deprecated and removed in 1.x**. Metadata moved into the standard `gradlePlugin { ... }` block (`website`, `vcsUrl` properties).
**Source**: This is a fact about the third-party Gradle Plugin Publish plugin (`com.gradle.plugin-publish`) maintained outside the kotlin-lang repository. No kotlin-lang citation is available — the claim is documented in the Gradle Plugin Publish plugin's release notes (1.0.0, 2022) and current documentation at https://plugins.gradle.org/docs/publish-plugin. Verifiable via the plugin's own changelog rather than this repository.
