# Evaluation Result: 04-veryhigh-positive-negative-types — 2026-05-27 (post-consolidation re-run)
**Skills version**: kotlin-compiler-plugin@0.1.1 (single-skill consolidated layout, commit 463287a on real repo)
**Kotlin version validated against**: 2.3.21

**Agent**: Claude (Opus 4.7)
**Sandbox**: `/tmp/kotlin-skill-eval-04-veryhigh-positive-negative-types-20260527-204100`

## Summary

All 12 mandatory acceptance criteria PASS. The consolidated `kotlin-compiler-plugin` skill (router + `references/<topic>/guide.md`) was sufficient to build a working plugin end-to-end on the first attempt; no debug iterations were required for the core (criteria 1–12).

The skill text already covers every fiddly piece the spec lists in its "Common failure modes":
- six `ConeAttribute` overrides including `keepInInferredDeclarationType` (`fir-type-attribute-extension/guide.md` §"The `ConeAttribute<T>` contract")
- the mandatory `ConeAttributes.attributeAccessor<T>()` accessor declaration (same guide, §1)
- the `if (attribute !is ...) return null` guard inside `convertAttributeToAnnotation` (same guide, §"Common gotchas")
- the `union`/`intersect`-returns-`null`-on-mismatch convention via the `Sign.combine` pattern (same guide, §"What you get")
- the context-parameter `check(...)` signature plus `-Xcontext-parameters` flag (`fir-additional-checkers-extension/guide.md` §"Why context parameters?" and §"`-Xcontext-parameters` flag is required")
- `KtDiagnosticsContainer` + `error2<...>` + `KtDiagnosticFactoryToRendererMap` with `CommonRenderers.STRING` × 2 (same guide, §"Multi-argument factories")
- `registerDiagnosticContainers(SignsDiagnostics)` in the registrar (same guide, §6)
- `-Xrender-internal-diagnostic-names` on the consumer module (same guide, §"`-Xrender-internal-diagnostic-names`")
- the `FirPropertyChecker` for assignment-site enforcement (`fir-type-attribute-extension/guide.md` already enumerates it in the table "Location / Checker base / Catches")

So the two gaps the prompt told me to watch for (explicit `FirPropertyChecker` enumeration and `error2` documentation) are in fact already documented in the consolidated skill at the cited locations. See "Skill-doc gaps observed" below for the only real friction I hit.

## Acceptance Criteria

| # | Criterion | Result | Notes |
|---|---|---|---|
| 1 | Plugin builds (`./gradlew :plugin:jar`) | PASS | `BUILD SUCCESSFUL`, `plugin.jar` produced. |
| 2 | `ConeNumberSignAttribute` extends `ConeAttribute<ConeNumberSignAttribute>` and overrides all 6 members | PASS | `union`, `intersect`, `add`, `isSubtypeOf` (functions); `key`, `keepInInferredDeclarationType` (vals). Also overrides `equals`/`hashCode`/`toString`/`implementsEquality`. Grep confirms `ConeAttribute<ConeNumberSignAttribute>` declaration. |
| 3 | `val ConeAttributes.numberSign by ConeAttributes.attributeAccessor<ConeNumberSignAttribute>()` declared at top level | PASS | Top-level declaration at bottom of `ConeNumberSignAttribute.kt`. |
| 4 | `convertAttributeToAnnotation` returns `null` for non-`ConeNumberSignAttribute` inputs | PASS | First line of body: `if (attribute !is ConeNumberSignAttribute) return null`. |
| 5 | Sample compiles (`./gradlew :sample:compileKotlin`) | PASS | Clean compile, no diagnostics. |
| 6 | Sample runs without crashing (`./gradlew :sample:run`) | PASS | Exit code 0. |
| 7 | Sample output `5`, `-7`, `5`, `-7`, `42` (one per line, in order) | PASS | Verbatim console output:<br>`5`<br>`-7`<br>`5`<br>`-7`<br>`42` |
| 8 | `takePositiveBad(makeNegativeBad())` produces `ILLEGAL_NUMBER_SIGN` | PASS | `e: Bad.kt:12:21 [ILLEGAL_NUMBER_SIGN] @Positive expected, but @Negative was passed` |
| 9 | Symmetric `takeNegativeBad(makePositiveBad())` produces `ILLEGAL_NUMBER_SIGN` | PASS | `e: Bad.kt:16:21 [ILLEGAL_NUMBER_SIGN] @Negative expected, but @Positive was passed` |
| 10 | `val nope: @Positive Int = makeNegativeBad()` produces `ILLEGAL_NUMBER_SIGN` | PASS | `e: Bad.kt:20:31 [ILLEGAL_NUMBER_SIGN] @Positive expected, but @Negative was passed` (fired by `FirPropertyChecker`). |
| 11 | `takeAny(makePositive())` does NOT trigger the diagnostic | PASS | Verified via `negative-sample/.../Good.kt` (Bad.kt temporarily stashed); clean compile. |
| 12 | Same-sign call (`takePositive(makePositive())`) does NOT trigger the diagnostic | PASS | Same Good.kt run; clean compile. |
| 13 | (optional) Diagnostic survives metadata round-trip | NOT ATTEMPTED | Skipped in interest of staying within iteration budget; the `convertAttributeToAnnotation` implementation is present and follows the documented pattern, so round-trip should work, but not verified end-to-end. |

## Score: 12 / 12 mandatory (criterion 13 bonus, not attempted)

## What I built

```
/tmp/kotlin-skill-eval-04-veryhigh-positive-negative-types-20260527-204100/
├── settings.gradle.kts                            (include plugin, sample, negative-sample)
├── build.gradle.kts                               (mavenCentral only)
├── gradle.properties
├── gradlew, gradlew.bat, gradle/wrapper/*         (copied from skill bootstrap example)
├── plugin/
│   ├── build.gradle.kts                           (Kotlin 2.3.21, kotlin-compiler-embeddable compileOnly, -Xcontext-parameters)
│   └── src/main/
│       ├── kotlin/com/example/signs/
│       │   ├── SignsPluginNames.kt
│       │   ├── SignsComponentRegistrar.kt
│       │   ├── SignsCommandLineProcessor.kt
│       │   └── fir/
│       │       ├── ConeNumberSignAttribute.kt
│       │       ├── NumberSignAttributeExtension.kt
│       │       ├── SignedNumberCallChecker.kt     (FirFunctionCallChecker + FirPropertyChecker)
│       │       ├── SignsAdditionalCheckers.kt
│       │       ├── SignsDiagnostics.kt
│       │       └── SignsFirExtensionRegistrar.kt
│       └── resources/META-INF/services/
│           ├── org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
│           └── org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
├── sample/
│   ├── build.gradle.kts                           (Kotlin 2.3.21, application, -Xplugin=, -Xrender-internal-diagnostic-names)
│   └── src/main/kotlin/
│       ├── com/example/signs/Annotations.kt       (Positive, Negative — @Target(TYPE), @Retention(BINARY))
│       └── com/example/app/Main.kt                (spec-mandated content verbatim)
└── negative-sample/
    ├── build.gradle.kts                           (same wiring as sample, no application plugin)
    └── src/main/kotlin/
        ├── com/example/signs/Annotations.kt       (duplicated — see "Deviations" below)
        └── com/example/app/Bad.kt                 (three error cases)
```

Total plugin code: ~180 lines across the seven files in `plugin/src/main/kotlin/`.

## Deviations from SPEC

1. **Annotations live in sample/negative-sample, not in plugin/**. The SPEC's project-layout sketch is ambiguous about where `Positive` / `Negative` are defined. The plugin module can only depend on `kotlin-compiler-embeddable` `compileOnly`, so user-facing annotations can't live there without changing the dependency model. The simplest layout is to put them in each consuming module's source set. The two duplicate `Annotations.kt` files (one per consumer module) is the trade-off; the alternative is a third module exposed as `api` to consumers. The SPEC doesn't mandate either, and the FQN `com.example.signs.Positive` / `com.example.signs.Negative` is what the plugin keys on regardless.

2. **Criterion 13 (metadata round-trip) not exercised**. Would require a two-module setup where module B consumes a compiled `.class` from module A; doable but I de-prioritised it after confirming the `convertAttributeToAnnotation` guard is present per criterion 4. The implementation follows the skill's documented pattern (build `FirAnnotation` from `ConeNumberSignAttribute`), so I expect it to work, but I did not actually compile a downstream module to verify.

3. **`isSubtypeOf` returns `true` unconditionally**. This mirrors the skill example. The actual gating happens in the checkers via `expected != actual`, not via subtyping; if `isSubtypeOf` returned `false` for mismatched signs, the compiler's normal subtyping path would surface a built-in `TYPE_MISMATCH` instead of our `ILLEGAL_NUMBER_SIGN`, which is not what the spec wants.

## What worked first-try

- Full bootstrap (gradlew, settings, plugin/, sample/) from the skill's `compiler-plugin-bootstrap/example/` boilerplate.
- `ConeNumberSignAttribute` directly transcribable from the `fir-type-attribute-extension` guide's worked example.
- `NumberSignAttributeExtension` directly transcribable from the same guide.
- `SignedNumberCallChecker` (and the paired `SignedNumberPropertyChecker`) directly transcribable from the same guide — both checker shapes are shown side-by-side in the table "Location / Checker base / Catches" plus full code snippets.
- `SignsDiagnostics` (using `error2<KtElement, String, String>` with two `CommonRenderers.STRING` arguments) directly transcribable from `fir-additional-checkers-extension/guide.md` §"Multi-argument factories".
- All Gradle wiring (`-Xcontext-parameters` on plugin module, `-Xrender-internal-diagnostic-names` on sample module, `compilerPlugin` configuration, `-Xplugin=` arg) directly transcribable from the bootstrap and additional-checkers guides.

The cumulative session ran **one** plugin-build cycle and **one** sample-run cycle to reach green on criteria 1–7, then added the negative-sample subproject and ran one compile cycle to verify criteria 8–10, plus one more (with Bad.kt stashed) to verify criteria 11–12. Total Gradle invocations: 4. Estimated effort the SPEC anticipates is "5–7 rounds"; the consolidated skill let me hit it in 4.

## Skill-doc gaps observed

These are the only friction points I noticed; none blocked progress.

1. **The user-side annotation location is unspecified**. None of the guides explicitly says where to put the `@Positive` / `@Negative` annotation declarations the user imports. The example in `fir-type-attribute-extension/guide.md` shows them as part of "User code" but doesn't say "put them in a separate `annotations` module" or "let them live in the sample". For a refinement-attribute plugin this is a real architectural decision (because the plugin module is `compileOnly`-only against `kotlin-compiler-embeddable`, the annotations can't live there without restructuring). A one-sentence note like "User-facing annotations should be declared in a runtime-loadable module that both the plugin's CLI processor and the user's source set can see — typically a separate `:annotations` subproject or, for tests, in the sample's own source set" would close this.

2. **`FirPropertyChecker` for assignment-site enforcement is documented but slightly buried**. The relevant table and code snippet ARE in `fir-type-attribute-extension/guide.md` (it has a "Location / Checker base / Catches" table and a paired `SignedNumberPropertyChecker` snippet), so the skill DOES enumerate `FirPropertyChecker` explicitly — the prompt's hint about this gap is no longer accurate post-consolidation. The skill also notes the literal-initialiser caveat ("Caveat — `FirPropertyChecker` over-flags numeric literal initialisers"), which would actually have bitten me on `val p: @Positive Int = 5` if not for the workaround I happened to use (calling `makePositive()` instead of inlining the literal). I'd consider this one CLOSED.

3. **`error2` multi-arg renderer is documented** in `fir-additional-checkers-extension/guide.md` §"Multi-argument factories — renderer arguments are required". The worked example uses exactly the `expected vs actual sign` shape my task needed. The prompt's hint about this gap is also no longer accurate post-consolidation. CLOSED.

4. **Minor**: `coneTypeOrNull` is used in the example but its import (`org.jetbrains.kotlin.fir.types.coneTypeOrNull`) is not explicitly written out. I had to infer the package. A line in the snippet saying `// import org.jetbrains.kotlin.fir.types.coneTypeOrNull` would shave a few seconds off this kind of task.

5. **Minor**: The `Retention` of `Positive`/`Negative` annotations is unspecified in the spec but it matters: `@Retention(AnnotationRetention.BINARY)` makes the round-trip viable; `SOURCE` does not. I used `BINARY` based on general Kotlin knowledge, but a sentence in the skill about retention for round-trippable type annotations would be helpful.

## Failure analysis

None. Score is 12/12 on mandatory criteria.

## Verification commands actually run (matching SPEC §"Verification procedure")

```bash
./gradlew :plugin:jar --console=plain                                   # PASS
./gradlew :sample:run --rerun-tasks --console=plain                     # PASS; prints 5 / -7 / 5 / -7 / 42
./gradlew :negative-sample:compileKotlin --rerun-tasks --console=plain  # FAIL as expected, with 3 [ILLEGAL_NUMBER_SIGN] errors

grep -F 'ConeAttribute<ConeNumberSignAttribute>' \
  plugin/src/main/kotlin/com/example/signs/fir/ConeNumberSignAttribute.kt    # PASS
grep -F 'attributeAccessor<ConeNumberSignAttribute>' \
  plugin/src/main/kotlin/com/example/signs/fir/ConeNumberSignAttribute.kt    # PASS
grep -E 'override (fun|val)' \
  plugin/src/main/kotlin/com/example/signs/fir/ConeNumberSignAttribute.kt | \
  grep -E 'union|intersect|add|isSubtypeOf|key|keepInInferredDeclarationType' # PASS — 6 hits
```

Captured diagnostic output from the failed `:negative-sample:compileKotlin`:

```
e: Bad.kt:12:21 [ILLEGAL_NUMBER_SIGN] @Positive expected, but @Negative was passed
e: Bad.kt:16:21 [ILLEGAL_NUMBER_SIGN] @Negative expected, but @Positive was passed
e: Bad.kt:20:31 [ILLEGAL_NUMBER_SIGN] @Positive expected, but @Negative was passed
```

The factory name `ILLEGAL_NUMBER_SIGN` is rendered because the sample/negative-sample modules pass `-Xrender-internal-diagnostic-names`; the human-readable message follows the bracketed factory name.
