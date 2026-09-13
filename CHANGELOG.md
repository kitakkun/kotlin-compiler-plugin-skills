# Changelog

Plugin release history. For per-API Kotlin version migrations affecting plugin authors, see each skill's `CHANGES.md`.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.3.2] - 2026-09-10

### Changed

- **Retargeted the whole skill from Kotlin 2.4.10 to 2.4.20.** All EVIDENCE.md / guide.md permalinks re-pinned to `v2.4.20`; 61 of the 167 cited source paths changed across the 3,939 upstream commits, producing 64 line-drift citations and 2 moved/deleted paths, all re-anchored and re-verified (358 citations, 0 dangling). Every changed cited file was read for plugin-facing API changes; the results below each gained a `## Kotlin 2.4.10 → 2.4.20` section in the topic's `CHANGES.md`.
- **`compiler-plugin-bootstrap` / `compiler-plugin-debugging`**: `CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY` and `CompilerConfiguration.messageCollector` now require `@OptIn(MessageCollectorAccess::class)` (KT-78277; empirically confirmed to be a compile error without it). The legacy K1 `ComponentRegistrar` was deleted (KT-85816). IR validation now reports through `IrDiagnosticReporter` (`IR_VALIDATION_ERROR` / `IR_VALIDATION_WARNING`); `-Xverify-ir-visibility` / `-Xverify-ir-nested-offsets` were replaced by `-Xdisable-ir-checkers` / `-Xenable-additional-ir-checkers`; `JvmK1IrValidationBeforeLoweringPhase` is gone; `MessageCollectorWithDiagnosticId` added.
- **`compiler-plugin-testing`**: box-test base class is now `AbstractJvmBlackBoxCodegenTestBase(parser: FirParser)` (`AbstractFirBlackBoxCodegenTestBase` deleted, KT-85292); `PhasedPipelineChecker` and friends are wired as `TestFailureSuppressor`s. `RUN_PIPELINE_TILL` stays mandatory.
- **`fir-additional-checkers-extension`**: `FirSimpleFunctionChecker` → `FirNamedFunctionChecker`, `simpleFunctionCheckers` → `namedFunctionCheckers` (no alias left); new `infoWithoutSource()`; `SourceElementPositioningStrategies.VALUE_ARGUMENTS` removed. Also fixed two strategy names that never existed (`SECONDARY_CONSTRUCTOR_KEYWORD`, `TYPE_OF_DECLARATION`).
- **`fir-declaration-generation-extension`**: new `generateFields` callback (`@UnsafePluginApi`, Java-interop only); `GeneratedDeclarationKey.toString()` now defaults to the simple class name; `createConstructor(generateDelegatedNoArgConstructorCall = true)` is best-effort instead of throwing when the superclass has no zero-arg constructor.
- **`ir-synthetic-class-generation`**: `IrGeneratedDeclarationsRegistrar` gained `registerClassAsMetadataVisible` and `registerPropertyAsMetadataVisible` (KT-79565 / KT-63881); the guide now recommends one class-level registration, keeping the per-member loop as the ≤2.4.10 fallback.
- **`ir-call-rewriting`**: `IrUtils.kt` annotation helpers `getAnnotationStringValue`, `getAnnotationValueOrNull`, `IrConstructorCall.getValueArgument(Name)` removed without deprecation; use `getAnnotationArgumentValue` / `IrAnnotation.getConstArgument` / `argumentMapping`. `IrAnnotation.symbol` deprecated in favor of `classSymbol`. Also corrected a pre-existing claim that `hasAnnotationOrOverridden` lives in `org.jetbrains.kotlin.ir.util` (it is power-assert's own helper).
- **`fir-function-call-refinement-extension`**: `KtFakeSourceElementKind.PluginGenerated` became a sealed class (`Default` / `Custom(marker)`, KT-84344); generated local declarations must carry distinct source elements, which the diagnostic test infrastructure now enforces.
- **`fir-status-transformer-extension`**, **`fir-session-components`**, remaining FIR and IR topics: line drift only; `FirExtensionRegistrar.AVAILABLE_EXTENSIONS` still has 17 entries in the same order.
- Code-sample version pins bumped to 2.4.20; bootstrap example pinned to 2.4.20 and rebuilt clean.

### Added

- `scripts/check_citation_drift.sh <clone> <old-tag> <new-tag>` — compares every cited line range at both tags and prints `DRIFT` (with candidate new line numbers) or `MISSING`; `verify_citations.sh` alone cannot see a file that changed above the cited line.

### Fixed

- `scripts/bump_kotlin_version.sh` now also re-pins `guide.md` permalinks and no longer rewrites historical rows in `README.md` / `CHANGELOG.md`. `CONTRIBUTING.md`'s version-bump procedure updated accordingly, including the zsh word-splitting pitfall that can make the cited-path diff look empty.
- JDK 25 / BTAPI `JavaVersion.parse` note: `versions.intellijSdk` is unchanged at 2.4.20, so the workaround is kept; a naive reproduction (toolchain 21, daemon strategy) does not hit the failure on either 2.4.10 or 2.4.20, which is now recorded in EVIDENCE.

### Validated against

- Kotlin 2.4.20, Gradle 9.5.0, JDK 21 (plus JDK 25 launcher for the BTAPI probe).
- `evaluation/` benchmarks were not re-run for this release; the affected guides were re-validated by source reading and by compiling the `MessageCollectorAccess` case. A future run against 2.4.20 is recommended before 0.4.0.

## [0.3.1] - 2026-09-10

### Changed

- **Re-pinned the whole skill from Kotlin 2.4.0 to 2.4.10.** All EVIDENCE.md / guide.md permalinks now point at `v2.4.10`; every cited line number re-verified against the tag (299 citations, 0 dangling, 0 line-drift). None of the 167 cited source paths changed between `v2.4.0` and `v2.4.10` (25 upstream commits; the touched areas are Wasm IC, KGP, K/N simulators, Compose stability, scripting internals, and a `FirExpressionEvaluator` constant-folding fix — none affect the plugin-facing API), so no `CHANGES.md` migration sections were needed.
- Code-sample version pins (`kotlin("jvm")`, `kotlin-compiler-embeddable`, `kotlin-compiler`, test-framework artifacts, the `kotlin.compiler` property default, and the CI matrix) bumped to 2.4.10 in `compiler-plugin-bootstrap`, `compiler-plugin-testing`, and `multi-version-kotlin-support`.
- Bootstrap example (`compiler-plugin-bootstrap/example`) pinned to 2.4.10 and rebuilt clean (`:sample:run` prints the injected line).

### Fixed

- Recorded that the JDK 25 / BTAPI `JavaVersion.parse` workaround is **still required** on 2.4.10 (`versions.intellijSdk=251.27812.49` unchanged since 2.3.21).

### Validated against

- Kotlin 2.4.10, Gradle 9.5.0, JDK 21.
- No reference guide was affected by the upstream diff, so the six `evaluation/` benchmarks were not re-run for this patch; the 0.3.0 results stand.

## [0.3.0] - 2026-06-13

### Changed

- **Retargeted the whole skill from Kotlin 2.3.21 to 2.4.0.** All EVIDENCE.md permalinks re-pinned to `v2.4.0` and every cited line number re-verified against the tag (299 citations, 0 dangling). Source-tree moves followed: `compiler/cli/cli-common/` → `compiler/cli/cli-base/`, `core/compiler.common/.../name/SpecialNames.kt` → `core/names/.../SpecialNames.kt`.
- **IR unified-arguments migration completed (KT-68003/KT-70054).** Documented the 2.4.0 **removal** of `IrMemberAccessExpression.extensionReceiver` / `valueArgumentsCount` / `getValueArgument` / `putValueArgument` and `IrFunction.valueParameters` / `extensionReceiverParameter`; `dispatchReceiver` survives and `IrFunction.dispatchReceiverParameter` is now read-only. Updated `ir-call-rewriting`, `ir-body-modification`, `fir-function-type-kind-extension`.
- **`FirReplSnippetResolveExtension` became a `FirExtensionSessionComponent`** (left `AVAILABLE_EXTENSIONS`, which dropped 18 → 17 entries; access is now `FirSession.replSnippetResolveExtension`). Updated `fir-repl-snippet-extensions` and `fir-extensions-overview`.
- **`IrGeneratedDeclarationsRegistrar` annotation APIs now use `IrAnnotation`** (was `IrConstructorCall`). Updated `ir-synthetic-class-generation`.
- Added `## Kotlin 2.3 → 2.4` sections to the affected reference `CHANGES.md` files; bumped each guide's "targets" line to 2.4.0.

### Added

- `scripts/verify_citations.sh` — verifies every EVIDENCE/SKILL permalink resolves at its pinned tag and surfaces the cited source line, for spot-checking line-number drift on future version bumps.

### Fixed

- Recorded that the JDK 25 / BTAPI `JavaVersion.parse` workaround is **still required** on 2.4.0 (bundled `intellijSdk` unchanged), correcting the earlier "likely resolved in 2.4.x" note.

### Improved (from building a plugin against 2.4.0)

- **Shaded ⇔ un-shaded + `reified PsiElement`**: documented that a plugin whose diagnostic factories use `reified PsiElement` cannot serve Pattern A (embeddable) and Pattern B (un-shaded) from one compiled artifact, and wired the `shadowJar`-relocate solution across `compiler-plugin-testing`, `compiler-plugin-bootstrap`, `fir-additional-checkers-extension`, and `gradle-plugin-integration`.
- **Diagnostic tests need `// RUN_PIPELINE_TILL: FRONTEND` on 2.4** (else `PhasedPipelineChecker` aborts); added it to the diagnostic runner's `defaultDirectives` with the rationale (also dodges the R8/dexing `NoClassDefFoundError`).
- **`fullyExpandedType`**: `CheckerContext` is a `SessionHolder` on 2.4, so passing `session` explicitly is now an error inside a checker; documented the no-arg form and a cross-version helper.
- **`multi-version-kotlin-support`**: added "Strategy 0 — single source on the common API" as the correct first choice for adjacent minors, before reflection.
- **`fir-additional-checkers-extension`**: added `FirTryExpressionChecker` to the checker catalog; noted `-Xcontext-parameters` now warns as redundant on 2.4.

### Validated against

- Kotlin 2.4.0, Gradle 9.5.0, JDK 21.
- All six accuracy benchmarks (`evaluation/`) re-run by fresh sub-agents against the 2.4.0 skill and **independently re-verified — 100% of mandatory criteria across all six** (01 small checker 9/9, 02 synthesis 10/10, 03 IR trace 13/13, 04 type attributes 12/12, 05 multi-version 12/12, 06 extreme JSON-serialize 16/16 + 2/2 optional). No accuracy regression from the 2.3.21 baseline. The agents' field experience confirmed the new 2.4 notes (e.g. `-Xcontext-parameters` redundancy, the JDK 25 BTAPI pin).
- Minor doc-gap candidates surfaced for a future patch (not 2.4.0-correctness issues): `ir-body-modification` should list the `irBlockBody` / `transformChildrenVoid` / `irSet` imports and the `IrConstImpl.Companion.double/long` constructors, and note that object-member calls keep a dispatch-receiver slot at `arguments[0]`; `multi-version-kotlin-support` could add a worked `buildscript{}` subproject snippet.

## [0.2.1] - 2026-05-27

### Added

- `SKILL.md` gains a **"How to plan parallel work"** section that prescribes a spec-first, fan-out workflow for non-trivial plugin work. Names the shared bottlenecks (`settings.gradle.kts`, `plugin/build.gradle.kts` deps, `META-INF/services/*`, the registrar's `registerExtensions(...)` body, shared `PluginNames`) that must be settled sequentially before fanning out FIR / IR / sample / testData branches as concurrent sub-agents.

## [0.2.0] - 2026-05-27

### Changed

- **Skill structure** (breaking for `~/.claude/plugins/cache` consumers): consolidate the previous 24 per-topic skills into a **single skill** named `kotlin-compiler-plugin`. The skill's `SKILL.md` is now a router; per-topic content lives at `skills/kotlin-compiler-plugin/references/<topic>/guide.md` (renamed from `SKILL.md`) and is read on demand. Per-topic `EVIDENCE.md` and `CHANGES.md` move alongside their `guide.md`. Cross-references inside guides become relative Markdown links (`[`<topic>`](../<topic>/guide.md)`). No reference-guide content changes beyond link form and stale `SKILL.md` → `guide.md` filename references. Top-level docs (README, NOTICE, CONTRIBUTING, evaluation/verification SPECs, sandbox runners) updated to the new paths.

### Validated against

- Kotlin 2.3.21
- Gradle 9.5.0
- JDK 21

All six accuracy benchmarks in `evaluation/` were re-run against the consolidated layout by fresh sub-agents and matched or exceeded the 0.1.1 baselines (`evaluation/<task>/RESULT.md` files updated in the same release). No accuracy regression.

## [0.1.1] - 2026-05-26

Documentation-only patch release. No behavioural changes; no new skills; no Kotlin version bump (still validated against Kotlin 2.3.21).

### Changed

- **`compiler-plugin-testing`**: substantially expanded based on field feedback from a real plugin project.
  - New "Exposing the plugin's runtime types to testData" section covering a second `EnvironmentConfigurator` that calls `addJvmClasspathRoot(...)` driven by a Gradle system property — the most-frequently-missing piece for plugins that ship annotation or runtime modules.
  - New guidance: extend the abstract `*Base` runner classes, not the concrete `*LightTree*` / `*Psi*` leaves (the latter implement `RunnerWithTargetBackendForTestGeneratorMarker` and break the JUnit-5 test generator).
  - New `IGNORE_DEXING` directive in box-runner `defaultDirectives` to skip D8/R8 for non-Android plugins.
  - `setLibraryProperty` is now fail-loud (`?: error(...)` instead of silently no-oping on missing `testArtifacts(...)` coordinates) and the surrounding gotcha was rewritten to match.
  - Extra `dependsOn(generateTests)` wiring for `kspTestKotlin` / `compileTestJava` to satisfy Gradle 8 warnings / Gradle 9 errors.
  - Notes on KMP module `-jvm-` jar naming, `workingDir`-relative `testDataRoot`, and `KotlinStandardLibrariesPathProvider`'s delegate pattern.
  - Clarified `configure(builder)` (abstract user hook) vs `configuration` (framework-internal property) with a direct GitHub permalink and corresponding EVIDENCE entry.
- **`fir-declaration-generation-extension`**: clarified that `SpecialNames.INIT` is mandatory only for synthesised classes that will actually be instantiated; pure marker classes used as discovery anchors can skip constructor generation. Cross-linked to `fir-predicate-system` for the `LookupPredicate.create { ... }` counterpart needed by `getSymbolsByPredicate`.
- **`fir-predicate-system`**: documented the "two predicates over the same FQN" convention — `register(...)` takes the common `AbstractPredicate<*>` base so either flavour can populate the session-wide index, but query-side type signatures still split `DeclarationPredicate` (for `matches`) from `LookupPredicate` (for `getSymbolsByPredicate`). Includes a copy-pasteable snippet with imports.

### Fixed

- `compiler-plugin-testing`: misidentified `configuration` as an abstract method in the 0.1.0 docs; it is in fact a property of type `TestConfigurationBuilder.() -> Unit`. Description and example now match the actual `AbstractKotlinCompilerTest` API.

## [0.1.0] - 2026-05-23

Initial release. Targets Kotlin 2.3.x.

### Skills

- **Foundation**: `compiler-plugin-bootstrap`, `compiler-plugin-debugging`, `compiler-plugin-testing`, `gradle-plugin-integration`, `multi-version-kotlin-support`
- **FIR (frontend)**: `fir-extensions-overview`, `fir-predicate-system`, `fir-additional-checkers-extension`, `fir-declaration-generation-extension`, `fir-supertype-generation-extension`, `fir-status-transformer-extension`, `fir-expression-resolution-extension`, `fir-session-components`, `fir-type-attribute-extension`, `fir-sam-conversion-transformer-extension`, `fir-assign-expression-alterer-extension`, `fir-function-type-kind-extension`, `fir-function-call-refinement-extension`, `fir-scripting-extensions`, `fir-repl-snippet-extensions`
- **IR (backend)**: `ir-plugincontext-usage`, `ir-call-rewriting`, `ir-body-modification`, `ir-synthetic-class-generation`

### Validated against

- Kotlin 2.3.21
- Gradle 9.5.0
- JDK 21

### Verification suite

`verification/` ships 13 working compiler-plugin Gradle projects: 11 cover the rows of the "How to choose" table in `fir-extensions-overview`, plus 2 additional probes — `12-cross-module-ir-visibility` for the IR-generated-declaration-visibility-across-modules claim, and `13-status-transformer-inline` for the `isInline` slot of `FirStatusTransformerExtension`. Every claim in the chooser table is backed by a working end-to-end build. The lone partial-pass entry is `FirFunctionCallRefinementExtension` (verification 11): FIR refinement runs, but fir2ir/JVM codegen requires substantial paired IR rewriting that is beyond a minimal verification — the SKILL flags this.

### Repo layout

- `skills/compiler-plugin-bootstrap/example/` — bootstrap skill's minimal reference (`hello-plugin`), co-located with the skill
- `verification/01..13/` — 11 per-extension verifications + 2 cross-cutting probes; each carries its own `SPEC.md` and `RESULT.md`
- `evaluation/01..06/` — agent-performance benchmark tasks
- `scripts/run-evaluation.sh`, `scripts/run-verification.sh` — sandbox runners that copy only the target `SPEC.md` into a tempdir, preventing accidental access to other answer keys
- Top-level layout is exactly three working-code roots: `skills/` (docs + tutorial example), `verification/` (fine-grained claim probes), `evaluation/` (comprehensive agent benchmarks)

The plugin uses standard semver independent of Kotlin's version — see the README's "Versioning policy" section. The Kotlin compatibility table in the README maps each plugin release to the Kotlin versions it was validated against.
