# Changelog

Plugin release history. For per-API Kotlin version migrations affecting plugin authors, see each skill's `CHANGES.md`.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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
