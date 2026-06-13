---
name: compiler-plugin-testing
description: "Test Kotlin compiler plugins with one of two infrastructures — (A) Gradle-based integration tests where `sample/` modules compile real source with `-Xplugin=` and assert on success/failure/output, or (B) JetBrains' official compiler test framework consumed via the published `kotlin-compiler-internal-test-framework` artifact, which enables FIR/IR-level fixture tests with `<!DIAGNOSTIC!>` markers, `fun box(): String`, `// FIR_DUMP`, `// DUMP_IR` and golden-file comparison. Read compiler-plugin-bootstrap first. NOT a tutorial on JUnit basics or third-party in-process compiler libraries."
---

# Compiler Plugin Testing

## Principle

Test a compiler plugin by **compiling real Kotlin source with the plugin loaded and checking the result** — either that compilation fails with an expected diagnostic, or that compilation succeeds and the program produces expected output. This is what JetBrains does for every plugin under `kotlin/plugins/`, and it's what standalone plugin projects should do too.

There are two infrastructures available, and they are usually not mutually exclusive — projects often start with (A) and add (B) later when they need IR/FIR-level assertions.

| Infrastructure | When to use | Cost |
|---|---|---|
| **A. Gradle integration tests** | Smoke tests, demos, simple "does the plugin load and do its job" checks | Trivial — one extra Gradle module, no extra dependencies |
| **B. Official compiler test framework** | FIR dump comparison, IR dump comparison, `<!DIAGNOSTIC!>` markers at exact source ranges, multi-module test data, K/JS+K/JVM matrix | Higher — extra dependency, system properties, JUnit 4+5 mix, brittle to compiler version bumps |

> ⚠️ **Exception — a plugin with `reified PsiElement` diagnostic factories cannot serve A and B from one compiled artifact.** A and B disagree on *which* `kotlin-compiler` you compile against: Pattern A loads your plugin into the **shaded** `kotlin-compiler-embeddable` (so `PsiElement` must be imported as `org.jetbrains.kotlin.com.intellij.psi.PsiElement` — see [`fir-additional-checkers-extension`](../fir-additional-checkers-extension/guide.md)), while the Pattern B test framework runs it against the **un-shaded** `kotlin-compiler` (`com.intellij.psi.PsiElement`). With a `reified` type parameter (e.g. `error1<PsiElement>` factories, or `inline fun <reified P : PsiElement>`), that `PsiElement` class reference is **baked into the bytecode**, so the same `.class` can't load under both compilers — loading the un-shaded build into the embeddable compiler throws `NoClassDefFoundError: com/intellij/psi/PsiElement`, and vice versa.
>
> **Resolution:** compile and run the **un-shaded** build for Pattern B (so the official framework and its testData resolve), and **distribute a shaded JAR** produced by `shadowJar` relocating `com.intellij` → `org.jetbrains.kotlin.com.intellij`. Point Pattern A's `-Xplugin=` (and `getPluginArtifact()`) at that **relocated** JAR, not at the raw plugin classes. The shadow/relocate recipe — including the `com.gradleup.shadow` setup and the `relocate(...)` call — is in [`gradle-plugin-integration`](../gradle-plugin-integration/guide.md) ("Embeddable variant"); the `<plugin>.embeddable` module in [`compiler-plugin-bootstrap`](../compiler-plugin-bootstrap/guide.md)'s production layout exists for exactly this reason. Without a `reified` `PsiElement` (no factory bakes the class into bytecode), A and B coexist fine and this whole exception doesn't apply.

Two **test types** apply equally to both infrastructures:

| Type | What it verifies | PASS criterion |
|---|---|---|
| **Diagnostic test** | Plugin emits expected errors / warnings | Compilation **fails** (or reports the expected diagnostic at the expected range) |
| **Box test** | Plugin transforms code correctly | Compilation succeeds AND `box()` returns `"OK"` (or sample `run` prints expected output) |

## A. Gradle integration tests

A testable plugin project has two Gradle modules:

```
my-plugin/
├── settings.gradle.kts
├── build.gradle.kts          # (optional) shared config
├── plugin/
│   ├── build.gradle.kts
│   └── src/main/kotlin/...   # plugin implementation
└── sample/
    ├── build.gradle.kts
    └── src/main/kotlin/...   # test source that exercises the plugin
```

`sample/` loads the plugin via `-Xplugin=` and contains source files that exercise the plugin's behaviour. This is the same structure used in every `verification/` project in this repository.

### `settings.gradle.kts`

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "my-plugin"

include("plugin", "sample")
```

### `plugin/build.gradle.kts`

```kotlin
plugins {
    kotlin("jvm") version "2.4.0"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.0")
}
```

### `sample/build.gradle.kts` — diagnostic test

For a plugin that emits custom diagnostics (checker), the goal is to verify that **compilation fails** with the expected error:

```kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.0"
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
```

Run with:

```bash
./gradlew :sample:compileKotlin
```

Expected: the build **fails** and the error output contains the custom diagnostic message.

### `sample/build.gradle.kts` — box test

For a plugin that transforms code (generation, status change, call rewriting, etc.), the goal is to verify that compilation **succeeds** and the program produces expected output:

```kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.0"
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.example.MainKt")
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
```

Run with:

```bash
./gradlew :sample:run
```

Expected: the build succeeds and stdout contains the expected output (e.g. `from Sub`).

### Writing test sources for Pattern A

Diagnostic test source — both positive and negative cases:

```kotlin
package com.example

@MustBeFinal
class Ok               // no diagnostic — this should compile cleanly

@MustBeFinal
open class Bad         // plugin should emit an error here
```

Box test source — calls into plugin-affected code and prints a verifiable result:

```kotlin
package com.example

@Open
class Base {
    fun greet(): String = "from Base"
}

class Sub : Base() {
    override fun greet(): String = "from Sub"
}

fun main() {
    val s: Base = Sub()
    println(s.greet())   // prints "from Sub" — proves the plugin made Base and greet() open
}
```

For plugins with multiple behaviours, create multiple sample modules:

```
my-plugin/
├── plugin/
├── sample-diagnostics/     # expects compilation failure
└── sample-codegen/          # expects successful compile + run
```

## B. Official compiler test framework (standalone)

The test framework JetBrains uses inside the Kotlin monorepo is **published as the Maven artifact `org.jetbrains.kotlin:kotlin-compiler-internal-test-framework`** (one publication per Kotlin version). The word "internal" in the artifact name refers to API stability — abstract base classes can be renamed or relocated even across patch releases — *not* to availability. Standalone plugin projects can and do depend on it; pin every coordinate to the exact same compiler version, and expect to fix import paths whenever you bump Kotlin.

The authoritative reference is JetBrains' own [`Kotlin/compiler-plugin-template`](https://github.com/Kotlin/compiler-plugin-template), which is the recommended starting point if you want this infrastructure. The setup below mirrors that template (Apache-2.0; see [`NOTICE.md`](../../NOTICE.md) for full attribution).

### Module layout

```
my-plugin/
├── plugin/
│   ├── src/                    # plugin implementation
│   ├── test-fixtures/          # test runner classes (extend abstract bases)
│   ├── test/                   # hand-written tests (rare)
│   ├── test-gen/               # auto-generated JUnit 5 tests (gitignored)
│   └── testData/
│       ├── box/                # *.kt files with `fun box(): String`
│       └── diagnostics/        # *.kt files with `<!FOO!>...<!>` markers
```

### `plugin/build.gradle.kts` — dependencies

```kotlin
plugins {
    kotlin("jvm") version "2.4.0"
    `java-test-fixtures`
}

dependencies {
    // Pattern B uses the un-shaded `kotlin-compiler` (NOT `kotlin-compiler-embeddable`)
    // because the test framework links against `kotlin-compiler` symbols. Mixing the
    // un-shaded test framework with the shaded embeddable compiler puts two copies of the
    // same compiler/IntelliJ-platform classes on the classpath under different package
    // prefixes, which produces classloader/classpath conflicts at test startup.
    // The official template uses `kotlin-compiler` for the plugin's compileOnly for this
    // reason. If you previously used `kotlin-compiler-embeddable` for Pattern A, switch to
    // `kotlin-compiler` when adopting Pattern B.
    compileOnly("org.jetbrains.kotlin:kotlin-compiler:2.4.0")

    // Test framework — testFixtures so test runners can be reused
    testFixturesApi("org.jetbrains.kotlin:kotlin-test-junit5:2.4.0")
    testFixturesApi("org.jetbrains.kotlin:kotlin-compiler-internal-test-framework:2.4.0")
    testFixturesApi("org.jetbrains.kotlin:kotlin-compiler:2.4.0")
    testFixturesRuntimeOnly("junit:junit:4.13.2")  // JUnit 4 also needed at runtime
}

sourceSets {
    test {
        java.setSrcDirs(listOf("test", "test-gen"))
        resources.setSrcDirs(listOf("testData"))
    }
    testFixtures {
        java.setSrcDirs(listOf("test-fixtures"))
    }
}
```

### `plugin/build.gradle.kts` — test task wiring

The framework looks up stdlib / reflect / kotlin-test JARs by absolute path via system properties. Without these, tests fail at startup:

```kotlin
val testArtifacts: Configuration by configurations.creating

dependencies {
    testArtifacts("org.jetbrains.kotlin:kotlin-stdlib:2.4.0")
    testArtifacts("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.4.0")
    testArtifacts("org.jetbrains.kotlin:kotlin-reflect:2.4.0")
    testArtifacts("org.jetbrains.kotlin:kotlin-test:2.4.0")
    testArtifacts("org.jetbrains.kotlin:kotlin-script-runtime:2.4.0")
    testArtifacts("org.jetbrains.kotlin:kotlin-annotations-jvm:2.4.0")
}

tasks.test {
    dependsOn(testArtifacts)
    useJUnitPlatform()
    workingDir = rootDir

    systemProperty("idea.home.path", rootDir)
    systemProperty("idea.ignore.disabled.plugins", "true")

    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib", "kotlin-stdlib")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib-jdk8", "kotlin-stdlib-jdk8")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-reflect", "kotlin-reflect")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-test", "kotlin-test")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-script-runtime", "kotlin-script-runtime")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-annotations-jvm", "kotlin-annotations-jvm")
}

fun Test.setLibraryProperty(propName: String, jarName: String) {
    val path = testArtifacts.files
        .find { """$jarName-\d.*""".toRegex().matches(it.name) }
        ?.absolutePath
        ?: error("testArtifacts is missing $jarName — add the matching `testArtifacts(\"<group>:$jarName:<version>\")` coordinate to `dependencies { }`")
    systemProperty(propName, path)
}
```

**KMP-module jars** — if a `testArtifacts(...)` coordinate is a Kotlin Multiplatform module (e.g. `kotlin { jvm() }`), the published JVM artifact name is `<module>-jvm-<version>.jar`, *not* `<module>-<version>.jar`. The regex `"""$jarName-\d.*"""` won't match it; pass the `-jvm`-suffixed name explicitly:

```kotlin
setLibraryProperty("my.plugin.runtime", "my-plugin-runtime-jvm")
```

### Test runner classes (in `test-fixtures/`)

A diagnostic test runner extends `AbstractFirPhasedDiagnosticTest`; a box test runner extends `AbstractFirBlackBoxCodegenTestBase`. Both classes come from the test framework artifact. Imports are elided in the snippets below; the most commonly needed ones live in these packages:

| Type | Package |
|---|---|
| `FirParser` | `org.jetbrains.kotlin.test` |
| `TestConfigurationBuilder` | `org.jetbrains.kotlin.test.builders` |
| `FirDiagnosticsDirectives`, `JvmEnvironmentConfigurationDirectives`, `CodegenTestDirectives`, `TestPhaseDirectives` (`RUN_PIPELINE_TILL`) | `org.jetbrains.kotlin.test.directives` |
| `TestPhase` (`FRONTEND` / `FIR2IR` / `BACKEND`) | `org.jetbrains.kotlin.test.services` |
| `AbstractFirPhasedDiagnosticTest` | `org.jetbrains.kotlin.test.runners` |
| `AbstractFirBlackBoxCodegenTestBase` | `org.jetbrains.kotlin.test.runners.codegen` |
| `EnvironmentBasedStandardLibrariesPathProvider`, `KotlinStandardLibrariesPathProvider`, `EnvironmentConfigurator`, `TestServices`, `TestModule` | `org.jetbrains.kotlin.test.services` |
| `CompilerPluginRegistrar` | `org.jetbrains.kotlin.compiler.plugin` |
| `CompilerConfiguration` | `org.jetbrains.kotlin.config` |
| `addJvmClasspathRoot` (extension on `CompilerConfiguration`) | `org.jetbrains.kotlin.cli.jvm.config` |
| `File` | `java.io` |

The official template's `compiler-plugin/test-fixtures/.../runners/*.kt` files have the exact import lists for the Kotlin version it tracks; treat the table above as a starting cheat sheet rather than an exhaustive list.

**`configure` vs `configuration` — same prefix, different members.** `AbstractKotlinCompilerTest` ([source](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/runners/AbstractKotlinCompilerTest.kt)) declares both:

```kotlin
protected val configuration: TestConfigurationBuilder.() -> Unit = { … }   // a property of lambda type
abstract fun configure(builder: TestConfigurationBuilder)                  // the user hook
```

`configure(builder)` is **abstract** and is the documented main hook — its own KDoc reads *"This is the main method to declare the test configuration."* That's where you add directives, configurators, and call `super.configure(builder)`. The intermediate `Abstract*` superclass (e.g. `AbstractFirBlackBoxCodegenTestBase`) implements `configure` and expects you to override it again. The `configuration` *property* is a lambda built up by the framework and consumed by `runTest`; you don't override it. Mistyping `override fun configuration(...)` produces `'configuration' overrides nothing` (it's a `val`, not a `fun`), and using an `override val configuration = { … }` clobbers the framework's pre-test setup. Always extend through `configure`.

**Pick the abstract `*Base` class, not a concrete leaf runner.** The framework ships both `AbstractFirBlackBoxCodegenTestBase(parser: FirParser)` (and its diagnostic counterpart) *and* concrete subclasses like `AbstractFirLightTreeBlackBoxCodegenTest` / `AbstractFirPsiBlackBoxCodegenTest` that pin the parser. Empirically, extending one of those concrete leaf classes as the parent of your own `AbstractMyXxxTest` makes the `generateTestGroupSuiteWithJUnit5` generator throw an `IllegalArgumentException` of the shape *"Test runner AbstractMyBoxTest which inherits from RunnerWithTargetBackendForTestGeneratorMarker and used as base class"* — the leaf classes implement that marker interface as JetBrains-internal scaffolding for their own test generator and aren't intended for re-extension. Extend the `*Base` class instead and pass `FirParser.LightTree` (or `Psi`) as a constructor argument, as below.

**`createKotlinStandardLibrariesPathProvider` — overriding a single method requires re-implementing every abstract one.** `EnvironmentBasedStandardLibrariesPathProvider` is the supplied implementation and is the right return value for typical use. If you need to substitute just one path (e.g. point `minimalRuntimeJarForTests()` at a custom jar), `KotlinStandardLibrariesPathProvider` is abstract with ~12 methods — you can't subclass and override one. Use a delegating wrapper:

```kotlin
// Skeleton — NOT compilable as written. KotlinStandardLibrariesPathProvider declares
// roughly a dozen abstract methods; you must override ALL of them, delegating the ones
// you don't customise to `base`. Look up the full abstract list in the framework's source
// (`org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider`).
object MyPathProvider : KotlinStandardLibrariesPathProvider() {
    private val base = EnvironmentBasedStandardLibrariesPathProvider

    // The one we actually customise:
    override fun minimalRuntimeJarForTests(): File =
        File(System.getProperty("my.minimal.runtime.jar")!!)

    // Examples of the delegating pattern — repeat for every remaining abstract:
    override fun runtimeJarForTests(): File = base.runtimeJarForTests()
    override fun runtimeJarForTestsWithJdk8(): File = base.runtimeJarForTestsWithJdk8()
    override fun reflectJarForTests(): File = base.reflectJarForTests()
    override fun kotlinTestJarForTests(): File = base.kotlinTestJarForTests()
    override fun scriptRuntimeJarForTests(): File = base.scriptRuntimeJarForTests()
    override fun jvmAnnotationsForTests(): File = base.jvmAnnotationsForTests()
    // … and so on for every other abstract method; the IDE will flag missing ones.
}
```

Boilerplate-heavy but mechanical (the IDE's "implement members" intent on `MyPathProvider` will list the remaining abstracts). Most plugins never need this — only override when a specific jar must come from somewhere other than the `testArtifacts` configuration.

```kotlin
// test-fixtures/.../runners/AbstractJvmDiagnosticTest.kt
open class AbstractJvmDiagnosticTest : AbstractFirPhasedDiagnosticTest(FirParser.LightTree) {
    override fun createKotlinStandardLibrariesPathProvider() =
        EnvironmentBasedStandardLibrariesPathProvider

    override fun configure(builder: TestConfigurationBuilder) = with(builder) {
        super.configure(builder)
        defaultDirectives {
            // REQUIRED on Kotlin 2.4: without a test phase the run aborts with
            // "Please specify the test phase in `// RUN_PIPELINE_TILL` directive".
            // FRONTEND stops the pipeline after FIR checking — exactly what a
            // diagnostic test needs, and it also avoids the box-test backend steps
            // (so no R8/D8 `NoClassDefFoundError: com/android/tools/r8/origin/Origin`).
            RUN_PIPELINE_TILL with TestPhase.FRONTEND
            +FirDiagnosticsDirectives.FIR_DUMP
            +JvmEnvironmentConfigurationDirectives.FULL_JDK
        }
        configurePlugin()  // see below
    }
}
```

**`RUN_PIPELINE_TILL` is mandatory for diagnostic tests on Kotlin 2.4.** The framework's `PhasedPipelineChecker` (`compiler/tests-common-new/.../services/PhasedPipelineChecker.kt`) fails any run that doesn't declare a phase, with `AssertionFailedError: Please specify the test phase in "// RUN_PIPELINE_TILL" directive`. The official `compiler-plugin-template` runner does **not** set it, so it's an easy trap. `TestPhase` (in `org.jetbrains.kotlin.test.services`) has `FRONTEND`, `FIR2IR`, `BACKEND`; `RUN_PIPELINE_TILL` and `TestPhase` live in `org.jetbrains.kotlin.test.directives` / `...services`. You can set it per-test instead with a `// RUN_PIPELINE_TILL: FRONTEND` line at the top of an individual `testData/*.kt` file, but putting it in the runner's `defaultDirectives` covers every diagnostic fixture at once.

```kotlin
// test-fixtures/.../runners/AbstractJvmBoxTest.kt
open class AbstractJvmBoxTest : AbstractFirBlackBoxCodegenTestBase(FirParser.LightTree) {
    override fun createKotlinStandardLibrariesPathProvider() =
        EnvironmentBasedStandardLibrariesPathProvider

    override fun configure(builder: TestConfigurationBuilder) = with(builder) {
        super.configure(builder)
        defaultDirectives {
            +CodegenTestDirectives.DUMP_IR
            +CodegenTestDirectives.IGNORE_DEXING  // unless you specifically test Android/R8 compatibility
            +FirDiagnosticsDirectives.FIR_DUMP
            +JvmEnvironmentConfigurationDirectives.FULL_JDK
        }
        configurePlugin()
    }
}
```

`IGNORE_DEXING` matters because the default box-test pipeline includes a D8/R8 step. Empirically (verified by running the framework against a `jvm()`-only plugin without R8 on the test classpath), box tests fail at startup with `NoClassDefFoundError: com/android/tools/r8/origin/Origin`; setting `+CodegenTestDirectives.IGNORE_DEXING` in `defaultDirectives` skips that step. Plugins that don't specifically validate Android compatibility should opt out via `IGNORE_DEXING` rather than add R8 as a dependency.

`configurePlugin()` registers the plugin's extensions inside the test compiler:

```kotlin
// test-fixtures/.../services/ExtensionRegistrarConfigurator.kt
fun TestConfigurationBuilder.configurePlugin() {
    useConfigurators(::ExtensionRegistrarConfigurator)
}

internal class ExtensionRegistrarConfigurator(testServices: TestServices)
    : EnvironmentConfigurator(testServices) {
    private val registrar = MyPluginComponentRegistrar()
    override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
        module: TestModule,
        configuration: CompilerConfiguration,
    ) {
        with(registrar) { registerExtensions(configuration) }
    }
}
```

(The configurator class is `internal` so it can be referenced by `::ClassName` from another file in the same module — see "Exposing the plugin's runtime types to testData" below for the second configurator. Don't make it `private`, or `::ExtensionRegistrarConfigurator` from a sibling `services/*.kt` file won't resolve.)

### Exposing the plugin's runtime types to testData

If your testData files `import` plugin-defined annotations (`@MyMarker`) or runtime helper types that the plugin generates calls into, those types must be **on the compilation classpath of the test compiler**, not just on the test runtime classpath. `useCustomRuntimeClasspathProviders` adjusts the *runtime* classpath used to execute `box()` — it does *not* affect what the test compiler can resolve while compiling the testData.

The right hook is a second `EnvironmentConfigurator` that calls `addJvmClasspathRoot(...)`. The path comes from a Gradle system property:

```kotlin
// test-fixtures/.../services/ClasspathConfigurator.kt
internal class ClasspathConfigurator(testServices: TestServices)
    : EnvironmentConfigurator(testServices) {
    override fun configureCompilerConfiguration(
        configuration: CompilerConfiguration,
        module: TestModule,
    ) {
        val jar = System.getProperty("my.plugin.annotations.jar")
            ?: error("system property my.plugin.annotations.jar not set — check tasks.test wiring")
        configuration.addJvmClasspathRoot(File(jar))
    }
}
```

Then extend the existing `configurePlugin()` in `ExtensionRegistrarConfigurator.kt` to chain the new configurator — don't redeclare `configurePlugin()`, that's a duplicate top-level declaration and won't compile:

```kotlin
// edit: test-fixtures/.../services/ExtensionRegistrarConfigurator.kt
fun TestConfigurationBuilder.configurePlugin() {
    useConfigurators(::ExtensionRegistrarConfigurator, ::ClasspathConfigurator)
}
```

In `plugin/build.gradle.kts`, pass the JAR path through a system property:

```kotlin
val annotationsRuntimeClasspath: Configuration by configurations.creating

dependencies {
    annotationsRuntimeClasspath(project(":my-plugin-annotations"))
}

tasks.test {
    dependsOn(annotationsRuntimeClasspath)
    systemProperty("my.plugin.annotations.jar", annotationsRuntimeClasspath.singleFile.absolutePath)
}
```

This is the most-frequently-missing piece — almost every realistic plugin ships an annotation module or a runtime helper module, and without this configurator, testData fails with `Unresolved reference 'MyMarker'`.

### Generated tests

The framework provides a DSL that walks `testData/` and emits one JUnit 5 test class per abstract runner. Add a `main()`:

```kotlin
// test-fixtures/.../GenerateTests.kt
fun main() {
    generateTestGroupSuiteWithJUnit5 {
        testGroup(testDataRoot = "plugin/testData", testsRoot = "plugin/test-gen") {
            testClass<AbstractJvmDiagnosticTest> { model("diagnostics") }
            testClass<AbstractJvmBoxTest> { model("box") }
        }
    }
}
```

`testDataRoot` and `testsRoot` are resolved against the **JavaExec task's `workingDir`** (set to `rootDir` above), not the plugin module. The `"plugin/..."` prefix above assumes the plugin module sits directly under the root project. For a nested module like `my-plugin-compiler/`, write `testDataRoot = "my-plugin-compiler/testData"` instead — getting this wrong produces zero generated tests with no error.

Wire it as a Gradle task that runs before test compilation:

```kotlin
val generateTests by tasks.registering(JavaExec::class) {
    inputs.dir(layout.projectDirectory.dir("testData"))
    outputs.dir(layout.projectDirectory.dir("test-gen"))
    classpath = sourceSets.testFixtures.get().runtimeClasspath
    mainClass.set("com.example.GenerateTestsKt")
    workingDir = rootDir
}
tasks.compileTestKotlin { dependsOn(generateTests) }
// If KSP is enabled or test sources include .java, those tasks also consume test-gen
// outputs. Gradle 8 warns about the implicit dependency; Gradle 9 fails the build.
tasks.matching { it.name == "kspTestKotlin" || it.name == "compileTestJava" }
    .configureEach { dependsOn(generateTests) }
```

Run `./gradlew :plugin:generateTests` to regenerate `test-gen/`. Forgetting to regenerate after adding a new `testData/` file means the new fixture is silently skipped.

### Writing test data

`testData/diagnostics/foo.kt` — `<!MARKER!>code<!>` denotes an expected diagnostic at exactly that range:

```kotlin
@MustBeFinal
open class <!MUST_BE_FINAL_OPEN!>Foo<!>

@MustBeFinal
class Bar
```

`testData/box/foo.kt` — `box()` must return `"OK"`:

```kotlin
fun box(): String {
    val obj = Base()
    return if (obj.generated() == "ok") "OK" else "FAIL"
}
```

Test files may carry `// LANGUAGE: +Feature`, `// FIR_DUMP`, `// DUMP_IR`, `// IGNORE_FIR_DIAGNOSTICS` directive comments. `// FIR_DUMP` and `// DUMP_IR` produce `.fir.txt` / `.ir.txt` golden files next to the test data; subsequent runs compare against those files, failing on mismatch.

### When to choose B over A

- You need to assert IR shape, not just program output (FIR/IR golden-file comparison).
- You want diagnostics pinned to a specific source range, not just "compilation failed".
- You need to run the same fixtures against multiple targets (JVM, JS, Native) without writing per-target Gradle modules.
- You are mirroring or contributing to JetBrains' own plugin tests under `kotlin/plugins/` and want identical conventions.

Trade-offs:
- The API surface has no stability guarantee — a Kotlin patch release can rename or remove abstract base classes.
- The setup is larger (system properties, mixed JUnit 4/5, generated test classes).
- Framework misconfiguration produces error messages that point inside the runner rather than at the user's code; see the gotchas below for how to interpret them.

## Common gotchas

### A: plugin not loaded in sample

The `-Xplugin=` path must point to the plugin JAR. The `compilerPlugin` configuration + `elements.map` pattern resolves this automatically. If the plugin isn't loading, verify:
1. `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar` exists in the plugin JAR
2. The `-Xplugin=` argument is being passed (run with `--info` to see compiler arguments)

### A: diagnostic test passes when it should fail

If `:sample:compileKotlin` succeeds when the plugin should reject the code, the plugin isn't emitting the diagnostic. Common causes:
- The predicate doesn't match the annotation (check fully qualified name)
- The checker is registered but its `check()` method never calls `reporter.reportOn()`
- The `FirExtensionRegistrar` doesn't register the checker

### A: box test output mismatch

When `:sample:run` prints unexpected output, the plugin transformation isn't applying. Use `-verbose` or add `MessageCollector.report(WARNING, ...)` calls in the plugin to trace what's happening during compilation.

### A/B: flaky results from deferred IR validation

Some IR validation runs only during certain backend phases. A test that compiles and runs may pass even though the IR is technically malformed. If you suspect this, add `-Xverify-ir=error` to `freeCompilerArgs` to surface IR validation errors at compile time.

### B: stale generated tests

A new `.kt` file added under `testData/` does not become a test until `generateTests` runs and regenerates `test-gen/`. The Gradle wiring above runs `generateTests` before `compileTestKotlin`, but IDE-driven test runs may skip it — invoke the task manually if a new fixture seems to be ignored.

### B: `NoSuchFileException` / `idea.home.path` not set

Symptom: tests fail before any test data is loaded with errors mentioning `idea.home.path` or a missing kotlin-stdlib jar. Cause: missing `systemProperty(...)` calls in `tasks.test`. With the fail-loud `setLibraryProperty(...)` shown above, a missing `testArtifacts(...)` dependency is surfaced immediately as `error("testArtifacts is missing <jarName>…")` at task configuration; older copies of the helper used `?: return` and silently no-oped, which is what produced the cryptic downstream `NoSuchFileException`. If you inherited the silent variant, switch it to `error(...)` first. Run `./gradlew :plugin:test --info` to see the exact `-D` flags being passed; cross-check that all six `org.jetbrains.kotlin.test.*` properties show non-empty values, and that the `testArtifacts` configuration resolved every coordinate.

### B: framework breaks after a Kotlin version bump

Abstract test bases have been renamed across patch releases (e.g. the `*FirBlackBox*` base shifted between `runners.codegen` and `runners.ir.codegen`). Treat the test runner classes as version-pinned. When bumping Kotlin, also bump every `2.3.x` coordinate in the test wiring together and recompile `test-fixtures/` first.

## Relation to other skills

- **Bootstrap first** → [`compiler-plugin-bootstrap`](../compiler-plugin-bootstrap/guide.md)
- **What you're testing** → any of the `fir-*` or `ir-*` skills
- **Debugging during testing** → [`compiler-plugin-debugging`](../compiler-plugin-debugging/guide.md)
- **Multi-Kotlin-version test matrices** → [`multi-version-kotlin-support`](../multi-version-kotlin-support/guide.md)

## What this skill does NOT cover

- Running the entire Kotlin test suite
- Custom test runners beyond JUnit 5 (and the JUnit 4 runtime that Pattern B requires)
- Performance / benchmarking of compiler plugins
- Fuzz testing or property-based testing of compiler plugins
- Third-party in-process compiler libraries (e.g. `kotlin-compile-testing` / kctfork) — these lag behind compiler API changes; either pattern in this skill is preferable
