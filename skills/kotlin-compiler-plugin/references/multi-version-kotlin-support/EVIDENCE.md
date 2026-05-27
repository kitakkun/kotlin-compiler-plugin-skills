# Evidence Dossier: multi-version-kotlin-support

References to the JetBrains Kotlin source tree use `kotlin/<path>` notation against tag `v2.3.21` unless noted. References to community plugins (Metro, kotlinx-rpc) are pinned to the SHAs listed at the bottom.

All quoted snippets below originate from projects under the **Apache License 2.0**:

- JetBrains/kotlin — Copyright 2010-2024 JetBrains s.r.o and respective authors and developers
- ZacSweers/metro — Copyright (C) 2024-2025 Zac Sweers
- Kotlin/kotlinx-rpc — Copyright 2023-2025 JetBrains s.r.o and contributors

Original copyright applies to each snippet. See [`../../NOTICE.md`](../../NOTICE.md) for the consolidated attribution required by Apache 2.0 § 4(b)/(d).

---

### Claim: `KotlinCompilerVersion.VERSION` is the canonical runtime hook for detecting the active Kotlin compiler version.

**File**: [`kotlin/compiler/compiler.version/src/org/jetbrains/kotlin/config/KotlinCompilerVersion.java:16`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/compiler.version/src/org/jetbrains/kotlin/config/KotlinCompilerVersion.java#L16)

**Snippet**:
```java
public class KotlinCompilerVersion {
    public static final String VERSION_FILE_PATH = "/META-INF/compiler.version";
    public static final String VERSION;
```
The `VERSION` field is populated from `/META-INF/compiler.version` packaged in `kotlin-compiler-embeddable.jar`, so plugins reading it observe whichever compiler is hosting them at runtime — not the one they were compiled against.

---

### Claim: `CompilerPluginRegistrar.pluginId` is an abstract `val` since Kotlin 2.3, breaking plugins built against 2.2.x without overriding it.

**File**: [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt:26`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt#L26)

**Snippet**:
```kotlin
abstract val pluginId: String
```
Introduced via commit `1180951a80f6` ("[Plugins] Require a unique pluginId for all compiler plugins", KT-55300). Plugin JARs compiled against pre-2.3 `CompilerPluginRegistrar` (which had no `pluginId`) fail to instantiate at load time on 2.3+ with `AbstractMethodError` / `InstantiationError`.

---

### Claim: Kotlin 2.2 unified IR member-access argument layout via KT-68003 — `arguments` replaces the separate `dispatchReceiver` / `extensionReceiver` / `valueArguments` slots.

**File**: [`kotlin/docs/backend/IR_parameter_api_migration.md:5`](https://github.com/JetBrains/kotlin/blob/v2.3.21/docs/backend/IR_parameter_api_migration.md#L5)

**Snippet**:
> "It has been refactored how value parameters in `IrFunction` and value arguments in `IrMemberAccessExpression` are represented (KT-68003). The old API is deprecated and scheduled for removal somewhere around Kotlin 2.2.20 or 2.3."

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/expressions/IrMemberAccessExpression.kt:43`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/expressions/IrMemberAccessExpression.kt#L43)

**Snippet**:
```kotlin
val arguments: ValueArgumentsList = ValueArgumentsList()
```

---

### Claim: `KotlinCompilerVersion.VERSION` returns the literal string `@snapshot@` for development builds and must be guarded before parsing.

**File**: [`kotlin/compiler/compiler.version/src/org/jetbrains/kotlin/config/KotlinCompilerVersion.java:40`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/compiler.version/src/org/jetbrains/kotlin/config/KotlinCompilerVersion.java#L40)

**Snippet**:
```java
public static String getVersion() {
    return VERSION.equals("@snapshot@") ? null : VERSION;
}
```

---

### Claim: JetBrains' own production plugins use multi-source-set splits — but the split is by ROLE, not by Kotlin minor version.

**Path**: [`kotlin/plugins/parcelize/parcelize-compiler/`](https://github.com/JetBrains/kotlin/tree/v2.3.21/plugins/parcelize/parcelize-compiler)

Subdirectories at v2.3.21: `parcelize.cli`, `parcelize.common`, `parcelize.k1`, `parcelize.k2`, `parcelize.backend`, `testData`, `testFixtures`, `tests`, `tests-gen`.

Neither parcelize nor `kotlin/plugins/kotlinx-serialization/` contain any directory like `parcelize.k2-2.2` / `parcelize.k2-2.3`. Each release of these plugins targets exactly one Kotlin compiler version, pinned by the `kotlin-lang` repo's branch.

---

### Claim: `KotlinCompilerPluginSupportPlugin.getPluginArtifact()` is the per-Kotlin-version selection hook on the Gradle plugin side.

**File**: [`kotlin/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt:234`](https://github.com/JetBrains/kotlin/blob/v2.3.21/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt#L234)

**Snippet**:
```kotlin
fun getPluginArtifact(): SubpluginArtifact
```

Surrounding doc: "Retrieves the Maven coordinates of the Kotlin compiler plugin associated with this supplemental Gradle plugin. The Kotlin Gradle plugin adds this artifact to the relevant Gradle configurations so it can be automatically provided for compilation."

`SubpluginArtifact` is declared further down at [`KotlinGradleSubplugin.kt:263`](https://github.com/JetBrains/kotlin/blob/v2.3.21/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinGradleSubplugin.kt#L263) as an `open class` (not a `data class`):
```kotlin
open class SubpluginArtifact(val groupId: String, val artifactId: String, val version: String? = null)
```

---

### Claim: kotlinx-rpc's CSM (Compiler Specific Modules) pattern: source preprocessor templates with version-pattern directives.

**File**: [`kotlinx-rpc/docs/workflow.md`](https://github.com/Kotlin/kotlinx-rpc/blob/3c3c6f0201253958d15248ffdafa138e143a4ce6/docs/workflow.md)

**Excerpt** (CSM directive rules):
> "We enclose code dependent on a version in `##csm` tags. Every such block must:
> - Start with `//##csm <block_name>`
> - End with `//##csm /<block_name>`
> - Specify inside zero or more of the code blocks:
>   - `//##csm default` + `//##csm /default`
>   - `//##csm specific=[<version_patten>]` + `//##csm /specific`"

**File**: [`kotlinx-rpc/compiler-plugin/compiler-plugin-cli/src/main/templates/kotlinx/rpc/codegen/CompilerPluginRegistrar.kt`](https://github.com/Kotlin/kotlinx-rpc/blob/3c3c6f0201253958d15248ffdafa138e143a4ce6/compiler-plugin/compiler-plugin-cli/src/main/templates/kotlinx/rpc/codegen/CompilerPluginRegistrar.kt)

**Real-world usage snippet**:
```kotlin
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
The `specific=[2.1.0...2.2.99]` block is empty (no override) for that range; the `default` block emits `pluginId` for all other versions. This matches the guide.md claim that pre-2.3 plugins must omit `pluginId`, while 2.3+ requires it.

---

### Claim: kotlinx-rpc's CSM template processor implements range / prefix matching against the active Kotlin version.

**File**: [`kotlinx-rpc/gradle-conventions/src/main/kotlin/util/csm/template.kt`](https://github.com/Kotlin/kotlinx-rpc/blob/3c3c6f0201253958d15248ffdafa138e143a4ce6/gradle-conventions/src/main/kotlin/util/csm/template.kt)

**Matching code snippet**:
```kotlin
private fun matchesKotlinVersion(projectVersion: String, pattern: String): Boolean {
    return pattern.split(",").any { part ->
        if (part.contains("...")) {
            val (from, to) = part.split("...")
            ...
            projectV in fromV..toV
        } else {
            val prefix = part.trim().substringBefore("*")
            projectVersion.startsWith(prefix)
        }
    }
}
```

The processor is wired in via `gradle-conventions/src/main/kotlin/compiler-specific-module.gradle.kts`, which registers a `processCsmTemplates` task that consumes `libs.versions.kotlin.compiler.get()`. Each compiler-plugin source set is configured to read templates from `src/main/templates` and emit processed Kotlin into `build/generated-sources/csm`.

---

### Claim: kotlinx-rpc also maintains a `FirVersionSpecificApi` interface for divergences too coarse for CSM directives.

**File**: [`kotlinx-rpc/compiler-plugin/compiler-plugin-k2/src/main/kotlin/kotlinx/rpc/codegen/FirVersionSpecificApi.kt`](https://github.com/Kotlin/kotlinx-rpc/blob/3c3c6f0201253958d15248ffdafa138e143a4ce6/compiler-plugin/compiler-plugin-k2/src/main/kotlin/kotlinx/rpc/codegen/FirVersionSpecificApi.kt)

**Snippet**:
```kotlin
interface FirVersionSpecificApi {
    fun ConeKotlinType.toClassSymbolVS(session: FirSession): FirClassSymbol<*>?
    fun FirRegularClassSymbol.declarationsVS(session: FirSession): List<FirBasedSymbol<*>>
    val FirResolvedTypeRef.coneTypeVS: ConeKotlinType
    fun FirTypeRef.toRegularClassSymbolVS(session: FirSession): FirRegularClassSymbol?
    fun ConeKotlinType.toRegularClassSymbolVS(session: FirSession): FirRegularClassSymbol?
    fun ConeKotlinType.toFirResolvedTypeRefVS(...): FirResolvedTypeRef
    val messageCollectorKey: CompilerConfigurationKey<MessageCollector>
    fun FirSession.getRegularClassSymbolByClassIdVS(classId: ClassId): FirRegularClassSymbol?
    fun FirAnnotation.getKClassArgumentVS(name: Name, session: FirSession): ConeKotlinType?
    ...
}

inline fun <T> vsApi(body: FirVersionSpecificApi.() -> T): T {
    return FirVersionSpecificApiImpl.body()
}
```
The `VS` suffix marks each method as a Version-Specific shim. Implementations are picked via the surrounding CSM template engine, not ServiceLoader — kotlinx-rpc swaps `FirVersionSpecificApiImpl` per build using the same template directives that select inline drift.

---

### Claim: kotlinx-rpc publishes per-Kotlin-version artifacts with the coordinate `<kotlin-version>-<library-version>`.

**File**: [`kotlinx-rpc/gradle-plugin/src/main/kotlin/kotlinx/rpc/RpcPluginConst.kt`](https://github.com/Kotlin/kotlinx-rpc/blob/3c3c6f0201253958d15248ffdafa138e143a4ce6/gradle-plugin/src/main/kotlin/kotlinx/rpc/RpcPluginConst.kt)

**Snippet**:
```kotlin
private val kotlinVersion by lazy { loadKotlinVersion() }

val libraryKotlinPrefixedVersion by lazy {
    "$kotlinVersion-$LIBRARY_VERSION"
}
```

**File**: [`kotlinx-rpc/gradle-plugin/src/main/kotlin/kotlinx/rpc/KotlinCompilerPluginBuilder.kt`](https://github.com/Kotlin/kotlinx-rpc/blob/3c3c6f0201253958d15248ffdafa138e143a4ce6/gradle-plugin/src/main/kotlin/kotlinx/rpc/KotlinCompilerPluginBuilder.kt)

**Snippet** (the resolved version is used as the artifact's coordinate):
```kotlin
override fun getPluginArtifact(): SubpluginArtifact {
    val artifactId = artifactId ?: compilerPluginArtifactId(isInternal)
    return SubpluginArtifact(groupId, artifactId + pluginSuffix, version)
}
```
At apply time `version` resolves to `libraryKotlinPrefixedVersion`, which depends on the Kotlin Gradle plugin version of the consumer build.

---

### Claim: Metro's compat-shim interface centralizes every version-volatile API call with `@CompatApi(since=..., reason=...)` documentation.

**File**: [`metro/compiler-compat/src/main/kotlin/dev/zacsweers/metro/compiler/compat/CompatContext.kt`](https://github.com/ZacSweers/metro/blob/45b38b230540497af32c8d176cbf18460cb44e19/compiler-compat/src/main/kotlin/dev/zacsweers/metro/compiler/compat/CompatContext.kt)

**Interface declaration**:
```kotlin
public interface CompatContext {
    public companion object Companion {
        public fun create(knownVersion: KotlinToolingVersion? = null): CompatContext =
            resolveFactory(knownVersion).create()
    }

    public interface Factory {
        public val minVersion: String
        public val currentVersion: String get() = loadCompilerVersionString()
        public fun create(): CompatContext

        public companion object Companion {
            private const val COMPILER_VERSION_FILE = "META-INF/compiler.version"
            public fun loadCompilerVersionString(): String { ... }
        }
    }

    @CompatApi(since = "2.3.0", reason = ABI_CHANGE,
        message = "containingFileName parameter was added")
    @ExperimentalTopLevelDeclarationsGenerationApi
    public fun FirExtension.createTopLevelFunction(
        key: GeneratedDeclarationKey,
        callableId: CallableId,
        returnType: ConeKotlinType,
        containingFileName: String? = null,
        config: SimpleFunctionBuildingContext.() -> Unit = {},
    ): FirFunction

    @CompatApi(since = "2.3.20", reason = RENAMED,
        message = "FirSimpleFunctionBuilder was renamed to FirNamedFunctionBuilder")
    public fun FirDeclarationGenerationExtension.buildMemberFunction(...): FirFunction

    @CompatApi(since = "2.4.0", reason = ABI_CHANGE,
        message = "2.4 introduced IrAnnotation for IrConstructorCall")
    fun IrBuilder.irAnnotationCompat(...): IrConstructorCall
    ...
}

internal annotation class CompatApi(
    val since: String,
    val reason: Reason,
    val message: String = "",
) {
    enum class Reason { DELETED, RENAMED, ABI_CHANGE, COMPAT }
}
```

The same file shows the resolver implementation and the dev-track-vs-stable handling:
```kotlin
private fun resolveFactoryForVersion(
    currentVersion: KotlinToolingVersion,
    factoryDataList: List<FactoryData>,
): Factory? {
    if (currentVersion.isDev) {
        val devFactories = factoryDataList.filter {
            KotlinToolingVersion(it.factory.minVersion).isDev
        }
        val devMatch = findHighestCompatibleFactory(currentVersion, devFactories)
        if (devMatch != null) return devMatch

        // Fall back: a 2.2.20-dev build still matches the 2.2.20 factory,
        // even though dev < stable in maturity ordering.
        val nonDevFactories = factoryDataList.filter {
            !KotlinToolingVersion(it.factory.minVersion).isDev
        }
        val baseVersion = KotlinToolingVersion(
            currentVersion.major, currentVersion.minor, currentVersion.patch, null,
        )
        return findHighestCompatibleFactory(baseVersion, nonDevFactories)
    }
    val nonDevFactories = factoryDataList.filter {
        !KotlinToolingVersion(it.factory.minVersion).isDev
    }
    return findHighestCompatibleFactory(currentVersion, nonDevFactories)
}
```

---

### Claim: Metro registers each per-version impl via `META-INF/services/...CompatContext$Factory` and loads them with `ServiceLoader`.

**File**: [`metro/compiler-compat/k230/src/main/resources/META-INF/services/dev.zacsweers.metro.compiler.compat.CompatContext$Factory`](https://github.com/ZacSweers/metro/blob/45b38b230540497af32c8d176cbf18460cb44e19/compiler-compat/k230/src/main/resources/META-INF/services/dev.zacsweers.metro.compiler.compat.CompatContext%24Factory)

The same file is present at the resource path `META-INF/services/dev.zacsweers.metro.compiler.compat.CompatContext$Factory` in every `compiler-compat/k*/` Gradle subproject (verified by listing the parent directories at the pinned SHA). Standard Java `ServiceLoader` mechanism — at runtime, all factories on the classpath are visible, and `CompatContext.Companion.resolveFactory()` selects the one with the highest `minVersion` ≤ active compiler version.

---

### Claim: Metro maintains an IDE Kotlin version alias table, generated by scanning IntelliJ Community Edition tags.

**File**: [`metro/compiler-compat/ide-mappings.txt`](https://github.com/ZacSweers/metro/blob/45b38b230540497af32c8d176cbf18460cb44e19/compiler-compat/ide-mappings.txt)

**Generated header & sample entries**:
```
# IDE Kotlin version alias mappings
# Generated by fetch-all-ide-kotlin-versions.py
# Format: <ide-version>=<dev-version>
2.2.20-ij252-17=CLI_ONLY
2.2.20-ij252-24=CLI_ONLY
2.2.255-dev-255=CLI_ONLY
2.3.20-ij253-105=2.3.0-dev-9992
2.3.20-ij253-119=2.3.0-dev-9992
2.3.20-ij253-87=2.3.0-dev-9992
2.3.255-dev-255=2.3.0-dev-9992
2.4.0-ij261-32=2.4.0-dev-2633
2.4.0-ij261-50=2.4.0-dev-2633
2.4.255-dev-255=2.4.0-dev-2631
```
The generator script (`compiler-compat/fetch-all-ide-kotlin-versions.py`) follows the comment in `compiler-compat/build.gradle.kts`: *"The real version can be found by checking the IntelliJ tag for the studio build number: https://github.com/JetBrains/intellij-community/blob/idea/<intellij-version>/.idea/libraries/kotlinc_kotlin_compiler_common.xml"*.

`CLI_ONLY` denotes IDE versions whose bundled Kotlin has no Metro compat coverage; those builds work for command-line compilation but skip plugin features inside the IDE.

---

### Claim: Metro's compat impls are bundled into a single fat JAR (`dev.zacsweers.metro:compiler`); the Gradle plugin returns just one `SubpluginArtifact`.

**File**: [`metro/gradle-plugin/src/main/kotlin/dev/zacsweers/metro/gradle/MetroGradleSubplugin.kt`](https://github.com/ZacSweers/metro/blob/45b38b230540497af32c8d176cbf18460cb44e19/gradle-plugin/src/main/kotlin/dev/zacsweers/metro/gradle/MetroGradleSubplugin.kt)

**Snippet**:
```kotlin
override fun getPluginArtifact(): SubpluginArtifact {
    val version = System.getProperty(COMPILER_VERSION_OVERRIDE, VERSION)
    return SubpluginArtifact(
        groupId = "dev.zacsweers.metro",
        artifactId = "compiler",
        version = version,
    )
}
```

The single artifact `compiler` is built via `shadowJar` to merge every `compiler-compat/k*/` impl into one publication (see `metro/compiler/build.gradle.kts` `tasks.shadowJar` configuration). This is the structural opposite of kotlinx-rpc's per-version coordinate scheme: Metro's consumer always pulls one artifact, and ServiceLoader picks the right impl at runtime; kotlinx-rpc's consumer pulls a different coordinate per Kotlin version, and code is selected at publish time.

---

### Claim: Metro's CI matrix is the union of every Kotlin version with a corresponding `compiler-compat/k*` module.

**File**: [`metro/compiler-compat/version-aliases.txt`](https://github.com/ZacSweers/metro/blob/45b38b230540497af32c8d176cbf18460cb44e19/compiler-compat/version-aliases.txt)

**Snippet** (full file contents at the pinned commit):
```
# Kotlin compiler versions to test against in CI
# This file must be an equal or superset of the available compiler-compat modules.
# Each line is a Kotlin version that has a corresponding k* module.

2.2.20
2.2.21
2.3.0
2.3.20
2.3.21-RC
2.3.21-RC2
2.4.0-dev-2124
2.4.0-Beta1
2.4.0-Beta2
```
This file is read by both the CI workflow (one matrix cell per line) and the build's correctness check (the constraint at the top: "must be an equal or superset of the available compiler-compat modules" — adding a `k*` directory without adding the version here is rejected).

---

## Pinned commits

- Metro: [`45b38b230540497af32c8d176cbf18460cb44e19`](https://github.com/ZacSweers/metro/tree/45b38b230540497af32c8d176cbf18460cb44e19)
- kotlinx-rpc: [`3c3c6f0201253958d15248ffdafa138e143a4ce6`](https://github.com/Kotlin/kotlinx-rpc/tree/3c3c6f0201253958d15248ffdafa138e143a4ce6)
