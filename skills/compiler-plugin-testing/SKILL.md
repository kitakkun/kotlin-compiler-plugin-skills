---
name: compiler-plugin-testing
description: "Test Kotlin compiler plugins with one of two infrastructures — (A) Gradle-based integration tests where `sample/` modules compile real source with `-Xplugin=` and assert on success/failure/output, or (B) JetBrains' official compiler test framework consumed via the published `kotlin-compiler-internal-test-framework` artifact, which enables FIR/IR-level fixture tests with `<!DIAGNOSTIC!>` markers, `fun box(): String`, `// FIR_DUMP`, `// DUMP_IR` and golden-file comparison. Read compiler-plugin-bootstrap first. NOT a tutorial on JUnit basics or third-party in-process compiler libraries."
---

# Compiler Plugin Testing

## Principle

Test a compiler plugin by **compiling real Kotlin source with the plugin loaded and checking the result** — either that compilation fails with an expected diagnostic, or that compilation succeeds and the program produces expected output. This is what JetBrains does for every plugin under `kotlin/plugins/`, and it's what standalone plugin projects should do too.

There are two infrastructures available, and they are not mutually exclusive — projects often start with (A) and add (B) later when they need IR/FIR-level assertions.

| Infrastructure | When to use | Cost |
|---|---|---|
| **A. Gradle integration tests** | Smoke tests, demos, simple "does the plugin load and do its job" checks | Trivial — one extra Gradle module, no extra dependencies |
| **B. Official compiler test framework** | FIR dump comparison, IR dump comparison, `<!DIAGNOSTIC!>` markers at exact source ranges, multi-module test data, K/JS+K/JVM matrix | Higher — extra dependency, system properties, JUnit 4+5 mix, brittle to compiler version bumps |

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
    kotlin("jvm") version "2.3.21"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.3.21")
}
```

### `sample/build.gradle.kts` — diagnostic test

For a plugin that emits custom diagnostics (checker), the goal is to verify that **compilation fails** with the expected error:

```kotlin
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
    kotlin("jvm") version "2.3.21"
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
    kotlin("jvm") version "2.3.21"
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
    compileOnly("org.jetbrains.kotlin:kotlin-compiler:2.3.21")

    // Test framework — testFixtures so test runners can be reused
    testFixturesApi("org.jetbrains.kotlin:kotlin-test-junit5:2.3.21")
    testFixturesApi("org.jetbrains.kotlin:kotlin-compiler-internal-test-framework:2.3.21")
    testFixturesApi("org.jetbrains.kotlin:kotlin-compiler:2.3.21")
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
    testArtifacts("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
    testArtifacts("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.3.21")
    testArtifacts("org.jetbrains.kotlin:kotlin-reflect:2.3.21")
    testArtifacts("org.jetbrains.kotlin:kotlin-test:2.3.21")
    testArtifacts("org.jetbrains.kotlin:kotlin-script-runtime:2.3.21")
    testArtifacts("org.jetbrains.kotlin:kotlin-annotations-jvm:2.3.21")
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
        ?: return  // silently no-ops if the dependency is missing — see gotcha below
    systemProperty(propName, path)
}
```

### Test runner classes (in `test-fixtures/`)

A diagnostic test runner extends `AbstractFirPhasedDiagnosticTest`; a box test runner extends `AbstractFirBlackBoxCodegenTestBase`. Both classes come from the test framework artifact. Imports are elided in the snippets below; every type referenced is in one of these specific packages:

| Type | Package |
|---|---|
| `FirParser` | `org.jetbrains.kotlin.test` |
| `TestConfigurationBuilder` | `org.jetbrains.kotlin.test.builders` |
| `FirDiagnosticsDirectives`, `JvmEnvironmentConfigurationDirectives`, `CodegenTestDirectives` | `org.jetbrains.kotlin.test.directives` |
| `AbstractFirPhasedDiagnosticTest` | `org.jetbrains.kotlin.test.runners` |
| `AbstractFirBlackBoxCodegenTestBase` | `org.jetbrains.kotlin.test.runners.codegen` |
| `EnvironmentBasedStandardLibrariesPathProvider`, `KotlinStandardLibrariesPathProvider` | `org.jetbrains.kotlin.test.services` |

The official template's `compiler-plugin/test-fixtures/.../runners/*.kt` files have the exact import lists for the Kotlin version it tracks.

```kotlin
// test-fixtures/.../runners/AbstractJvmDiagnosticTest.kt
open class AbstractJvmDiagnosticTest : AbstractFirPhasedDiagnosticTest(FirParser.LightTree) {
    override fun createKotlinStandardLibrariesPathProvider() =
        EnvironmentBasedStandardLibrariesPathProvider

    override fun configure(builder: TestConfigurationBuilder) = with(builder) {
        super.configure(builder)
        defaultDirectives {
            +FirDiagnosticsDirectives.FIR_DUMP
            +JvmEnvironmentConfigurationDirectives.FULL_JDK
        }
        configurePlugin()  // see below
    }
}
```

```kotlin
// test-fixtures/.../runners/AbstractJvmBoxTest.kt
open class AbstractJvmBoxTest : AbstractFirBlackBoxCodegenTestBase(FirParser.LightTree) {
    override fun createKotlinStandardLibrariesPathProvider() =
        EnvironmentBasedStandardLibrariesPathProvider

    override fun configure(builder: TestConfigurationBuilder) = with(builder) {
        super.configure(builder)
        defaultDirectives {
            +CodegenTestDirectives.DUMP_IR
            +FirDiagnosticsDirectives.FIR_DUMP
            +JvmEnvironmentConfigurationDirectives.FULL_JDK
        }
        configurePlugin()
    }
}
```

`configurePlugin()` registers the plugin's extensions inside the test compiler:

```kotlin
// test-fixtures/.../services/ExtensionRegistrarConfigurator.kt
fun TestConfigurationBuilder.configurePlugin() {
    useConfigurators(::ExtensionRegistrarConfigurator)
}

private class ExtensionRegistrarConfigurator(testServices: TestServices)
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

Symptom: tests fail before any test data is loaded with errors mentioning `idea.home.path` or a missing kotlin-stdlib jar. Cause: missing `systemProperty(...)` calls in `tasks.test`, often because a `setLibraryProperty(...)` helper silently no-oped when its corresponding `testArtifacts(...)` dependency was misspelled or absent. Run `./gradlew :plugin:test --info` to see the exact `-D` flags being passed; cross-check that all six `org.jetbrains.kotlin.test.*` properties show non-empty values, and that the `testArtifacts` configuration resolved every coordinate.

### B: framework breaks after a Kotlin version bump

Abstract test bases have been renamed across patch releases (e.g. the `*FirBlackBox*` base shifted between `runners.codegen` and `runners.ir.codegen`). Treat the test runner classes as version-pinned. When bumping Kotlin, also bump every `2.3.x` coordinate in the test wiring together and recompile `test-fixtures/` first.

## Relation to other skills

- **Bootstrap first** → `compiler-plugin-bootstrap`
- **What you're testing** → any of the `fir-*` or `ir-*` skills
- **Debugging during testing** → `compiler-plugin-debugging`
- **Multi-Kotlin-version test matrices** → `multi-version-kotlin-support`

## What this skill does NOT cover

- Running the entire Kotlin test suite
- Custom test runners beyond JUnit 5 (and the JUnit 4 runtime that Pattern B requires)
- Performance / benchmarking of compiler plugins
- Fuzz testing or property-based testing of compiler plugins
- Third-party in-process compiler libraries (e.g. `kotlin-compile-testing` / kctfork) — these lag behind compiler API changes; either pattern in this skill is preferable
