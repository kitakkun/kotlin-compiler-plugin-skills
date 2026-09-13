# Changes affecting this skill

API migrations relevant to testing a Kotlin compiler plugin with the official test framework (`kotlin-compiler-internal-test-framework`). This skill targets the **current stable Kotlin** (2.4.20). The artifact's name carries "internal" for a reason — its class hierarchy is reshuffled between patch releases, so entries here are mostly base-class and handler-wiring moves rather than language-level API changes.

## Kotlin 2.4.10 → 2.4.20

### Box-test base class renamed: `AbstractFirBlackBoxCodegenTestBase` → `AbstractJvmBlackBoxCodegenTestBase`

Upstream commit `75cdc5ef74c4` ("[Tests] Simplify `AbstractJvmBlackBoxCodegenTestBase` hierarchy", KT-85292) deleted `compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/runners/codegen/AbstractFirBlackBoxCodegenTest.kt` — the file that declared `AbstractFirBlackBoxCodegenTestBase(val parser: FirParser)`. Its `parser` constructor parameter and FIR handler wiring were folded into the former generic superclass, which is now the class a box runner extends (`runners/codegen/AbstractJvmBlackBoxCodegenTestBase.kt:23-25` at v2.4.20):

```kotlin
abstract class AbstractJvmBlackBoxCodegenTestBase(
    val parser: FirParser,
) : AbstractKotlinCompilerWithTargetBackendTest(TargetBackend.JVM_IR)
```

A runner written against ≤ 2.4.10 (including the one in the official `compiler-plugin-template`) fails to compile on 2.4.20 with `Unresolved reference 'AbstractFirBlackBoxCodegenTestBase'`.

```kotlin
// Before (≤ 2.4.10)
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirBlackBoxCodegenTestBase

open class AbstractJvmBoxTest : AbstractFirBlackBoxCodegenTestBase(FirParser.LightTree) { /* … */ }

// After (2.4.20+)
import org.jetbrains.kotlin.test.runners.codegen.AbstractJvmBlackBoxCodegenTestBase

open class AbstractJvmBoxTest : AbstractJvmBlackBoxCodegenTestBase(FirParser.LightTree) { /* … */ }
```

**Migration**: change the superclass name and its import; the constructor argument (`FirParser.LightTree` / `FirParser.Psi`), the `configure(builder)` override, `super.configure(builder)`, and the `defaultDirectives` block are unchanged. The parser-pinning leaf classes `AbstractFirLightTreeBlackBoxCodegenTest` / `AbstractFirPsiBlackBoxCodegenTest` keep their names (`AbstractJvmBlackBoxCodegenTestBase.kt:64-67`) — still don't extend them, for the reason given in guide.md. The diagnostic base `AbstractFirPhasedDiagnosticTest(val parser: FirParser)` is untouched (`runners/AbstractFirPhasedDiagnosticTest.kt:30`).

Note that on ≤ 2.4.10 `AbstractJvmBlackBoxCodegenTestBase` was a *generic* class (`AbstractJvmBlackBoxCodegenTestBase<R : ResultingArtifact.FrontendOutput<R>>(val targetFrontend: FrontendKind<R>)` with abstract facade properties). If you support both versions from one source set, keep separate runner sources per Kotlin version — there is no spelling that compiles against both.

### Failure suppressors split out of after-analysis checkers (internal wiring, visible only if you touch the checker lists)

Several checkers that used to be registered with `useAfterAnalysisCheckers(...)` are now `TestFailureSuppressor`s registered with `useFailureSuppressors(...)`: `PhasedPipelineChecker` and `FirFailingTestSuppressor` in `AbstractFirPhasedDiagnosticTest` (`:62`), `BlackBoxCodegenSuppressor` in `AbstractJvmBlackBoxCodegenTestBase` (`:57`), and `IrValidationErrorChecker` in `AbstractKotlinCompilerTest.defaultConfiguration` (`:65`). `RUN_PIPELINE_TILL` is still mandatory for diagnostic tests and the failure message is unchanged (`services/PhasedPipelineChecker.kt:128`). Nothing to do unless your runner removes or reorders these checkers by hand.

### Directive churn (none of it affects the directives this guide uses)

`CodegenTestDirectives` dropped `DUMP_SIGNATURES`, `SEPARATE_SIGNATURE_DUMP_FOR_K2`, `MUTE_SIGNATURE_COMPARISON_K2` and changed `ENABLE_IR_NESTED_OFFSETS_CHECKS` from a string directive to a flag; `FirDiagnosticsDirectives` dropped `FIR_IDENTICAL`. `DUMP_IR`, `IGNORE_DEXING`, `IGNORE_FIR_DIAGNOSTICS`, `FIR_DUMP`, `FULL_JDK`, `WITH_STDLIB`, `LANGUAGE`, and `RUN_PIPELINE_TILL` are unchanged.
