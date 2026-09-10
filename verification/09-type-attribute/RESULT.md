# 09-type-attribute — RESULT

**Status:** PASS

## Summary

The plugin uses `FirTypeAttributeExtension` to attach a `ConePositiveAttribute` to
any `ConeKotlinType` whose source carries the `@com.example.Positive` annotation, and
a paired `FirFunctionCallChecker` (registered through a `FirAdditionalCheckersExtension`)
that compares the parameter-type attribute against the argument-type attribute and
emits a custom diagnostic when they disagree. The sample compiles with exactly the
expected behaviour: `takePositive(p)` (where `p: @Positive Int`) passes, and
`takePositive(n)` (where `n: Int`) fails with the custom diagnostic.

## Observed compiler output

```
e: .../verification/09-type-attribute/sample/src/main/kotlin/Sample.kt:16:18 Expected an argument of type @Positive Int, but got a plain Int

BUILD FAILED in 967ms
```

- Line 13 `takePositive(p)` — no diagnostic (p is `@Positive Int`, attribute matches).
- Line 16 `takePositive(n)` — diagnostic at column 18 (the `n` argument, plain `Int`).
- Build status: `BUILD FAILED`, exactly one error from this plugin, no others.

## Implementation notes

- **`ConePositiveAttribute`** — single-instance `ConeAttribute<ConePositiveAttribute>`.
  - `union` / `intersect` / `add` use a `combine(other)` that returns `INSTANCE` only
    when both sides are `@Positive` and `null` otherwise — drops the attribute on
    branch mismatches (sound; e.g. `if (cond) positiveInt else plainInt` becomes plain `Int`).
  - `isSubtypeOf` returns `true` (the call-site checker, not subtyping, decides
    refinement compatibility).
  - `keepInInferredDeclarationType = true` so the attribute survives through
    declaration-type approximation; required for refinement attributes.
  - `implementsEquality = true` (singleton-based equality is fine).
  - **Mandatory accessor:** `val ConeAttributes.positive by ConeAttributes.attributeAccessor<ConePositiveAttribute>()`
    — without it the checker can't read the attribute back from a type.
- **`PositiveAttributeExtension : FirTypeAttributeExtension`**:
  - `extractAttributeFromAnnotation` — checks the annotation's `classId` against
    `com.example.Positive` and returns the singleton (analysis-time, source `@Positive Int`
    → attached attribute).
  - `convertAttributeToAnnotation` — early-returns `null` for any non-`ConePositiveAttribute`
    (per KDoc: never claim other plugins' attributes — corrupts metadata), otherwise
    builds a synthetic `FirAnnotation` with the right ClassId for the serializer.
- **`PositiveCallChecker : FirFunctionCallChecker`** — iterates `resolvedArgumentMapping`,
  for each pair: if `parameter.returnTypeRef.coneType.attributes.positive != null` and the
  argument's resolved-type attribute is missing or different, reports
  `PositiveDiagnostics.EXPECTED_POSITIVE_INT` on the argument's source.
- **`PositiveDiagnostics`** — `KtDiagnosticsContainer` declaring `EXPECTED_POSITIVE_INT`
  via `error0<KtElement>()` plus a `BaseDiagnosticRendererFactory` mapping it to
  `"Expected an argument of type @Positive Int, but got a plain Int"`.
- **Registrar** — `PositiveExtensionRegistrar` registers both the `FirTypeAttributeExtension`
  factory and the `FirAdditionalCheckersExtension` factory, plus
  `registerDiagnosticContainers(PositiveDiagnostics)` (mandatory — without it the FIR
  pipeline rejects the unknown factory at report time).
- **Plugin module** uses `-Xcontext-parameters` so the
  `context(context: CheckerContext, reporter: DiagnosticReporter) override fun check(...)`
  signature compiles against Kotlin 2.3.21.

## Issues encountered

- **First attempt also registered a `FirPropertyChecker`** (`PositivePropertyChecker`)
  modeled on the SKILL.md example. That extension flagged
  `val p: @Positive Int = 5` as a violation because the literal `5` doesn't carry
  `@Positive`, producing a spurious error in addition to the desired one on `takePositive(n)`.
  Since the verification goal explicitly says the first call (with `p`) must compile
  cleanly, the property checker was removed; only the function-call checker remains.
  In a stricter implementation one would also keep the property checker and require
  users to provide a `@Positive`-producing builder for the initializer (the
  SKILL.md example covers that scenario). The trade-off is documented in source.
- The annotation in `Sample.kt` is declared with `@Target(AnnotationTarget.TYPE)` so
  `@Positive Int` is accepted as a type-use annotation. Without the explicit target,
  Kotlin reports an unrelated "annotation cannot be applied to type" error before the
  plugin even runs.

## Reproduce

```bash
cd verification/09-type-attribute
../gradlew :sample:compileKotlin
```

Expected: `BUILD FAILED` with exactly one diagnostic at line 16 of `Sample.kt`.

## Re-run on Kotlin 2.4.20

**Status: PASS** (unchanged from the 2.3.21 result; no source changes were needed, only the version pins in `build.gradle.kts`).

```
$ ../gradlew --no-daemon -q clean :sample:compileKotlin
e: file://09-type-attribute/sample/src/main/kotlin/Sample.kt:16:18 Expected an argument of type @Positive Int, but got a plain Int
FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':sample:compileKotlin' (registered by plugin 'org.jetbrains.kotlin.jvm').
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.btapi.BuildToolsApiCompilationWork
   > Compilation error. See log for more details
(exit code 1, as required for a diagnostic-style probe)
```

Validated with Kotlin 2.4.20, Gradle 9.5.0, JDK 21 on 2026-09-10.
