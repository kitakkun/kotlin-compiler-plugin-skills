# Evidence for guide.md

All quoted snippets below originate from projects under the **Apache License 2.0**:

- JetBrains/kotlin — Copyright 2010-2024 JetBrains s.r.o and respective authors and developers
- Kotlin/compiler-plugin-template — Apache-2.0

Original copyright applies to each snippet. See [`../../NOTICE.md`](../../NOTICE.md) for the consolidated attribution required by Apache 2.0 § 4(b)/(d).

## Test-data directives

### Claim: `// FIR_DUMP` — "Compare FIR text dump against a `.fir.txt` golden"
- **File**: [`kotlin/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/directives/FirDiagnosticsDirectives.kt:40`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/directives/FirDiagnosticsDirectives.kt#L40)
- **Snippet**:
  ```kotlin
  val FIR_DUMP by directive(
      description = """
          Dumps resulting fir to `testName.fir.txt` file
      """.trimIndent(),
      applicability = Global
  )
  ```

### Claim: `// LANGUAGE: +Feature / -Feature`
- **File**: [`kotlin/compiler/test-infrastructure/testFixtures/org/jetbrains/kotlin/test/directives/LanguageSettingsDirectives.kt:13`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/test-infrastructure/testFixtures/org/jetbrains/kotlin/test/directives/LanguageSettingsDirectives.kt#L13)
- **Snippet**:
  ```kotlin
  val LANGUAGE by stringDirective(
      description = """
          List of enabled and disabled language features.
          Usage: // LANGUAGE: +SomeFeature -OtherFeature
      """.trimIndent()
  )
  ```
  No `!` prefix in the current syntax (confirmed by usage line).

### Claim: `// DUMP_IR` — "Dump the generated IR for inspection"
- **File**: [`kotlin/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/directives/CodegenTestDirectives.kt:103`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/directives/CodegenTestDirectives.kt#L103)
- **Snippet**:
  ```kotlin
  val DUMP_IR by directive(
      description = "Dumps generated backend IR (enables ${IrTextDumpHandler::class})"
  )
  ```

### Claim: `// IGNORE_FIR_DIAGNOSTICS` — "Suppress FIR diagnostic checking"
- **File**: [`kotlin/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/directives/CodegenTestDirectives.kt:91`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/directives/CodegenTestDirectives.kt#L91)
- **Snippet**:
  ```kotlin
  val IGNORE_FIR_DIAGNOSTICS by directive(
      description = "Run backend even FIR reported some diagnostics with ERROR severity"
  )
  ```

### Claim: `<!DIAGNOSTIC!>code<!>` inline marker syntax
- **File**: [`kotlin/compiler/test-infrastructure-utils/testFixtures/org/jetbrains/kotlin/codeMetaInfo/CodeMetaInfoParser.kt:11`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/test-infrastructure-utils/testFixtures/org/jetbrains/kotlin/codeMetaInfo/CodeMetaInfoParser.kt#L11)
- **Snippet**:
  ```kotlin
  val openingRegex = """(<!([^"]*?((".*?")(, ".*?")*?)?[^"]*?)!>)""".toRegex()
  val closingRegex = """(<!>)""".toRegex()
  ```

## `AbstractKotlinCompilerTest` location

### Claim: `configure(builder)` is the abstract user hook, `configuration` is a property
- **File**: [`kotlin/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/runners/AbstractKotlinCompilerTest.kt:63-107`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/runners/AbstractKotlinCompilerTest.kt#L63-L107)
- **Snippet**:
  ```kotlin
  protected val configuration: TestConfigurationBuilder.() -> Unit = {
      defaultConfiguration()
      // …
      configureInternal(this)
      // …
  }

  /**
   * This is the main method to declare the test configuration.
   * …
   * If you inherit your test runner which already has an implemented [configure] method,
   * then you ALWAYS need to call `super.configure(builder)` before expanding the test configuration.
   */
  abstract fun configure(builder: TestConfigurationBuilder)
  ```
  Confirms: `configuration` is a `val` (lambda-typed property) — overriding it as `fun configuration(...)` does not compile; the user-facing extension point is the abstract `configure(builder)`.

### Claim: "the common base class" lives in `tests-common-new/testFixtures`
- **File**: [`kotlin/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/runners/AbstractKotlinCompilerTest.kt:29`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/runners/AbstractKotlinCompilerTest.kt#L29)
- **Snippet**:
  ```kotlin
  abstract class AbstractKotlinCompilerTest {
      companion object {
          val defaultDirectiveContainers = listOf(
              ConfigurationDirectives,
              LanguageSettingsDirectives
          )
  ```

## Empirically-observed framework behaviours (no permalink — sourced from running the framework)

### Claim: `IGNORE_DEXING` skips a D8/R8 step that requires `com.android.tools.r8.origin.Origin`
- **Status**: Observed empirically when running a `kotlin("jvm")`-only plugin's `:plugin:test` task without R8 on the test classpath. Symptom: `NoClassDefFoundError: com/android/tools/r8/origin/Origin` at test startup, before any test data is loaded. Adding `+CodegenTestDirectives.IGNORE_DEXING` to `defaultDirectives` resolves it. No permalink — the relevant pipeline wiring lives in the test framework's backend handlers and is not stable across patch releases.
- See also: the `IGNORE_DEXING` directive itself is declared at [`CodegenTestDirectives.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/directives/CodegenTestDirectives.kt) (search for the symbol — line number drifts).

### Claim: extending concrete `AbstractFirLightTreeBlackBoxCodegenTest` / `AbstractFirPsiBlackBoxCodegenTest` as a test base throws `IllegalArgumentException`
- **Status**: Observed empirically. Those classes implement `RunnerWithTargetBackendForTestGeneratorMarker`; the generator-side check that produces the exception lives in the test-generator infrastructure. The corrective action is to extend the parameterized `*Base` class instead. Permalink omitted because the exception originates from a private generator-side `require(...)` that has been renamed between patch releases.

## Plugin path conventions

### Claim: `parcelize-compiler/parcelize.k2/` exists for K2 frontend extension sources
- **Path**: `kotlin/plugins/parcelize/parcelize-compiler/parcelize.k2/`
- Sibling directories at `parcelize-compiler/` at v2.4.10: `parcelize.backend`, `parcelize.cli`, `parcelize.common`, `parcelize.k1`, `parcelize.k2`, `testData`, `testFixtures`, `tests` (and `build.gradle.kts`). Note: parcelize dropped its separate `tests-gen/` directory in 2.4.0 — the generated suites now live under `tests/`.
- Generated JUnit test classes are produced by `generateTestGroupSuiteWithJUnit5` from `testData/` — the same mechanism Pattern B in guide.md exposes for standalone projects (where it lives at `compiler-plugin/test-gen/`). Many monorepo plugins still keep these in a dedicated `tests-gen/` directory; parcelize folded them into `tests/`.

### Claim: `allopen/testFixtures/` (tests live at the `allopen/` level, not inside `allopen.k2/`)
- **Path**: `kotlin/plugins/allopen/testFixtures/org/`
- Sibling directories at `allopen/`: `allopen.cli`, `allopen.common`, `allopen.embeddable`, `allopen.k1`, `allopen.k2`, `testData`, `testFixtures`. No `testFixtures` exists under `allopen.k2/`.

### Claim: `power-assert/` gained a `power-assert-compiler/` parent in 2.4.0
- **Path**: `kotlin/plugins/power-assert/power-assert-compiler/`
- Direct subdirectories of `power-assert/` at v2.4.10: `power-assert-compiler`, `power-assert-runtime`. Under `power-assert-compiler/`: `power-assert.backend`, `power-assert.cli`, `power-assert.common`, `power-assert.embeddable`, `power-assert.frontend`, `testData`, `testFixtures` (and `build.gradle.kts`).
- Through 2.3.x `power-assert/` used a *flat* layout (the implementation modules — `power-assert.backend`, `power-assert.cli`, `power-assert.embeddable` — sat directly under the plugin root, with no intermediary). In 2.4.0 it was restructured to the `parcelize`-style `<plugin>-compiler/` shape, plus a separate `power-assert-runtime/` module.

## `box(): String` runner convention

### Claim: "must return exactly `\"OK\"` to pass"
- **File**: [`kotlin/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/backend/handlers/JvmBoxRunner.kt:47`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/backend/handlers/JvmBoxRunner.kt#L47)
- **Snippet**:
  ```kotlin
  private const val DEFAULT_EXPECTED_RESULT = "OK"
  ```

### Claim: "The runner ... executes `box()`"
- **File**: [`kotlin/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/backend/handlers/JvmBoxRunner.kt:311`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/backend/handlers/JvmBoxRunner.kt#L311)
- **Snippet**:
  ```kotlin
  private fun Class<*>.getBoxMethodOrNull(): Method? {
      return try {
          getMethod("box")
      } catch (e: NoSuchMethodException) {
          return null
      }
  }
  ```

### Claim: Runner invokes `box()` and compares its `String` return
- **File**: [`kotlin/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/backend/handlers/JvmBoxRunner.kt:170`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/backend/handlers/JvmBoxRunner.kt#L170)
- **Snippet**:
  ```kotlin
  method.invoke(null) as String
  ```

### Claim: "Codegen test should contain one global `fun box()`"
- **File**: [`kotlin/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/services/SplittingModuleTransformerForBoxTests.kt:50`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/services/SplittingModuleTransformerForBoxTests.kt#L50)
- **Snippet**:
  ```kotlin
  val boxFiles = realFiles.filter { it.originalContent.contains("fun box()") || it.name == "entry.mjs" }
  ```

## Pattern B: official test framework available to standalone projects

### Claim: `kotlin-compiler-internal-test-framework` is published as a Maven artifact

JetBrains' [`Kotlin/compiler-plugin-template`](https://github.com/Kotlin/compiler-plugin-template) — the official template for writing a Kotlin compiler plugin — depends on this artifact directly via Gradle and is consumed by external (non-monorepo) projects. The artifact's group/name pair is `org.jetbrains.kotlin:kotlin-compiler-internal-test-framework`, published per Kotlin version.

Maven Central existence checked directly (HTTP 200 on the POM):
- `https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compiler-internal-test-framework/2.3.20/kotlin-compiler-internal-test-framework-2.3.20.pom`
- `https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compiler-internal-test-framework/2.3.21/kotlin-compiler-internal-test-framework-2.3.21.pom`

Note on version: the template's `gradle/libs.versions.toml` (linked snippet below) currently pins `kotlin = "2.3.20"`, while this skill standardizes on `2.4.0`. The published artifact exists at all of these versions, and the two abstract bases referenced later in this section are unchanged across them (signatures identical at v2.3.20, v2.3.21, and v2.4.0; only line numbers drifted) — verified by direct comparison:

- [`AbstractFirPhasedDiagnosticTest.kt` at v2.3.20 line 36](https://github.com/JetBrains/kotlin/blob/v2.3.20/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/runners/AbstractFirPhasedDiagnosticTest.kt#L36) and [v2.4.0 line 39](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/runners/AbstractFirPhasedDiagnosticTest.kt#L39): identical signature `abstract class AbstractFirPhasedDiagnosticTest(val parser: FirParser) : AbstractKotlinCompilerWithTargetBackendTest(TargetBackend.JVM_IR)`.
- [`AbstractFirBlackBoxCodegenTest.kt` at v2.3.20 line 29](https://github.com/JetBrains/kotlin/blob/v2.3.20/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/runners/codegen/AbstractFirBlackBoxCodegenTest.kt#L29) and [v2.4.0 line 31](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/runners/codegen/AbstractFirBlackBoxCodegenTest.kt#L31): identical declaration `abstract class AbstractFirBlackBoxCodegenTestBase(val parser: FirParser) : AbstractJvmBlackBoxCodegenTestBase<FirOutputArtifact>(FrontendKinds.FIR)`.

- **File**: [`Kotlin/compiler-plugin-template/gradle/libs.versions.toml`](https://github.com/Kotlin/compiler-plugin-template/blob/c94e164ac8e970cbf252066c1d233d5787635c29/gradle/libs.versions.toml)
- **Snippet** (excerpt):
  ```toml
  [versions]
  kotlin = "2.3.20"

  [libraries]
  kotlin-test-framework = { group = "org.jetbrains.kotlin", name = "kotlin-compiler-internal-test-framework", version.ref = "kotlin" }
  kotlin-test-junit5    = { group = "org.jetbrains.kotlin", name = "kotlin-test-junit5",                       version.ref = "kotlin" }
  kotlin-compiler       = { group = "org.jetbrains.kotlin", name = "kotlin-compiler",                          version.ref = "kotlin" }
  ```

The word "internal" in the artifact name signals that the API surface has no stability guarantee across compiler versions — *not* that the artifact is unpublished or unusable from outside the monorepo.

### Claim: official template wires the test framework via `testFixtures` dependencies + system properties

- **File**: [`Kotlin/compiler-plugin-template/compiler-plugin/build.gradle.kts`](https://github.com/Kotlin/compiler-plugin-template/blob/c94e164ac8e970cbf252066c1d233d5787635c29/compiler-plugin/build.gradle.kts)
- **Dependency snippet**:
  ```kotlin
  dependencies {
      compileOnly(libs.kotlin.compiler)

      testFixturesApi(libs.kotlin.test.junit5)
      testFixturesApi(libs.kotlin.test.framework)   // kotlin-compiler-internal-test-framework
      testFixturesApi(libs.kotlin.compiler)
      testFixturesRuntimeOnly(libs.junit)            // junit:junit:4.13.2

      testArtifacts(libs.kotlin.stdlib)
      testArtifacts(libs.kotlin.stdlib.jdk8)
      testArtifacts(libs.kotlin.reflect)
      testArtifacts(libs.kotlin.test)
      testArtifacts(libs.kotlin.script.runtime)
      testArtifacts(libs.kotlin.annotations.jvm)
  }
  ```
- **Test task wiring snippet**:
  ```kotlin
  tasks.test {
      dependsOn(testArtifacts)
      useJUnitPlatform()
      workingDir = rootDir

      setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib",            "kotlin-stdlib")
      setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib-jdk8",       "kotlin-stdlib-jdk8")
      setLibraryProperty("org.jetbrains.kotlin.test.kotlin-reflect",           "kotlin-reflect")
      setLibraryProperty("org.jetbrains.kotlin.test.kotlin-test",              "kotlin-test")
      setLibraryProperty("org.jetbrains.kotlin.test.kotlin-script-runtime",    "kotlin-script-runtime")
      setLibraryProperty("org.jetbrains.kotlin.test.kotlin-annotations-jvm",   "kotlin-annotations-jvm")

      systemProperty("idea.ignore.disabled.plugins", "true")
      systemProperty("idea.home.path", rootDir)
  }
  ```
- This file is the authoritative reference for the system properties listed in guide.md Pattern B.

### Claim: standalone runners extend the same abstract bases as monorepo plugin tests

- **File**: [`compiler-plugin/test-fixtures/org/jetbrains/kotlin/compiler/plugin/template/runners/AbstractJvmDiagnosticTest.kt`](https://github.com/Kotlin/compiler-plugin-template/blob/c94e164ac8e970cbf252066c1d233d5787635c29/compiler-plugin/test-fixtures/org/jetbrains/kotlin/compiler/plugin/template/runners/AbstractJvmDiagnosticTest.kt)
- **Snippet**:
  ```kotlin
  open class AbstractJvmDiagnosticTest : AbstractFirPhasedDiagnosticTest(FirParser.LightTree) {
      override fun configure(builder: TestConfigurationBuilder) = with(builder) {
          super.configure(builder)
          defaultDirectives {
              +FirDiagnosticsDirectives.FIR_DUMP
              +JvmEnvironmentConfigurationDirectives.FULL_JDK
          }
          configurePlugin()
      }
  }
  ```
- **File**: [`compiler-plugin/test-fixtures/org/jetbrains/kotlin/compiler/plugin/template/runners/AbstractJvmBoxTest.kt`](https://github.com/Kotlin/compiler-plugin-template/blob/c94e164ac8e970cbf252066c1d233d5787635c29/compiler-plugin/test-fixtures/org/jetbrains/kotlin/compiler/plugin/template/runners/AbstractJvmBoxTest.kt)
- **Snippet**:
  ```kotlin
  open class AbstractJvmBoxTest : AbstractFirBlackBoxCodegenTestBase(FirParser.LightTree) {
      override fun configure(builder: TestConfigurationBuilder) = with(builder) {
          super.configure(this)
          defaultDirectives {
              +CodegenTestDirectives.DUMP_IR
              +FirDiagnosticsDirectives.FIR_DUMP
              +JvmEnvironmentConfigurationDirectives.FULL_JDK
          }
          configurePlugin()
      }
  }
  ```
- Both `AbstractFirPhasedDiagnosticTest` and `AbstractFirBlackBoxCodegenTestBase` are exported by `kotlin-compiler-internal-test-framework`; nothing here requires running inside the Kotlin monorepo.

### Claim: plugin extensions are registered via `EnvironmentConfigurator` inside the test runner

- **File**: [`compiler-plugin/test-fixtures/org/jetbrains/kotlin/compiler/plugin/template/services/ExtensionRegistrarConfigurator.kt`](https://github.com/Kotlin/compiler-plugin-template/blob/c94e164ac8e970cbf252066c1d233d5787635c29/compiler-plugin/test-fixtures/org/jetbrains/kotlin/compiler/plugin/template/services/ExtensionRegistrarConfigurator.kt)
- **Snippet**:
  ```kotlin
  fun TestConfigurationBuilder.configurePlugin() {
      useConfigurators(::ExtensionRegistrarConfigurator)
  }

  private class ExtensionRegistrarConfigurator(testServices: TestServices)
      : EnvironmentConfigurator(testServices) {
      private val registrar = SimplePluginComponentRegistrar()
      override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
          module: TestModule,
          configuration: CompilerConfiguration,
      ) {
          with(registrar) { registerExtensions(configuration) }
      }
  }
  ```

### Claim: JUnit 5 test classes are auto-generated by `generateTestGroupSuiteWithJUnit5`

- **File**: [`compiler-plugin/test-fixtures/org/jetbrains/kotlin/compiler/plugin/template/GenerateTests.kt`](https://github.com/Kotlin/compiler-plugin-template/blob/c94e164ac8e970cbf252066c1d233d5787635c29/compiler-plugin/test-fixtures/org/jetbrains/kotlin/compiler/plugin/template/GenerateTests.kt)
- **Snippet**:
  ```kotlin
  fun main() {
      generateTestGroupSuiteWithJUnit5 {
          testGroup(testDataRoot = "compiler-plugin/testData", testsRoot = "compiler-plugin/test-gen") {
              testClass<AbstractJvmDiagnosticTest> { model("diagnostics") }
              testClass<AbstractJvmBoxTest>        { model("box") }
              testClass<AbstractJsBoxTest>         { model("box") }
          }
      }
  }
  ```
- **README directive** (same repo): "*The generated JUnit 5 test classes will be updated automatically when tests are next run. They can be manually updated with the `generateTests` Gradle task as well.*"
