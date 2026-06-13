---
name: multi-version-kotlin-support
description: Strategies for shipping a Kotlin compiler plugin across multiple Kotlin versions. Covers (1) pinning to one version, (2) reflective access for narrow drift, (3) source preprocessor templates as in kotlinx-rpc's CSM, and (4) a compat-shim interface dispatched per-version as in Metro's CompatContext. Also covers the orthogonal distribution shapes (single fat JAR with bundled compat impls vs. per-Kotlin-version published coordinates), CI matrices, and IDE-bundled-Kotlin version quirks. Read compiler-plugin-bootstrap and gradle-plugin-integration first. NOT a guide to upgrading user code to a newer Kotlin language version.
---

# Multi-Version Kotlin Support

The Kotlin compiler plugin API is `@ExperimentalCompilerApi`. JetBrains breaks it routinely:

- **Patch versions** (2.3.20 → 2.3.21): mostly API-stable. One JAR usually works.
- **Minor versions** (2.3.x → 2.4.x): occasional breaking changes. Some plugins survive without recompilation; some don't.
- **Major versions** (2.x → 3.x): assume breaking changes.

Concrete recent breaks:

- **Kotlin 2.3** made `CompilerPluginRegistrar.pluginId` abstract — plugins built against 2.2.x without a `pluginId` override fail to load.
- **Kotlin 2.3.20** renamed `FirSimpleFunction` → `FirNamedFunction` (and `FirSimpleFunctionBuilder` → `FirNamedFunctionBuilder`). Source-level rename, runtime linkage failure for plugins built against the old name.
- **Kotlin 2.3.0** added a `containingFileName` parameter to `FirExtension.createTopLevelFunction`; older callers need a compat shim.
- **Kotlin 2.2** ([KT-68003](https://youtrack.jetbrains.com/issue/KT-68003)) collapsed `IrMemberAccessExpression`'s `dispatchReceiver`/`extensionReceiver`/`valueArguments` into a single flat `arguments: ValueArgumentsList`. Plugins that produce or transform `IrCall` need divergent code per Kotlin minor.
- **Kotlin 2.4** completed the KT-68003 migration by **removing** the deprecated IR accessors `IrMemberAccessExpression.extensionReceiver` / `valueArgumentsCount` / `getValueArgument` / `putValueArgument` and `IrFunction.valueParameters` / `extensionReceiverParameter` (only `dispatchReceiver` survives; `IrFunction.dispatchReceiverParameter` became read-only `val`). It also reworked `FirReplSnippetResolveExtension` into a `FirExtensionSessionComponent` (so it left `FirExtensionRegistrar.AVAILABLE_EXTENSIONS`, which dropped 18 → 17 entries), and `IrGeneratedDeclarationsRegistrar` now takes `List<IrAnnotation>` rather than `IrConstructorCall` annotations.

If consumers use your plugin across Kotlin versions you must pick a strategy. The table below sketches the choice; the rest of the skill explains each option.

## The two orthogonal axes

Multi-version support has two independent decisions, and conflating them is the most common design mistake:

| Axis | Question | Options |
|---|---|---|
| Source organization | How is version-specific code expressed in the source tree? | (1) Pin, (2) Reflection, (3) Source preprocessor, (4) Compat-shim interface |
| Distribution shape | How is the result shipped? | (A) Single fat JAR with bundled compat impls, (B) Per-Kotlin-version coordinates, (C) Branch per Kotlin |

Strategies 3 and 4 are the **two production-quality patterns** in active use by community plugins (kotlinx-rpc and Metro respectively). They are not mutually exclusive — kotlinx-rpc combines both: CSM templates for inline drift plus a `FirVersionSpecificApi` interface for structural divergences. Distribution-wise, kotlinx-rpc ships per-Kotlin-version coordinates (B), Metro ships a fat JAR (A), JetBrains' in-tree plugins ship a branch per Kotlin (C).

Read the strategy sections below for axis 1, then the "Distribution shape" section for axis 2; the "Practical recommendation" at the end maps common situations to the resulting (strategy × distribution) pair.

## Strategy 1: Pin to one Kotlin version

The default. `plugin/build.gradle.kts`:

```kotlin
dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.0")
}
```

Document in your README: "Requires Kotlin 2.4.x". Bump as needed.

Pros: simple, fast to maintain. Cons: consumers on older Kotlin can't use you; you ship a release per Kotlin minor.

This is what JetBrains' own in-tree plugins (`allopen`, `parcelize`, `kotlinx-serialization`, `power-assert`) do — every release in `kotlin/plugins/` is pinned to whatever Kotlin version the surrounding monorepo branch ships.

Don't overengineer until you actually have users on multiple versions.

## Strategy 2: Reflective access to narrow API drift

For one or two API call sites that drift, reflection lets you keep a single source set:

```kotlin
fun callMaybeRenamedMethod(target: Any): Any? {
    val cls = target.javaClass
    val method = cls.methods.firstOrNull { it.name == "newName" }
        ?: cls.methods.firstOrNull { it.name == "oldName" }
        ?: return null
    return method.invoke(target)
}
```

Slow, brittle, type-unsafe — but cheap in build complexity. Use only for narrow drift you don't expect to grow.

A subtle pitfall: reflection sees the classes on the **embeddable** compiler classpath under their relocated package prefix (`org.jetbrains.kotlin.com.intellij.*`), while the un-shaded compiler exposes them at the original `com.intellij.*`. Method names are stable but enclosing class lookups must match the compiler artifact your plugin actually links against. Always reflect against the same compiler shape (`kotlin-compiler-embeddable` for shaded, `kotlin-compiler` for un-shaded) the rest of your plugin uses.

## Strategy 3: Source preprocessor templates (kotlinx-rpc's CSM)

Keep one logical source tree and mark version-specific sections inline. A Gradle task evaluates the directives at build time and emits a single ordinary `.kt` file per template, with only the matching block kept.

This is how [kotlinx-rpc](https://github.com/Kotlin/kotlinx-rpc) handles drift. The directives live as comments so the templates remain syntactically valid Kotlin (though only one branch is compiled per build).

The directive syntax and the snippet below are from kotlinx-rpc
(© 2023-2025 JetBrains s.r.o and contributors, Apache-2.0); see
[`NOTICE.md`](../../NOTICE.md) for full attribution.

```kotlin
// compiler-plugin-cli/src/main/templates/.../CompilerPluginRegistrar.kt
class RpcCompilerPlugin : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true

//##csm RpcCompilerPlugin.pluginId
//##csm specific=[2.1.0...2.2.99]
//##csm /specific
//##csm default
    override val pluginId: String = PLUGIN_ID
//##csm /default
//##csm /RpcCompilerPlugin.pluginId
    ...
}
```

A `specific=[range]` block is included if the active Kotlin version matches; otherwise the `default` block (if any) is. Patterns are comma-separated; each is either a closed range (`2.0.0...2.1.0`) or a prefix matcher (`2.3.0-dev-*`, `2.1.0-ij-*`).

The Kotlin version comes from a Gradle property (`-Pkotlin.compiler=2.3.21` or `KOTLIN_COMPILER_VERSION` env var). Each CI / publish job picks a version, runs the template processor, compiles the result, and produces an artifact tagged with that version (kotlinx-rpc uses the convention `$kotlinVersion-$libraryVersion`, e.g. `kotlinx-rpc-compiler-plugin:2.3.0-0.11.0`).

Pros:
- Single source tree; no module proliferation.
- Patterns can match IDE-tagged versions (`2.1.0-ij-*`) and dev builds (`2.3.0-dev-*`) directly.

Cons:
- Only one branch is type-checked per build. Errors in non-active branches lurk until that version becomes active.
- IDE doesn't understand the directives — refactoring across `//##csm` regions and step-debugging through them are awkward.
- The preprocessor is custom code you maintain.

## Strategy 4: Compat-shim interface (Metro's CompatContext)

Define an interface listing every version-volatile API call your plugin makes. Implement it once per supported Kotlin version. Dispatch to the right implementation either at build time (via dependency selection) or at runtime (via `ServiceLoader`).

This is how [Metro](https://github.com/ZacSweers/metro) handles drift. kotlinx-rpc combines this approach with Strategy 3: it uses a sibling interface `FirVersionSpecificApi` for divergences too coarse to express as inline `//##csm` blocks (e.g., when an entire helper method's signature changes), while still relying on CSM directives for one-line drift.

### Define the interface

Put every shimmable call in one place. Annotate each with **why** it exists — future-you needs to know whether the shim can be retired.

The `@CompatApi` annotation pattern, the `Reason` enum, and the `Factory`
companion-object shape below are adapted from
[Metro's `CompatContext.kt`](https://github.com/ZacSweers/metro/blob/45b38b230540497af32c8d176cbf18460cb44e19/compiler-compat/src/main/kotlin/dev/zacsweers/metro/compiler/compat/CompatContext.kt)
(© 2024-2025 Zac Sweers, Apache-2.0). See [`NOTICE.md`](../../NOTICE.md) for full attribution.

```kotlin
// compiler-compat/src/main/kotlin/.../CompatContext.kt
package com.example.compat

import org.jetbrains.kotlin.fir.extensions.FirExtension
// ...etc — fully qualified compiler imports omitted for brevity

// Default retention is BINARY — visible to bytecode-introspection tools (ASM, ProGuard)
// and to source-grep, but NOT to java.lang.Class.getAnnotations() at runtime. That's
// what we want here: the annotations are documentation for tooling and humans, not
// a runtime-queryable signal. Add @Retention(AnnotationRetention.RUNTIME) only if you
// want to inspect them via reflection.
internal annotation class CompatApi(
    val since: String,
    val reason: Reason,
    val message: String = "",
) {
    enum class Reason { DELETED, RENAMED, ABI_CHANGE, COMPAT }
}

interface CompatContext {

    // Each method below is a shim for an upstream API that drifted. Default parameter
    // values mirror the upstream API at the version named in `since=` so call-sites in
    // plugin code don't need to change when they migrate to the shim.
    @CompatApi(since = "2.3.0", reason = CompatApi.Reason.ABI_CHANGE,
        message = "containingFileName parameter was added")
    fun FirExtension.createTopLevelFunction(
        key: GeneratedDeclarationKey,
        callableId: CallableId,
        returnType: ConeKotlinType,
        containingFileName: String? = null,
        config: SimpleFunctionBuildingContext.() -> Unit = {},
    ): FirFunction

    // ...one method per drifted API — see Metro's CompatContext.kt (linked in EVIDENCE.md)
    // for ~30 real-world entries covering 2.2.20 through 2.4.0-Beta2.

    interface Factory {
        // Smallest Kotlin version this Factory supports. Kept as String — not a typed
        // KotlinToolingVersion — because the Factory class is loaded by ServiceLoader
        // before any compiler classes are available, so we can't depend on the version
        // parser at construction time. The resolver lifts the string at comparison time.
        val minVersion: String
        fun create(): CompatContext

        companion object Companion {
            private const val COMPILER_VERSION_FILE = "META-INF/compiler.version"

            fun loadCompilerVersionString(): String {
                val cl = FirExtension::class.java.classLoader
                return cl.getResourceAsStream(COMPILER_VERSION_FILE)
                    ?.bufferedReader()?.use { it.readText() }?.takeIf { it.isNotBlank() }
                    ?: error("'$COMPILER_VERSION_FILE' not on classpath — is the active compiler shaded differently than expected?")
            }
        }
    }
}
```

The interface above shows one shim method to keep the snippet compilable; in production you accumulate roughly one method per `@CompatApi`-tagged entry in EVIDENCE.md's Metro citation.

The `@CompatApi(since=..., reason=..., message=...)` pattern is doctrinal. Without it, you accumulate compat methods whose original motivation is forgotten and you can't safely retire shims as you drop old versions. A simple `grep -r '@CompatApi(since = "2.2'` later tells you exactly what's retirable when you drop 2.2 support.

### Per-version implementation modules

One Gradle subproject per Kotlin version you support, each pinned to that compiler. Module-name convention: `k` + concatenated `<major><minor><patch>` digits with no separators between the numbers, plus an underscore-prefixed lowercased qualifier for pre-releases. So `2.3.20` becomes `k2320`, `2.4.0-Beta1` becomes `k240_beta1`, `2.4.0-dev-2124` becomes `k240_dev_2124`:

```
compiler-compat/
├── build.gradle.kts            # the interface module
├── src/main/kotlin/.../CompatContext.kt
├── k2220/                       # impl for Kotlin 2.2.20+
│   ├── build.gradle.kts
│   ├── version.txt              # "2.2.20"
│   └── src/main/kotlin/.../CompatContextImpl.kt
├── k230/                        # impl for Kotlin 2.3.0+
├── k2320/                       # impl for Kotlin 2.3.20+
└── k240_beta1/                  # impl for Kotlin 2.4.0-Beta1+
```

Each impl module's `build.gradle.kts`:

```kotlin
plugins { kotlin("jvm") }

dependencies {
    // The compat impl pins the un-shaded `kotlin-compiler` (NOT `kotlin-compiler-embeddable`).
    // Reason: the impl directly references compiler internals (FirSession, IrBuilder, etc.)
    // and the test framework / runtime classpath that hosts it links against the un-shaded
    // names. This deliberately diverges from compiler-plugin-bootstrap's "always embeddable"
    // guidance, which applies to the plugin's outer shell (the Gradle plugin's
    // `kotlinCompilerPluginClasspath` consumes a single shaded JAR). Strategy 4 builds compat
    // impls separately and then bundles them into the outer shell via shadowJar; the inner
    // compile-only against `kotlin-compiler` doesn't propagate.
    val kotlinVersion =
        providers.fileContents(layout.projectDirectory.file("version.txt")).asText.map { it.trim() }
    compileOnly(kotlinVersion.map { "org.jetbrains.kotlin:kotlin-compiler:$it" })
    api(project(":compiler-compat"))
    implementation(project(":compiler-compat:k2220"))   // optional fallback chain
}
```

Each impl declares its `minVersion` via the `Factory.minVersion` field and registers itself in `META-INF/services/...CompatContext$Factory` so `ServiceLoader` finds it.

### Runtime dispatch via ServiceLoader

Read the active compiler's `META-INF/compiler.version` resource at plugin startup, pick the highest factory whose `minVersion <= active version`, and create the impl. The naive resolver is one line of filter + maxByOrNull:

```kotlin
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

fun resolveFactoryNaive(
    factories: Sequence<CompatContext.Factory> =
        java.util.ServiceLoader.load(CompatContext.Factory::class.java).asSequence(),
): CompatContext.Factory {
    val active = KotlinToolingVersion(CompatContext.Factory.Companion.loadCompilerVersionString())
    return factories
        .filter { KotlinToolingVersion(it.minVersion) <= active }
        .maxByOrNull { KotlinToolingVersion(it.minVersion) }
        ?: error("No compatible CompatContext factory for $active")
}
```

This handles the common case but has **two known footguns**:

1. **Dev builds**: `2.3.20-dev-7791` is a dev build *of* 2.3.20. `KotlinToolingVersion`'s maturity ordering puts dev < beta < stable, so the naive `<= active` filter excludes the `2.3.20` factory when running on a dev build. Production resolvers split factories into dev/non-dev tracks: prefer a dev factory for the same major.minor.patch, then fall back to non-dev with the dev classifier stripped.

2. **IDE-tagged versions** (e.g. `2.3.20-ij253-105`): the IntelliJ Kotlin plugin reports its bundled Kotlin under a tag suffix that doesn't appear in any normal release. The resolver must consult an alias table before parsing — see "IDE-bundled Kotlin version quirks" below.

The production version of the resolver, lifted from
[Metro's `CompatContext.kt`](https://github.com/ZacSweers/metro/blob/45b38b230540497af32c8d176cbf18460cb44e19/compiler-compat/src/main/kotlin/dev/zacsweers/metro/compiler/compat/CompatContext.kt)
(© 2024-2025 Zac Sweers, Apache-2.0):

```kotlin
private fun resolveFactoryForVersion(
    currentVersion: KotlinToolingVersion,
    factories: List<CompatContext.Factory>,
): CompatContext.Factory? {
    if (currentVersion.isDev) {
        val devFactories = factories.filter { KotlinToolingVersion(it.minVersion).isDev }
        findHighest(currentVersion, devFactories)?.let { return it }

        // Strip the dev classifier so 2.2.20-dev-* compares against the 2.2.20 factory.
        val nonDev = factories.filter { !KotlinToolingVersion(it.minVersion).isDev }
        val baseVersion = KotlinToolingVersion(
            currentVersion.major, currentVersion.minor, currentVersion.patch,
            maturity = null,   // null maturity == "stable, no qualifier"
        )
        return findHighest(baseVersion, nonDev)
    }
    return findHighest(currentVersion, factories.filter { !KotlinToolingVersion(it.minVersion).isDev })
}

private fun findHighest(
    currentVersion: KotlinToolingVersion,
    factories: List<CompatContext.Factory>,
): CompatContext.Factory? = factories
    .filter { currentVersion >= KotlinToolingVersion(it.minVersion) }
    .maxByOrNull { KotlinToolingVersion(it.minVersion) }
```

Use this shape when supporting dev builds; use the naive form only if you commit to never matching dev classifiers.

Pros:
- Compile-time guarantees per version: each impl is a real Gradle module compiled against its own pinned compiler. If an impl forgets a method, the build fails.
- One shipped JAR — bundle the compat modules with shadowJar, publish a single coordinate. ServiceLoader picks at runtime.
- Drift is centralized: every shim is named, dated, and motivated by `@CompatApi`.

Cons:
- Bigger upfront design surface: every drifted API needs an interface entry.
- Indirection cost: every call site goes through `with(compatContext) { ... }`.
- A misordered `minVersion` (e.g. `2.2.20` factory matching when running 2.3) is a silent footgun — pin a **CI matrix** that runs every supported version.

## Distribution shape

The source-organisation strategies above can be packaged in two distinct ways. The choice is independent of the source strategy:

### Pattern A: Single fat JAR with bundled compat impls

What Metro does. Use `shadowJar` (or equivalent) to merge the main plugin module + every `compiler-compat/k*` impl into one publication. Consumers add **one** dependency on `dev.zacsweers.metro:compiler:0.x.y` regardless of which Kotlin they use; ServiceLoader picks the right impl at runtime.

```kotlin
// gradle-plugin's KotlinCompilerPluginSupportPlugin
override fun getPluginArtifact(): SubpluginArtifact =
    SubpluginArtifact("dev.zacsweers.metro", "compiler", VERSION)
```

Pros: one publication to maintain. Consumer's Gradle plugin doesn't need to know their Kotlin version. Forward compatibility — newer Kotlins still load the highest-`minVersion` impl that matches.
Cons: JAR size bloated by all bundled impls. Compat impls compiled against unused Kotlin versions ride along.

### Pattern B: Per-Kotlin-version coordinates

What kotlinx-rpc does. Each (plugin version × Kotlin version) is published separately under a coordinate that includes both versions: `kotlinx-rpc-compiler-plugin:2.3.0-0.11.0`, `:2.3.20-0.11.0`, etc. The Gradle plugin computes the coordinate from the consumer's active Kotlin version at apply time:

```kotlin
override fun getPluginArtifact(): SubpluginArtifact {
    val kotlinVersion = project.getKotlinPluginVersion()
    return SubpluginArtifact(GROUP_ID, ARTIFACT_ID, "$kotlinVersion-$LIBRARY_VERSION")
}
```

Pros: each artifact is small and contains exactly one version's code. No ServiceLoader indirection — the matching code is the code.
Cons: N× publishing surface. Releasing a new plugin version means publishing N artifacts. Consumers on a Kotlin version you forgot to publish for get a `dependency not found` error.

### Pattern C: Per-version `getPluginArtifact()` dispatch

A simpler variant of Pattern B: ship a small, hand-curated list of coordinates rather than a generated one:

```kotlin
override fun getPluginArtifact(): SubpluginArtifact {
    val v = project.getKotlinPluginVersion().split(".").map { it.toInt() }
    return when {
        v[0] == 2 && v[1] >= 3 -> SubpluginArtifact("com.example", "myplugin-k2-2.3", "0.1.0")
        v[0] == 2 && v[1] == 2 -> SubpluginArtifact("com.example", "myplugin-k2-2.2", "0.1.0")
        else                   -> SubpluginArtifact("com.example", "myplugin-k2-2.1", "0.1.0")
    }
}
```

Pros: minimum publication count. Cons: every Kotlin minor bump requires a new artifact + new dispatch arm.

## IDE-bundled Kotlin version quirks

The IntelliJ Kotlin plugin and Android Studio bundle their own copy of the Kotlin compiler. The version string they report does not match anything you publish for:

| Source | Example version string | What it really is |
|---|---|---|
| IntelliJ IDEA 2025.3.3 | `2.3.20-ij253-105` | Roughly Kotlin `2.3.0-dev-9992` |
| Android Studio Otter (canary) | `2.2.255-dev-255` | A pre-release Kotlin used only inside the IDE |
| Android Studio Panda 4 | `2.3.255-dev-255` | Pre-release Kotlin for the next IDE drop |
| Intellij EAP | `2.4.0-ij261-32` | Kotlin `2.4.0-dev-2633` |

If your plugin runs inside the IDE (it usually does, for diagnostics), your version detection logic must recognize these tagged versions and map them to a real entry in your compat table. Concrete options:

1. **Alias file** — bundle a text file mapping tagged versions to known versions. Metro generates this from the IntelliJ Community Edition repo via a Python script (`fetch-all-ide-kotlin-versions.py` in `compiler-compat/`) that scans `idea/<intellij-version>/.idea/libraries/kotlinc_kotlin_compiler_common.xml`. The file is consulted before the matchers.
2. **Pattern matcher** — accept tag suffixes generically: a version of the form `<major>.<minor>.<patch>-ij<n>-<m>` is treated as `<major>.<minor>.<patch>` for compat matching, with the understanding that the IJ-tagged build may diverge slightly.
3. **CLI-only fallback** — for tagged versions you have no compat coverage for, mark them as `CLI_ONLY` (compat works at compile time but not in the IDE). Metro records this explicitly: `2.2.20-ij252-17=CLI_ONLY`.

The 255-numbered versions (`2.2.255`, `2.3.255`) are placeholders Android Studio uses for "next major" — they always overshoot by a year. Your compat resolver should be conservative: pick the nearest known version *below* the tagged version, never the closest in numerical distance.

## Multiple Kotlin Gradle plugin versions in a single build

A practical Gradle gotcha when you split into per-version source sets (Strategy 4 with build-time selection): **two subprojects in the same build cannot apply different versions of the Kotlin Gradle plugin via the modern `plugins {}` block**. The plugins block resolves each plugin once per build at `pluginManagement` time.

The canonical workaround is to fall back to the **legacy `buildscript {}` block per subproject**, which gives each subproject its own classloader for the Kotlin Gradle plugin:

```kotlin
// compiler-compat/k230/build.gradle.kts
buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}
apply(plugin = "org.jetbrains.kotlin.jvm")

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler:2.4.0")
}
```

Each subproject's `buildscript {}` is evaluated independently, so different KGP versions coexist. Trade-off: you give up the modern plugin DSL's type safety inside that subproject. Both Metro and kotlinx-rpc accept this cost.

A cleaner alternative both projects use in practice: **don't apply different KGP versions per subproject**. Instead, apply one KGP version everywhere (the latest you support), and only the `compileOnly("...kotlin-compiler:VERSION")` differs per subproject. KGP's compatibility window is wider than the compiler's plugin API, so this usually works. Use the per-subproject `buildscript {}` only if you actually hit a KGP-specific incompatibility.

### Daemon noise: set `kotlin.compiler.execution.strategy=in-process`

When two KGP versions coexist (or when you launch CI matrices of `-Pkotlin.compiler=...` across the same build dir), Gradle emits `w: Detected multiple Kotlin daemon sessions at <...>` warnings (`KotlinGradleFinishBuildHandler.kt:47`) as the per-version daemons fail to reuse cached state. Set in your root `gradle.properties`:

```properties
kotlin.compiler.execution.strategy=in-process
```

This runs the compiler in the Gradle worker JVM instead of spawning a Kotlin compile daemon. The warning goes away and the build is fully reproducible across version flips. Trade-off: you lose the Kotlin daemon's cross-module warmup state (each compilation now starts in the worker's JVM), which can lengthen clean builds; for CI multi-version sweeps the determinism is usually worth more than the speed.

## CI matrix

Run your build matrix against every Kotlin version you claim to support. Both Metro and kotlinx-rpc maintain a versions file (`compiler-compat/version-aliases.txt` and `versions-root/libs.versions.toml` respectively) that doubles as the CI matrix source.

```yaml
# .github/workflows/ci.yml
strategy:
  matrix:
    kotlin: [2.1.21, 2.2.20, 2.2.21, 2.3.0, 2.3.20, 2.3.21, 2.4.0]
steps:
  - run: ./gradlew test -Pkotlin.compiler=${{ matrix.kotlin }}
```

In Gradle:

```kotlin
val kotlinVersion = providers.gradleProperty("kotlin.compiler").orElse("2.4.0")
dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler:${kotlinVersion.get()}")
}
```

This is the only mechanism that catches Strategy 4's "misordered `minVersion` in the resolver" footgun before consumers do.

Cap the matrix:
- Test the *current* Kotlin against current Gradle and current JDK
- Test the *oldest supported* Kotlin against the oldest Gradle that supports it
- Skip combinations in between
- Pin Gradle / JDK versions in CI; only the Kotlin axis is variable

3 Kotlin × 2 Gradle × 3 JDK = 18 cells. 7 Kotlin × 1 Gradle × 1 JDK = 7 cells, which actually catches what you care about.

## Common gotchas

### Per-subproject `kotlin-compiler` mismatch with consumer's compiler

Your plugin's `compileOnly("...kotlin-compiler:2.4.0")` doesn't pin the consumer's Kotlin version. The consumer can be on 2.2 or 2.4. If APIs you reference don't exist in their version, you get `NoSuchMethodError` at their compile time. Either use only APIs stable across the supported range, ship per-version artifacts, or ship a compat shim (Strategy 4).

### `KotlinCompilerVersion.VERSION` returns `@snapshot@`

`KotlinCompilerVersion.VERSION` (in `org.jetbrains.kotlin.config`) returns the literal string `@snapshot@` for development builds of the Kotlin compiler. If you parse it for major/minor numbers, guard the snapshot case — return early or treat it as the latest known version.

For production resolvers (Strategy 4 ServiceLoader), prefer reading `META-INF/compiler.version` directly off the classpath via `ClassLoader.getResourceAsStream`. That file contains the canonical version string and avoids the `@snapshot@` placeholder.

### `@CompatApi` shims that are no longer needed

When you drop support for an old Kotlin version, every `@CompatApi(since = "X.Y.Z")` shim where `X.Y.Z <= droppedVersion` becomes dead code. Without the annotation you cannot identify them. With it, a quick grep — `grep -r '@CompatApi(since = "2.2'` — surfaces every shim retirable when 2.2 support is dropped.

### IDE only sees one Kotlin version's behaviour

The IntelliJ Kotlin plugin embeds one Kotlin version. Even if your plugin supports 2.1 / 2.2 / 2.3, the IDE only ever shows the version it embeds. Document that consumers should match their project's Kotlin to the IDE's bundled version for accurate in-IDE diagnostics. See "IDE-bundled Kotlin version quirks" above for the version-string resolution problem this creates.

### `kotlin-stdlib` version on consumer's classpath

The Kotlin Gradle plugin transitively pulls a stdlib matching the consumer's Kotlin version. Your plugin must not bundle stdlib (see [`compiler-plugin-bootstrap`](../compiler-plugin-bootstrap/guide.md)'s "Wrong artifact" gotcha). Always `compileOnly` for stdlib in the compiler plugin module, regardless of strategy.

### `ExperimentalCompilerApi` opt-in flag changes

Newer Kotlin versions occasionally add new things to `ExperimentalCompilerApi`'s surface. Older `@OptIn` annotations may need updating when supporting both. Prefer file-level `@file:OptIn(ExperimentalCompilerApi::class)` over class-level so adding new uses doesn't require changing every site.

### `CompilerPluginRegistrar.pluginId` missing on pre-2.3 plugins

A pre-2.3 plugin loaded on Kotlin 2.3+ fails at instantiation with `AbstractMethodError` because `pluginId: String` is now `abstract`. Add the override before doing anything else; it's the cheapest break to fix and the most common cause of "my plugin worked yesterday" reports after a Kotlin bump.

## Practical recommendation

Default starting point: **Strategy 1, single artifact**. Don't escalate until you actually have users on multiple Kotlin versions.

Escalation order when you do:

1. **One or two drift sites** → Strategy 2 (narrow reflection).
2. **Three or more Kotlin minors and you want one shipped JAR** → Strategy 4 + fat JAR (Metro pattern). The investment buys you compile-time guarantees per version and a single coordinate for consumers.
3. **A lot of inline drift across many call sites** → Strategy 3 (CSM-style source preprocessor) + per-version coordinates (kotlinx-rpc pattern). The investment is in the template processor itself; once it's built, adding a new compat case is cheap.
4. **Both** → Strategy 3 for inline drift + Strategy 4 (interface, possibly without ServiceLoader if templates pick the impl) for structural divergence. This is what kotlinx-rpc does in production.

If you're writing **inside** `kotlin/plugins/`, stay with Strategy 1 and let JetBrains' branching policy (one Kotlin version per release branch) handle the drift for you. Most successful plugins (allopen, parcelize, atomicfu, kotlinx-serialization) live there precisely because version drift is JetBrains' problem, not theirs. Contributing upstream is usually cheaper than rolling your own multi-version layer.

## Relation to other skills

- **Single-version foundation** → [`compiler-plugin-bootstrap`](../compiler-plugin-bootstrap/guide.md)
- **Distribution mechanics** → [`gradle-plugin-integration`](../gradle-plugin-integration/guide.md)
- **CI testing across versions** → [`compiler-plugin-testing`](../compiler-plugin-testing/guide.md)

## What this skill does NOT cover

- Migrating a K1-only plugin to K2 (the K1→K2 transition is its own multi-month effort; consult `kotlin/docs/fir/k2-plugins.md`)
- Cross-platform (JVM / JS / Native / Wasm) variance — see the platform-specific notes in [`gradle-plugin-integration`](../gradle-plugin-integration/guide.md)
- Upgrading from `org.jetbrains.kotlin:kotlin-compiler` (the un-shaded artefact) to `kotlin-compiler-embeddable` — that's a one-time fix, not a multi-version concern
- Designing the compiler plugin's *runtime library* across versions — that's an ordinary Kotlin Multiplatform / binary-compatibility problem, not a compiler-plugin-API one
