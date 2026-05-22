# Changelog

Plugin release history. For per-API Kotlin version migrations affecting plugin authors, see each skill's `CHANGES.md`.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

Initial development. Targeting Kotlin 2.3.x for the first published release (`0.1.0`).

### Skills planned for `0.1.0`

- **Foundation**: `compiler-plugin-bootstrap`, `compiler-plugin-debugging`, `compiler-plugin-testing`, `gradle-plugin-integration`, `multi-version-kotlin-support`
- **FIR (frontend)**: `fir-extensions-overview`, `fir-predicate-system`, `fir-additional-checkers-extension`, `fir-declaration-generation-extension`, `fir-supertype-generation-extension`, `fir-status-transformer-extension`, `fir-expression-resolution-extension`, `fir-session-components`, `fir-type-attribute-extension`, `fir-sam-conversion-transformer-extension`, `fir-assign-expression-alterer-extension`, `fir-function-type-kind-extension`, `fir-function-call-refinement-extension`
- **IR (backend)**: `ir-plugincontext-usage`, `ir-call-rewriting`, `ir-body-modification`, `ir-synthetic-class-generation`

### Validated against (development environment)

- Kotlin 2.3.21
- Gradle 9.5.0
- JDK 21

### Verification suite

`verification/` ships 13 working compiler-plugin Gradle projects: 11 cover the rows of the "How to choose" table in `fir-extensions-overview`, plus 2 additional probes — `12-cross-module-ir-visibility` for the IR-generated-declaration-visibility-across-modules claim, and `13-status-transformer-inline` for the `isInline` slot of `FirStatusTransformerExtension`. Every claim in the chooser table is now backed by a working end-to-end build. The lone partial-pass entry is `FirFunctionCallRefinementExtension` (verification 11): FIR refinement runs, but fir2ir/JVM codegen requires substantial paired IR rewriting that is beyond a minimal verification — the SKILL has been updated to flag this.

### Repo layout

- `skills/compiler-plugin-bootstrap/example/` — bootstrap skill's minimal reference (`hello-plugin`), co-located with the skill (moved from `examples/hello-plugin/`)
- `verification/01..13/` — 11 per-extension verifications + 2 cross-cutting probes; each carries its own `SPEC.md` and `RESULT.md`
- `evaluation/01..06/` — agent-performance benchmark tasks (renamed from `benchmark/01..06/`)
- `scripts/run-evaluation.sh`, `scripts/run-verification.sh` — sandbox runners that copy only the target `SPEC.md` into a tempdir, preventing accidental access to other answer keys
- Removed: `examples/02-validation-bootstrap`, `examples/03-validation-checker`, `examples/04-validation-call-rewrite` (subsumed by `verification/`; previously doubled as evaluation answer keys)
- Top-level layout is now exactly three working-code roots: `skills/` (docs + tutorial example), `verification/` (fine-grained claim probes), `evaluation/` (comprehensive agent benchmarks)

### Not yet released

No tagged release exists. The first tagged release will be `0.1.0`.

The plugin uses standard semver independent of Kotlin's version — see the README's "Versioning policy" section. The Kotlin compatibility table in the README maps each plugin release to the Kotlin versions it was validated against.
