---
name: gradle-plugin-integration
description: Package a Kotlin compiler plugin as a proper Gradle plugin (not the raw -Xplugin= harness from compiler-plugin-bootstrap) so consumers apply it with `plugins { id("com.example.myplugin") }`, the IDE picks it up, and it works across multiplatform targets. Covers KotlinCompilerPluginSupportPlugin, getPluginArtifact, applyToCompilation, the Gradle plugin DSL `plugins {}` block, java-gradle-plugin block, and Maven Central publication of both the compiler plugin and the Gradle plugin. Read compiler-plugin-bootstrap first. NOT a tutorial on Gradle plugin authoring basics.
---

# Gradle Plugin Integration

The bootstrap scaffold uses raw `-Xplugin=` injection — fine for experimentation, wrong for distribution. To ship a plugin, package a Gradle plugin that:

1. Provides a stable plugin id (`com.example.myplugin`)
2. Wires the compiler plugin through `KotlinCompilerPluginSupportPlugin` (the official path)
3. Makes the IntelliJ Kotlin plugin discover and run your plugin in the IDE
4. Works for JVM, JS, Native, Wasm, and multiplatform targets uniformly

Real-world references:

- `kotlin/libraries/tools/kotlin-allopen/`
- `kotlin/libraries/tools/kotlin-noarg/`
- `kotlin/libraries/tools/kotlin-power-assert/`
- `kotlin/libraries/tools/kotlin-serialization/`

Each has a `src/common/kotlin/.../*Subplugin.kt` that subclasses `KotlinCompilerPluginSupportPlugin`.

## Module layout

Production plugins typically split:

```
my-plugin/
├── plugin/                    # the compiler plugin JAR (CompilerPluginRegistrar, FIR, IR)
├── plugin-embeddable/         # shaded variant for kotlin-compiler-embeddable consumers
├── gradle-plugin/             # the Gradle plugin (this skill)
└── annotations/               # user-facing annotations the consumer applies
```

`annotations/` is a regular Kotlin/JVM library that the consumer adds to their compile classpath (so they can write `@MyMarker` in source). The Gradle plugin transitively brings it in.

## gradle-plugin/build.gradle.kts

```kotlin
plugins {
    `java-gradle-plugin`
    `kotlin-dsl`              // for build script convenience; optional
    kotlin("jvm") version "2.3.21"
    `maven-publish`
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin-api:2.3.21")
}

gradlePlugin {
    plugins {
        create("myCompilerPlugin") {
            id = "com.example.myplugin"
            displayName = "My Kotlin Compiler Plugin"
            description = "Does X to Kotlin code"
            implementationClass = "com.example.myplugin.gradle.MyGradleSubplugin"
        }
    }
}
```

`kotlin-gradle-plugin-api` exposes `KotlinCompilerPluginSupportPlugin`. `compileOnly` is correct — consumers bring their own Kotlin Gradle plugin.

## The subplugin class

```kotlin
package com.example.myplugin.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class MyGradleSubplugin : KotlinCompilerPluginSupportPlugin {

    override fun apply(target: Project) {
        // Optional: register an extension for user configuration
        target.extensions.create("myPlugin", MyPluginExtension::class.java)
        // Add user-facing annotations to consumer's compile classpath
        target.dependencies.add(
            "implementation",
            "com.example:myplugin-annotations:0.1.0",
        )
    }

    override fun getCompilerPluginId(): String = "com.example.myplugin"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "com.example",
        artifactId = "myplugin-compiler-embeddable",  // the embeddable (shaded) compiler plugin JAR
        version = "0.1.0",
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val ext = project.extensions.getByType(MyPluginExtension::class.java)
        return project.provider {
            listOf(
                SubpluginOption(key = "enabled", value = ext.enabled.get().toString()),
                // … one SubpluginOption per CliOption your CommandLineProcessor exposes
            )
        }
    }
}

abstract class MyPluginExtension {
    abstract val enabled: org.gradle.api.provider.Property<Boolean>
}
```

Four abstract members (plus the inherited `apply(target: Project)` from `Plugin<Project>`):

| Member | Purpose |
|---|---|
| `getCompilerPluginId()` | Must match `CompilerPluginRegistrar.pluginId` and `CommandLineProcessor.pluginId`. |
| `getPluginArtifact()` | Maven coordinates of the compiler plugin JAR. Real plugins universally return the **embeddable** (shaded) variant — see "Embeddable variant" below. |
| `isApplicable(compilation)` | Filter by target / source set. Return `true` to apply everywhere. |
| `applyToCompilation(compilation)` | Returns a list of `SubpluginOption` (key=value pairs forwarded to your `CommandLineProcessor.processOption`). |

`SubpluginArtifact`'s `version` parameter defaults to `null` — when omitted, the Kotlin Gradle plugin substitutes its own version. Hardcoding `"0.1.0"` (as in the example) works but isn't required; many real plugins omit it.

The Kotlin Gradle plugin then synthesises `-Xcompiler-plugin=<id>:<jar>:` and `-P plugin:<id>:key=value` arguments for each compile task automatically.

## Consumer usage

```kotlin
// consumer's build.gradle.kts
plugins {
    kotlin("jvm") version "2.3.21"
    id("com.example.myplugin") version "0.1.0"
}

myPlugin {
    enabled = true
}
```

The consumer doesn't need to know about `-Xplugin=`, JAR paths, or service files. The Gradle plugin and the Kotlin Gradle plugin coordinate the wiring.

## Publishing

```kotlin
publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            // Gradle plugin development plugin auto-generates the plugin descriptor
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri("https://central.sonatype.com/api/v1/publisher/upload")
            credentials { /* ... */ }
        }
    }
}
```

You publish three artefacts to Maven Central:

1. `com.example:myplugin-compiler:0.1.0` (the compiler plugin JAR)
2. `com.example:myplugin-annotations:0.1.0` (user-facing annotations)
3. `com.example:myplugin-gradle:0.1.0` + plugin descriptor (the Gradle plugin)

For publication to the Gradle Plugin Portal so `id("com.example.myplugin")` resolves from `gradlePluginPortal()` directly, use the `com.gradle.plugin-publish` plugin (1.x). It reads metadata from the standard `gradlePlugin { ... }` block (set `website` and `vcsUrl` there). The older `pluginBundle { ... }` block from `com.gradle.plugin-publish` 0.x is **deprecated and dropped in 1.x** — do not add it to new builds.

## Embeddable variant

The Kotlin compiler used inside Gradle (kotlinc daemon, Build Tools API) and inside Kotlin/Native compiles against `kotlin-compiler-embeddable` — a JAR where IntelliJ classes are relocated to `org.jetbrains.kotlin.com.intellij.*`. A compiler plugin built against the un-shaded `kotlin-compiler` references unrelocated `com.intellij.*` symbols and fails at load time with `NoClassDefFoundError`.

Real plugins (allopen, noarg, parcelize, kotlinx-serialization, power-assert) **always return the embeddable variant** from `getPluginArtifact()` — there is no separate "for native" hook. The Gradle plugin points all targets (JVM, Native, JS, Wasm) at the same shaded JAR.

Build the embeddable JAR via the `com.gradleup.shadow` plugin. Shadow does **not** create an `embedded` configuration for you — declare one explicitly and feed it via `tasks.shadowJar { configurations = listOf(...) }`:

```kotlin
// plugin-embeddable/build.gradle.kts
plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "8.3.5"
}

val embedded by configurations.creating
configurations.named("compileOnly") { extendsFrom(embedded) }

dependencies {
    embedded(project(":plugin")) {
        isTransitive = false
    }
}

tasks.shadowJar {
    archiveClassifier = ""
    configurations = listOf(embedded)
    relocate("com.intellij", "org.jetbrains.kotlin.com.intellij")
    // No ASM relocation: kotlin-compiler-embeddable already exposes ASM at
    // `org.jetbrains.kotlin.org.objectweb.asm`. Relocating it again would double-prefix.
}
```

Publish this JAR as `myplugin-compiler-embeddable` and reference it from `getPluginArtifact()`. (When `java-gradle-plugin` is also applied to the publishing module, Gradle auto-creates a `pluginMaven` publication; don't re-create one with `publishing { publications { create("pluginMaven") { ... } } }` — that conflicts.)

## Checking that IDE picks up the plugin

After `./gradlew :consumer:build`, open IntelliJ. The IDE's Kotlin plugin reads the Gradle model and discovers your plugin's id. In-IDE highlighting (red squiggles for diagnostics, code completion for synthesised members) should work just like compilation. If it doesn't:

1. File | Invalidate Caches and restart (the IDE's Kotlin plugin caches Gradle plugin discovery)
2. Verify the consumer's Gradle build sees the plugin id (`./gradlew :consumer:dependencies`)
3. Check IDE log: Help | Show Log → look for "compiler plugin" lines

## Common gotchas

### `getCompilerPluginId()` differs from registrar's `pluginId`

The Gradle plugin emits `-P plugin:<id>:key=value`. The `<id>` here is the **compiler plugin id** — what `CompilerPluginRegistrar.pluginId` and `CommandLineProcessor.pluginId` declare. These three names — `getCompilerPluginId()`, `CompilerPluginRegistrar.pluginId`, `CommandLineProcessor.pluginId` — must match exactly. Use the `MyPluginNames.PLUGIN_ID` shared-constant pattern from `compiler-plugin-bootstrap` (under "Shared plugin ID constant") so they can't drift.

The **Gradle plugin id** (the `id` you write in `gradlePlugin { plugins { create(...) { id = "..." } } }`, and what users put in their `plugins {}` block) is a **separate** identifier and is usually different by convention. JetBrains' own plugins demonstrate this: `kotlin-allopen` registers Gradle plugin id `org.jetbrains.kotlin.plugin.allopen` while its compiler plugin id (returned from `getCompilerPluginId()`) is `org.jetbrains.kotlin.allopen`.

### `applyToCompilation` returns options the registrar doesn't read

`SubpluginOption(key="enabled", value="true")` only has effect if your `CommandLineProcessor.processOption` knows the `enabled` option. Mismatches are silently dropped — the build runs without applying the option.

### Plugin doesn't work for `kotlin("multiplatform")`

`isApplicable(compilation)` is per-compilation; with multiplatform you have many compilations (`commonMain`, `jvmMain`, `nativeMain`, etc.). Returning `true` applies to all; filter if you need to limit:

```kotlin
override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
    kotlinCompilation.platformType == KotlinPlatformType.jvm
```

### `getPluginArtifact()` returns the un-shaded JAR

If you accidentally publish and reference the un-shaded `myplugin-compiler` JAR (the one built directly from your plugin module), it references unrelocated `com.intellij.*` and `org.jetbrains.org.objectweb.asm` symbols. The Kotlin compiler daemon will fail to load it with `NoClassDefFoundError`. Always publish and reference the embeddable (shaded) variant.

### Maven coordinate version drift

`getPluginArtifact().version` must match what's actually published. Hardcoding `"0.1.0"` is fragile across versioning. Read the version from a Gradle property or a `kotlin-version` file at runtime.

### IDE shows old plugin behaviour

The IDE caches Gradle plugin discovery aggressively. After publishing a new version, consumers need to re-sync (Gradle | Reload All Gradle Projects in IntelliJ). Bumping the plugin version helps; same-version updates require explicit cache invalidation.

### Conflict with another plugin's id

Use a unique reverse-DNS plugin id (`com.example.myplugin`, not `myplugin`). Plugin id collisions on the Gradle Plugin Portal are first-come-first-served.

### `kotlin-gradle-plugin-api` version drift across consumers

Your Gradle plugin compiles against Kotlin 2.3.21 KGP API, but consumers may be on 2.0.x or older. The KGP API has occasional breaking changes between minor versions. For broad compatibility:

- Compile against the **oldest** KGP API you intend to support (e.g. 2.1.20)
- At runtime, your code must work against newer KGP versions too — the API is forward-compatible if you don't use methods added in newer versions

Or maintain a multi-version source set (see `multi-version-kotlin-support`).

## Relation to other skills

- **Foundation** → `compiler-plugin-bootstrap`
- **What you're packaging** → all `fir-*` and `ir-*` skills
- **Multiple Kotlin versions** → `multi-version-kotlin-support`
- **Distribution beyond Maven Central** → out of scope; consult Gradle Plugin Portal docs

## What this skill does NOT cover

- Custom Gradle plugin extensions beyond a simple boolean (use Gradle's `Property<T>` and `ListProperty<T>`)
- KSP integration (KSP is its own ecosystem)
- Composite builds
- Build cache / configuration cache compatibility (your Gradle plugin must follow standard configuration-cache rules)
