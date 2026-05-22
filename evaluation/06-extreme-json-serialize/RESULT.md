# Evaluation Result: 06-extreme-json-serialize — 2026-04-30

**Skills version**: HEAD of `main` at evaluation time
**Kotlin version validated against**: 2.3.20

## Final Score: 96 / 100

| Category | Score | Max |
|---|---|---|
| Functionality | 60 | 60 |
| Code Quality | 18 | 20 |
| Skill Adherence | 18 | 20 |

## Functionality breakdown

All 16 mandatory criteria PASS. Both optional criteria PASS.

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Plugin builds | PASS | `:plugin:jar` BUILD SUCCESSFUL |
| 2 | `module-A` compiles | PASS | `:module-A:compileKotlin` BUILD SUCCESSFUL |
| 3 | `User.class` declares `toJson()` | PASS | `javap` shows `public final java.lang.String toJson();` |
| 4 | `User$Companion.class` declares `parse(String)` | PASS | `javap` shows `public final com.example.model.User parse(java.lang.String);` |
| 5 | `module-B` compiles | PASS | `:module-B:compileKotlin` BUILD SUCCESSFUL — confirms metadata visibility round-trip |
| 6 | `module-B:run` produces correct JSON | PASS | stdout contains `{"user_name":"Alice","age":30,"active":true}` (passwordHash omitted, name renamed) |
| 7 | `User.parse` round-trip | PASS | stdout contains `parsed ok: Alice` |
| 8 | Order serialization (nested + list) | PASS | stdout contains `"items":[{...},{...}]`, `"customer":{...}`, `"paid_amount":99.95` |
| 8b | `Order.parse` round-trip | PASS | stdout contains `order ok: SKU-1` |
| 9 | `JSON_REQUIRED_WITH_DEFAULT` fires | PASS | temp file w/ `@JsonRequired val x: String = "anon"` produced `[JSON_REQUIRED_WITH_DEFAULT]` error |
| 10 | `JSON_REQUIRED_ON_NULLABLE` fires | PASS | temp file w/ `@JsonRequired val x: String?` produced `[JSON_REQUIRED_ON_NULLABLE]` error |
| 11 | No false positives on valid usage | PASS | `@JsonRequired val x: String` BUILD SUCCESSFUL with no diagnostics |
| 12 | `JSON_IGNORE_AND_REQUIRED_CONFLICT` fires | PASS | temp file w/ both annotations produced `[JSON_IGNORE_AND_REQUIRED_CONFLICT]` error |
| 13 | `JSON_RENAME_EMPTY` fires | PASS | temp file w/ `@JsonRename("")` produced `[JSON_RENAME_EMPTY]` error |
| 14 | `module-A` source can't call `parse()` in same compilation | PASS | `_probe.kt` calling `User.parse("")` produced `[UNRESOLVED_REFERENCE] Unresolved reference 'parse'` under `--rerun-tasks` |
| 15 | Companion synthesis when missing; user companion preserved | PASS | `User`, `Order`, `LineItem` (no source companion) all produced `$Companion.class`. Probe class `HasOwnCompanion` with explicit user companion containing `MARKER` retained the constant (hoisted to outer per JVM `const val` lowering) AND received `parse()` |
| 16 | Diagnostic factory names rendered with `-Xrender-internal-diagnostic-names` | PASS | every diagnostic in build output bracketed with its factory name (e.g. `[JSON_REQUIRED_WITH_DEFAULT]`) |
| 17 *(optional)* | `parse()` returns null on malformed JSON | PASS | `fillParseBody` wraps the body in `irTry` with a catch-all returning `null` (`JsonIrGenerationExtension.kt:415-447`); `parsed != null` `require`-check in Main passes runtime |
| 18 *(optional)* | Cross-module nesting compiles | PASS | `module-B/NestedModel.kt` defines `Wrapper(val owner: User)` — `Wrapper.class` shows `public final java.lang.String toJson()` and `Wrapper$Companion` shows `parse(String)` (round-trip not exercised at runtime, but compile + bytecode shape verified) |

Functionality score: 16/16 mandatory + 2/2 bonus → 60/60.

## Code Quality breakdown

| Sub-axis | Score | Notes |
|---|---|---|
| File organization | 5 | Clear plugin/sample split. `fir/` and `ir/` subpackages matching SPEC layout. `JsonRuntime.kt` as a runtime helper colocated with annotations in module-A is acceptable and pragmatic — keeps the IR side simple by delegating low-level JSON tokenizing to ordinary Kotlin. `META-INF/services` set up correctly. |
| Idiomatic Kotlin | 5 | `object` for `JsonChecker`, `JsonDiagnostics`, `JsonGeneratedDeclarationKey`, `JsonPluginNames`. Companion-object `PREDICATE`. Sensible `data class PropertyInfo`. `when` expressions instead of long chains. No Java-style getters. |
| Readability | 5 | Comment headers ("PASS 1: declare parse()", "PASS 2a: fill toJson()") segment the IR generator logically. Names like `info.jsonKey`, `firstVar`, `rawListVar` are descriptive. No commented-out blocks, no `tmp`/`xx`. The runtime-helper functions are explicitly prefixed `__json…` which signals "compiler-internal". |
| No anti-patterns | 3 | One real concern: 3 deprecation warnings in build output — `IrType.isNullable()` is deprecated, replacement is `kotlin.ir.util.isNullable` (`JsonIrGenerationExtension.kt:266, 476, 707`). Three identical warnings flagged at build time and not addressed. Minus 2. No `Thread.sleep`, no empty catches (the `irTry` catch returning `null` in `fillParseBody` is intentional and matches SPEC criterion 17), no `@Suppress("ALL")`. The catch-all of `Throwable` for `parse()` is a deliberate spec-mandated fallback, not an anti-pattern. |

Code Quality: 5+5+5+3 = 18.

## Skill Adherence breakdown

| Sub-axis | Score | Notes |
|---|---|---|
| Recommended patterns | 5 | `JsonPluginNames.PLUGIN_ID` exists and is shared (`JsonPluginNames.kt:8`). Plugin module uses `compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.3.20")` (`plugin/build.gradle.kts:12`). `@OptIn(ExperimentalCompilerApi::class)` at `JsonComponentRegistrar.kt:11` and `JsonCommandLineProcessor.kt:7`. `supportsK2 = true` set (`JsonComponentRegistrar.kt:14`). `-Xcontext-parameters` in `plugin/build.gradle.kts:16`. `registerDiagnosticContainers(JsonDiagnostics)` in `JsonFirExtensionRegistrar.kt:9`. |
| Modern APIs | 4 | `pluginContext.finderForBuiltins()` used (`JsonIrGenerationExtension.kt:111`); no `referenceFunctions`/`referenceClass`. `metadataDeclarationRegistrar.registerFunctionAsMetadataVisible` used correctly (line 211). However the SPEC's hint #6 explicitly recommends `pluginContext.finderForSource(file).findFunctions(...)` for the nested-toJson lookup; the implementer used `nestedClass.functions.first { ... }` (line 324) instead — functionally equivalent (since they have the IrClass already) but doesn't follow the recommended idiom. Minus 1. |
| No invented/deprecated APIs | 4 | No `getPluginArtifactForNative()`, no `createParameterDeclarations()` (uses `thisReceiver.copyTo(this)` and `addValueParameter`), no `registerClassAsMetadataVisible`, no `dispatchReceiver = ...` setter (uses `arguments[0] = ...`), `KtDiagnosticFactoryToRendererMap` used via `by` delegate (`JsonDiagnostics.kt:20`). However: `IrType.isNullable()` is deprecated in favor of `kotlin.ir.util.isNullable` and is used in 3 places (lines 266, 476, 707). Minus 1. |
| Correct API forms | 5 | Checker uses context parameters per the modern signature: `context(context: CheckerContext, reporter: DiagnosticReporter) override fun check(declaration: FirRegularClass)` (`JsonChecker.kt:25-26`). Predicate registration is on `FirDeclarationGenerationExtension.registerPredicates()` not on the registrar (`JsonDeclarationGenerator.kt:39`). `-Xcontext-parameters` is in plugin module `freeCompilerArgs`. `MppCheckerKind.Common` correctly passed. `KtFakeSourceElementKind` source-kind guard included to avoid double-firing on synthesised companion. |

Skill Adherence: 5+4+4+5 = 18.

## Anti-cheat findings

Reviewed every failure mode in SPEC; none triggered:

1. `toJson` body filled at IR — confirmed from runtime output (criterion 6 emits the actual JSON, not `""` or `NotImplementedError`).
2. `parse` IR-only — confirmed by criterion 14 negative test (`Unresolved reference 'parse'` from module-A source).
3. `registerFunctionAsMetadataVisible` called — `JsonIrGenerationExtension.kt:211` and module-B compiles + runs.
4. `@JsonRename` honoured — output shows `"user_name"` not `"name"`, `"paid_amount"` not `"paid"`.
5. `@JsonIgnore` honoured — `passwordHash` absent from output JSON.
6. Recursion through `toJson()` not `toString()` — output shows `"customer":{"user_name":"Alice"…}` shape.
7. `JSON_REQUIRED_WITH_DEFAULT` checks the parameter's `defaultValue` (`JsonChecker.kt:42 — param.defaultValue != null`), not a naive grep.
8. `JsonDiagnostics` registered via `registerDiagnosticContainers(JsonDiagnostics)` — `JsonFirExtensionRegistrar.kt:9`.
9. `parse` exposed via `metadataDeclarationRegistrar` — module-B resolves it.
10. No reflection at runtime — IR generates direct calls to nested `toJson` and `parse` functions.
11. `JSON_RENAME_EMPTY` enforced (criterion 13 PASS).
12. Companion synthesis works — `User`/`Order`/`LineItem` with no user companion still get `$Companion.class` containing `parse`.

## Overall assessment

A solid, end-to-end correct implementation. All 16 mandatory criteria + both bonus criteria pass. The IR generator separates concerns cleanly (declare → fill toJson → fill parse), and the runtime helper module (`JsonRuntime.kt`) is a pragmatic offload that avoids re-implementing string/array tokenization in IR. Minor deductions for the deprecated `IrType.isNullable()` API and not following the SPEC's `finderForSource` recommendation. Focus next: replace `IrType.isNullable()` calls with `kotlin.ir.util.isNullable` to clear the deprecation warnings.

## Suggested skill fixes

The implementer flagged five skill gaps. My independent verdicts:

1. **`metadataDeclarationRegistrar` end-to-end pattern not in `ir-synthetic-class-generation`** — PARTIALLY VALID. The API is documented (`ir-synthetic-class-generation/SKILL.md:142`, `ir-plugincontext-usage/SKILL.md:124`), but the *specific* recipe of "FIR synthesises the companion shell + IR adds a single function on it + registers JUST that function as metadata-visible" is not shown end-to-end. The shown example (`newClass.functions.forEach { ... }`) operates on a class the plugin built whole; the cross-stage FIR-companion / IR-only-function composition is not explicitly demonstrated. **Suggest**: add an end-to-end recipe at `ir-synthetic-class-generation/SKILL.md` that contrasts "IR-only function on FIR-generated companion" vs "whole IR class".

2. **`IrType.isString()` doesn't match nullable `String?`** — VALID. `ir-plugincontext-usage/SKILL.md:175` uses `isString()` without warning about nullable behaviour. The implementer worked around it with "unwrap nullable then check" (which is fine) and a redundant `|| type.classOrNull == irBuiltIns.stringClass` fallback. **Suggest**: add a callout in `ir-body-modification/SKILL.md` or `ir-plugincontext-usage/SKILL.md` documenting that `isString()`/`isInt()`/etc. exclude the nullable variant, with the recommended `type.makeNotNull().isString()` or "unwrap then check" pattern.

3. **Shaded `org.jetbrains.kotlin.com.intellij.psi.PsiElement` not documented** — VALID. No skill specifies the import path. `fir-additional-checkers-extension/SKILL.md:345` mentions `PsiElement` by bare name only. The implementer reportedly hit context-parameter cascade errors when using the unshaded import. **Suggest**: add the explicit import line `import org.jetbrains.kotlin.com.intellij.psi.PsiElement` in `fir-additional-checkers-extension/SKILL.md` and note that the unshaded `com.intellij.psi.PsiElement` is wrong from inside `kotlin-compiler-embeddable`.

4. **`DirectDeclarationsAccess` doesn't help detect existing companion** — VALID. The implementer used `@OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)` and walked `fir.declarations` directly (`JsonDeclarationGenerator.kt:51-58`). The skill `fir-declaration-generation-extension/SKILL.md:250` notes that `getNestedClassifiersNames` is the entry hook but does not show how to *check* for an existing user-written companion to skip generating one. **Suggest**: add a recipe demonstrating "skip companion generation if user already wrote one" in `fir-declaration-generation-extension/SKILL.md`. The current API surface forces an opt-in into experimental territory which feels unfortunate.

5. **Context-parameter error cascade when PsiElement import is wrong** — UNVERIFIABLE FROM ARTIFACT. This is a debugging-experience claim. The plugin compiles cleanly now; I cannot reproduce the historical cascade. Plausibly a downstream effect of #3. Treat as evidence reinforcing #3 rather than a separate gap.

Also worth noting (not flagged by implementer): `IrType.isNullable()` is **deprecated** in 2.3.20 in favour of `kotlin.ir.util.isNullable`. The skill docs do not warn about this — `ir-body-modification/SKILL.md` and `ir-plugincontext-usage/SKILL.md` could add a cross-reference.
