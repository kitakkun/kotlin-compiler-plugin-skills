# 01-additional-checkers — RESULT

**Status:** PASS

## Summary

The plugin uses `FirAdditionalCheckersExtension` with a `FirRegularClassChecker` to
emit a custom error diagnostic on classes annotated with `@com.example.MustBeFinal`
that are also declared `open`. The sample module compilation fails exactly as
expected.

## Observed compiler output

```
e: file:///.../verification/01-additional-checkers/sample/src/main/kotlin/Sample.kt:11:1 Class annotated @MustBeFinal must not be open
```

- Line 11 is `@MustBeFinal open class Bad` — diagnostic fires (expected).
- Line 8 is `@MustBeFinal class Ok` — no diagnostic (predicate correctly
  filters by both annotation and `Modality.OPEN`).
- Build status: `BUILD FAILED` (single error, no others).

## Implementation notes

- Custom diagnostic: `MustBeFinalDiagnostics.MUST_BE_FINAL_OPEN` declared via
  `error0<KtClass>(SourceElementPositioningStrategies.MODALITY_MODIFIER)` inside a
  `KtDiagnosticsContainer`. Renderer message:
  `"Class annotated @MustBeFinal must not be open"`.
- `FirExtensionRegistrar` registers the extension factory and calls
  `registerDiagnosticContainers(MustBeFinalDiagnostics)` (required — without it
  the FIR pipeline rejects the unknown factory at report time).
- The checker uses the FIR predicate system
  (`DeclarationPredicate.create { annotated(MUST_BE_FINAL_FQN) }`) and queries
  `session.predicateBasedProvider.matches(...)`; the predicate is registered via
  the extension's `registerPredicates()` override.
- The plugin module uses `-Xcontext-parameters` so the
  `context(context: CheckerContext, reporter: DiagnosticReporter) override fun check(...)`
  override compiles against Kotlin 2.3.21 (still experimental).
- The sample module wires the plugin via the standard `compilerPlugin`
  configuration and `-Xplugin=<jar>` free compiler arg, identical to the
  `01-hello-plugin` template.

## Issues encountered

- Initial compile of the plugin failed with
  `Class 'MustBeFinalComponentRegistrar' is not abstract and does not implement
  abstract base class member: val pluginId: String`. Fixed by adding
  `override val pluginId: String = "com.example.must-be-final"` (the template
  provides this too but it was easy to miss).

## Reproduce

```bash
cd verification/01-additional-checkers
../gradlew :sample:compileKotlin
```

## Re-run on Kotlin 2.4.20

**Status: PASS** (unchanged from the 2.3.21 result; no source changes were needed, only the version pins in `build.gradle.kts`).

```
$ ../gradlew --no-daemon -q clean :sample:compileKotlin
e: file://01-additional-checkers/sample/src/main/kotlin/Sample.kt:11:1 Class annotated @MustBeFinal must not be open
FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':sample:compileKotlin' (registered by plugin 'org.jetbrains.kotlin.jvm').
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.btapi.BuildToolsApiCompilationWork
   > Compilation error. See log for more details
(exit code 1, as required for a diagnostic-style probe)
```

Validated with Kotlin 2.4.20, Gradle 9.5.0, JDK 21 on 2026-09-10.
