# Evaluation Result: 04-veryhigh-positive-negative-types — 2026-04-30

**Skills version**: HEAD of `main` at evaluation time
**Kotlin version validated against**: 2.3.20

## Final Score: 100 / 100

| Category | Score | Max |
|---|---|---|
| Functionality | 60 | 60 |
| Code Quality | 20 | 20 |
| Skill Adherence | 20 | 20 |

(All 12 mandatory functionality criteria pass; criterion 13 is optional and not attempted.)

## Functionality breakdown

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Plugin builds (`./gradlew :plugin:jar`) | PASS | `BUILD SUCCESSFUL in 4s` for `:plugin:jar`. |
| 2 | `ConeNumberSignAttribute` extends `ConeAttribute<ConeNumberSignAttribute>` and overrides `union`, `intersect`, `add`, `isSubtypeOf`, `key`, `keepInInferredDeclarationType` | PASS | `plugin/src/main/kotlin/com/example/signs/fir/ConeNumberSignAttribute.kt:7` declares the class; lines 22, 23, 24, 25, 29, 30 supply all six required overrides. |
| 3 | Top-level `val ConeAttributes.numberSign: ConeNumberSignAttribute? by ConeAttributes.attributeAccessor<ConeNumberSignAttribute>()` | PASS | `ConeNumberSignAttribute.kt:54`. |
| 4 | `convertAttributeToAnnotation` returns `null` for non-`ConeNumberSignAttribute` | PASS | `NumberSignAttributeExtension.kt:32`: `if (attribute !is ConeNumberSignAttribute) return null`. |
| 5 | Sample compiles (`./gradlew :sample:compileKotlin`) | PASS | Verified via `:sample:run` chain (compileKotlin task SUCCESSFUL, see `/tmp/04-out.txt`). |
| 6 | Sample runs without crashing (`./gradlew :sample:run`) | PASS | `BUILD SUCCESSFUL in 1s`, exit code 0. |
| 7 | Sample output is correct (`5`, `-7`, `5`, `-7`, `42`) | PASS | `/tmp/04-out.txt` lines 14-18 contain `5`, `-7`, `5`, `-7`, `42` in order. |
| 8 | `takePositive(makeNegative())` produces `ILLEGAL_NUMBER_SIGN` | PASS | Temp file (`NegTest.kt:12`) produced: `e: NegTest.kt:12:19 [ILLEGAL_NUMBER_SIGN] @Positive expected, but @Negative was passed`. |
| 9 | `takeNegative(makePositive())` produces `ILLEGAL_NUMBER_SIGN` | PASS | `e: NegTest.kt:16:19 [ILLEGAL_NUMBER_SIGN] @Negative expected, but @Positive was passed`. |
| 10 | `val x: @Positive Int = makeNegative()` produces `ILLEGAL_NUMBER_SIGN` | PASS | `e: NegTest.kt:20:28 [ILLEGAL_NUMBER_SIGN] @Positive expected, but @Negative was passed`. Implemented via the dedicated `SignedNumberPropertyChecker` (`SignedNumberCallChecker.kt:35-51`). |
| 11 | `takeAny(makePositive())` does NOT trigger | PASS | Temp `PosTest.kt` with `takeAnyP(mkPosP())`/`takeAnyP(mkNegP())` compiled cleanly. |
| 12 | Same-sign call does NOT trigger | PASS | Temp `PosTest.kt` with `takePosP(mkPosP())`/`takeNegP(mkNegP())` compiled cleanly. |
| 13 *(optional)* | Diagnostic survives metadata round-trip | NOT ATTEMPTED | Optional, no deduction. |

## Code Quality breakdown

| Sub-axis | Score | Notes |
|---|---|---|
| File organization | 5 | Plugin sources split between `com.example.signs` (registrars, plugin id) and `com.example.signs.fir` (FIR-side: attribute, extension, checker, diagnostics, registrar). Annotation classes live with the sample (single-module setup); naming and packaging match the SPEC layout. |
| Idiomatic Kotlin | 5 | `object` for singletons (`SignsDiagnostics`, `SignedNumberCallChecker`, `SignedNumberPropertyChecker`, `SignsPluginNames`); `enum class Sign` with abstract `combine` overrides per case; private constructor + `fromSign` companion factory keeps the two `Positive`/`Negative` instances canonical so `==` identity is consistent. `implementsEquality = true` plus `equals`/`hashCode` is a nice (and correct) hardening over the canonical reference. |
| Readability | 5 | Variables `expected`, `actual`, `numberSign`, `Sign.Positive`/`Sign.Negative` are self-explanatory. Comment at `NumberSignAttributeExtension.kt:31` explains the type-guard rationale ("Other plugins'/compiler's attributes flow through here too"). No commented-out code, no `tmp`/`xx`/`data1`. |
| No anti-patterns | 5 | No `Thread.sleep`, no empty catches, no `@Suppress("ALL")`, no copy-pasted long blocks (the two checkers share shape but each is short and the duplication makes their distinct purposes obvious). |

## Skill Adherence breakdown

| Sub-axis | Score | Notes |
|---|---|---|
| Recommended patterns | 5 | `SignsPluginNames.PLUGIN_ID` shared between `SignsCommandLineProcessor` (line 9) and `SignsComponentRegistrar` (line 11). `compileOnly("...kotlin-compiler-embeddable:2.3.20")` in `plugin/build.gradle.kts:12`. `@OptIn(ExperimentalCompilerApi::class)` on both `SignsComponentRegistrar:9` and `SignsCommandLineProcessor:7`. `supportsK2 = true` (`SignsComponentRegistrar.kt:12`). |
| Modern APIs | 5 | Pure FIR layer (no IR work to chase); checker uses `reporter.reportOn(src, factory, args...)` and reads `arg.resolvedType.attributes.numberSign` via the modern accessor. `KtDiagnosticFactoryToRendererMap` is consumed via `by` delegate (`SignsDiagnostics.kt:18`), not a direct constructor call. |
| No invented/deprecated APIs | 5 | No `getPluginArtifactForNative`, no `createParameterDeclarations`, no `registerClassAsMetadataVisible`, no `dispatchReceiver = ...` setter. `buildAnnotation { ... }` + `FirEmptyAnnotationArgumentMapping` is the documented construction shape; `ConeClassLikeTypeImpl(lookupTag, EMPTY_ARRAY, isMarkedNullable = false)` is the public constructor form. |
| Correct API forms | 5 | Both checkers declare `context(context: CheckerContext, reporter: DiagnosticReporter) override fun check(...)` (`SignedNumberCallChecker.kt:16-17`, `:36-37`). `-Xcontext-parameters` is in plugin's `freeCompilerArgs` (`plugin/build.gradle.kts:16`); `-Xrender-internal-diagnostic-names` is in sample's `freeCompilerArgs` (`sample/build.gradle.kts:29`). `registerDiagnosticContainers(SignsDiagnostics)` in the FIR registrar (`SignsFirExtensionRegistrar.kt:9`). Service files for `CompilerPluginRegistrar` and `CommandLineProcessor` are present. |

## Anti-cheat findings

None of the SPEC's "Common failure modes" triggered:

1. All six required `ConeAttribute` overrides present, including `keepInInferredDeclarationType = true` as a `val`.
2. `attributeAccessor` declaration present at top level (`ConeNumberSignAttribute.kt:54`); the checker uses it (`SignedNumberCallChecker.kt:20-21`).
3. `union`/`intersect`/`add` return `null` when signs differ — `Sign.Positive.combine(Negative)` returns `null` per the `if (other == Positive) Positive else null` clause; `combine(null)` returns null too. No always-true / always-positive bug.
4. `convertAttributeToAnnotation` guards with `if (attribute !is ConeNumberSignAttribute) return null` (`NumberSignAttributeExtension.kt:32`).
5. `keepInInferredDeclarationType = true` (`ConeNumberSignAttribute.kt:30`).
6. Both checkers use the context-parameter `check(...)` form; no value-parameter form leaving `check` abstract.
7. `-Xcontext-parameters` flag set on the plugin module.
8. `-Xrender-internal-diagnostic-names` flag set on the consumer module — `[ILLEGAL_NUMBER_SIGN]` factory name appears in the build output, which is the only reason the negative tests pass at the CI-grep level.
9. `registerDiagnosticContainers(SignsDiagnostics)` called in the registrar — no `IllegalStateException: Diagnostic factory was not registered` at consumer build time.
10. Only a single `FirTypeAttributeExtension` registered; no cross-plugin metadata corruption risk.

Note on `isSubtypeOf` returning `true`: the user-instructed special focus reads "overrides return null on mismatch (not always-true)". `isSubtypeOf` returns `Boolean`, not nullable, so "null" cannot apply there; the relevant overrides are `union`/`intersect`/`add`, which all correctly drop the attribute on mismatch. The `isSubtypeOf = true` choice matches the canonical reference pattern (the attribute itself does not constrain subtyping; the checker handles compatibility separately at call sites and assignments). Not flagged.

`@Target(AnnotationTarget.TYPE)` is correctly applied to both `Positive` and `Negative` (`sample/.../Annotations.kt:3,6`); without this, `@Positive Int` on a parameter would not even parse as a type-use annotation.

## Overall assessment

A clean, faithful implementation of the FIR type-attribute extension pattern. All 12 mandatory criteria pass; the implementer correctly identified that criterion 10 (assignment violation) requires a separate `FirPropertyChecker`, not just a `FirFunctionCallChecker`. Code quality and skill adherence are both at the maximum. No anti-cheat triggers. Score: 100 / 100.

## Suggested skill fixes

The implementer's RESULT flagged three potential skill-doc gaps. My independent verdict:

1. **`FirPropertyChecker` companion to the function-call checker is needed for assignment enforcement (criterion 10)** — VALID. `skills/fir-type-attribute-extension/SKILL.md` exemplifies only `FirFunctionCallChecker`. A `val x: @Positive Int = makeNegative()` is a `FirProperty` whose initializer is a call resolved against `makeNegative`'s parameters (none), so `FirFunctionCallChecker` cannot reach across the assignment context. Without a `FirPropertyChecker` (or `FirVariableAssignmentChecker`/return checker), criterion 10 silently never fires. The skill should explicitly enumerate which checker(s) to pair when the attribute should constrain non-call assignment positions.

2. **`error2` factory + multi-arg renderer was undocumented** — VALID. `skills/fir-additional-checkers-extension/SKILL.md` lists `error2` in the arity table but the only worked renderer example uses `error0` with a literal-string `map.put`. The 2-arg `map.put(factory, "{0} ... {1}", CommonRenderers.STRING, CommonRenderers.STRING)` form needed to interpolate `reporter.reportOn(src, factory, expectedName, actualName)` is not exemplified. A reader copy-pasting the existing example will hit a runtime renderer-arity mismatch.

3. (Implementer also flagged a `PsiElement` import path nit. Marginal; not load-bearing for this evaluation.)
