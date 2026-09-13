# Verification 17 — `FirNamedFunctionChecker` / `namedFunctionCheckers` (Kotlin 2.4.20)

## Goal

Verify the Kotlin 2.4.10 → 2.4.20 rename documented in
`references/fir-additional-checkers-extension/{guide.md,CHANGES.md}`:

- The checker for named functions is `FirNamedFunctionChecker` (the `FirSimpleFunctionChecker`
  alias is gone, with no deprecated forwarding alias).
- The `DeclarationCheckers` bucket is `namedFunctionCheckers` (was `simpleFunctionCheckers`).
- The `check` override uses context parameters
  (`context(context: CheckerContext, reporter: DiagnosticReporter) override fun check(declaration: FirNamedFunction)`)
  and the plugin module compiles **without** `-Xcontext-parameters` on 2.4.20.
- A diagnostic declared in a `KtDiagnosticsContainer` registered through
  `registerDiagnosticContainers` can be positioned on the name via
  `SourceElementPositioningStrategies.NAME_IDENTIFIER`.

The plugin reports an error `NO_SHOUTING` on any named function whose name is all upper-case.

## Project layout

- `plugin/` — `CompilerPluginRegistrar` (`pluginId`, `supportsK2 = true`) → `FirExtensionRegistrar`
  registering `NoShoutingCheckersExtension` (`FirAdditionalCheckersExtension`) and
  `NoShoutingDiagnostics` (`KtDiagnosticsContainer`, `NO_SHOUTING by error1<KtNamedFunction, String>(NAME_IDENTIFIER)`).
  `NoShoutingChecker : FirNamedFunctionChecker(MppCheckerKind.Common)` is registered in
  `namedFunctionCheckers`. No `-Xcontext-parameters` flag.
- `sample/` — loads the plugin via the `compilerPlugin` configuration + `-Xplugin=`, plus
  `-Xrender-internal-diagnostic-names` so the factory name appears in the output. Contains
  `fun quiet()` (must compile) and `fun SHOUT()` (must be rejected).

## PASS criterion

```bash
cd verification/17-named-function-checker
../gradlew --no-daemon clean :sample:compileKotlin
```

fails, and the output contains a `NO_SHOUTING` error naming `SHOUT`, positioned on the name
identifier (`Sample.kt:7:5`, i.e. the `S` of `SHOUT` after `fun `). No diagnostic on `quiet`.

Additionally (scratch, not committed): replacing `FirNamedFunctionChecker` /
`namedFunctionCheckers` with the pre-2.4.20 names must fail `:plugin:compileKotlin` with an
unresolved reference, and adding `-Xcontext-parameters` to the plugin module must only produce a
"redundant" warning. Both observations are recorded in `RESULT.md`.

## FAIL criterion

- `:plugin:compileKotlin` fails (e.g. `FirNamedFunctionChecker` / `namedFunctionCheckers`
  unresolved, or the context-parameter override needs `-Xcontext-parameters`), or
- `:sample:compileKotlin` succeeds, or fails without the `NO_SHOUTING` diagnostic, or the
  diagnostic is not positioned on the name identifier.

## Skills consulted

- `references/fir-additional-checkers-extension/guide.md` (sections 2–6, "Why context parameters?",
  "`-Xcontext-parameters` is no longer needed", "Reporter API patterns",
  "`-Xrender-internal-diagnostic-names`")
- `references/fir-additional-checkers-extension/CHANGES.md` (`## Kotlin 2.4.10 → 2.4.20`)
- `references/fir-additional-checkers-extension/EVIDENCE.md` (typealias / bucket claims)
- `references/compiler-plugin-bootstrap/guide.md` (project shape, mirrored from probe 01/12)

## Reference patterns from kotlin v2.4.20 (paths)

- `compiler/fir/checkers/gen/org/jetbrains/kotlin/fir/analysis/checkers/declaration/FirDeclarationCheckerAliases.kt:39`
  — `typealias FirNamedFunctionChecker = FirDeclarationChecker<FirNamedFunction>`
- `compiler/fir/checkers/gen/org/jetbrains/kotlin/fir/analysis/checkers/declaration/DeclarationCheckers.kt:26,52`
  — `open val namedFunctionCheckers`, `allNamedFunctionCheckers`
- `compiler/fir/checkers/src/org/jetbrains/kotlin/fir/analysis/checkers/declaration/FirDeclarationChecker.kt:16-17`
  — `context(context: CheckerContext, reporter: DiagnosticReporter) abstract fun check(declaration: D)`
- `compiler/fir/checkers/src/org/jetbrains/kotlin/fir/analysis/checkers/CommonDeclarationCheckers.kt:77`
  — upstream `namedFunctionCheckers` usage
- `compiler/frontend.common-psi/src/org/jetbrains/kotlin/diagnostics/SourceElementPositioningStrategies.kt:303`
  — `NAME_IDENTIFIER`
- `compiler/frontend.common-psi/src/org/jetbrains/kotlin/diagnostics/KtDiagnosticFactoryDsl.kt:78` — `error1`
- `compiler/frontend.common/src/org/jetbrains/kotlin/diagnostics/KtDiagnosticReportHelpers.kt:36,46`
  — `context(context: DiagnosticContext) DiagnosticReporter.reportOn(...)` overloads
- `git grep FirSimpleFunctionChecker v2.4.20 -- compiler/fir/checkers` returns nothing
