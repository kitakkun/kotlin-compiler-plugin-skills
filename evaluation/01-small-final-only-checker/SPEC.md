# Benchmark 01 — `@FinalOnly` Checker (Small)

A minimal compiler plugin that adds a single custom diagnostic. Tests that a fresh agent can scaffold a project, register a FIR checker, declare a diagnostic factory, and produce a compile error against annotated user code.

## Specification

Implement a Kotlin compiler plugin that exposes the following user-facing contract:

```kotlin
// User code can declare:
package com.example.app
annotation class FinalOnly

// Then:
@FinalOnly class Ok                        // compiles cleanly
@FinalOnly open class Bad1                 // compile error: FINAL_ONLY_VIOLATED
@FinalOnly abstract class Bad2             // compile error: FINAL_ONLY_VIOLATED
@FinalOnly sealed class Bad3               // compile error: FINAL_ONLY_VIOLATED

// Class without @FinalOnly is unaffected:
open class Unrelated                       // compiles cleanly
```

The diagnostic should:
- Have factory name `FINAL_ONLY_VIOLATED` (uppercase, exact spelling)
- Be a hard `error` (not a warning)
- Render the message: `"Class annotated @FinalOnly must not be open, abstract, or sealed"`

## Project layout

```
evaluation/01-small-final-only-checker/work/
├── settings.gradle.kts
├── gradle.properties
├── build.gradle.kts
├── gradle/, gradlew      ← copy from skills/compiler-plugin-bootstrap/example/
├── plugin/               ← the compiler plugin module
└── sample/               ← exercises the plugin
    └── src/main/kotlin/com/example/app/Main.kt   ← contains the test classes above
```

## Acceptance criteria

| # | Criterion | How to verify |
|---|---|---|
| 1 | Project structure correct | `find work -name SKILL.md -o -name 'plugin.json' \| wc -l` returns 0; `find work -name '*.kt' \| wc -l` ≥ 5 |
| 2 | Plugin module builds | `./gradlew :plugin:jar` exits 0 |
| 3 | `Ok` class compiles | sample compile succeeds when only `@FinalOnly class Ok` is present |
| 4 | `Bad1` (open) errors with correct factory name | `./gradlew :sample:compileKotlin --rerun-tasks 2>&1 \| grep FINAL_ONLY_VIOLATED` matches |
| 5 | `Bad2` (abstract) errors with correct factory name | same grep matches |
| 6 | `Bad3` (sealed) errors with correct factory name | same grep matches |
| 7 | Error message text correct | output contains `"must not be open, abstract, or sealed"` |
| 8 | Unrelated classes are not affected | `open class Unrelated` (no annotation) compiles without error |
| 9 | Diagnostic positioning | error squiggle should be on the modality modifier (`open`/`abstract`/`sealed`), not the class name. Verifiable from compiler error output's column offset |

## Verification procedure

```bash
cd evaluation/01-small-final-only-checker/work

# 1. Build plugin module
./gradlew :plugin:jar

# 2. Run the sample compile and capture output
./gradlew :sample:compileKotlin --rerun-tasks --console=plain 2>&1 | tee /tmp/01-out.txt

# 3. Check expected errors and absences
grep -c FINAL_ONLY_VIOLATED /tmp/01-out.txt              # should be ≥ 3 (Bad1, Bad2, Bad3)
grep "must not be open, abstract, or sealed" /tmp/01-out.txt
grep -c "Unrelated" /tmp/01-out.txt                       # should be 0 (no diagnostic on it)
```

## Skills exercised

| Skill | Used for |
|---|---|
| `compiler-plugin-bootstrap` | project layout, registrar, services file, `pluginId` constant, sample wiring |
| `fir-extensions-overview` | `FirExtensionRegistrar` + `FirExtensionRegistrarAdapter` |
| `fir-additional-checkers-extension` | `FirRegularClassChecker`, `KtDiagnosticsContainer`, `error0` factory, renderer factory, `MppCheckerKind`, context-parameter `check(...)` signature |
| `fir-predicate-system` *(optional)* | annotation-driven filtering; agent could use `declaration.hasAnnotation` directly instead |
| `fir-additional-checkers-extension/CHANGES.md` | `-Xcontext-parameters` compiler flag requirement |

## Common failure modes (anti-cheat)

Watch the agent's output for these:

1. **`pluginId` not overridden** → registrar fails to instantiate, plugin doesn't load. Sample compiles silently with no diagnostic.
2. **`check(declaration, context, reporter)` value-param form** → override doesn't take effect. Class with abstract `check` fails to compile, OR plugin loads but checker silently does nothing.
3. **`KtDiagnosticFactoryToRendererMap("...")` direct constructor call** → `internal constructor` compile error; must use `by` delegate.
4. **Wrong package: `org.jetbrains.kotlin.diagnostics.rendering.KtDiagnosticFactoryToRendererMap`** → resolves to a different older class (`DiagnosticFactoryToRendererMap`), unexpected behavior.
5. **`registerDiagnosticContainers(...)` not called** → checker fires but raises `IllegalStateException: Diagnostic factory was not registered`.
6. **Missing `-Xcontext-parameters` in plugin's `freeCompilerArgs`** → the checker's `context(...)` signature won't compile.
7. **Squiggle on class name instead of modifier** → wrong `SourceElementPositioningStrategy`; should be `MODALITY_MODIFIER`.

## Estimated effort

A correctly-skilled agent should complete this in **1 round** of work (under 20 minutes) without needing to consult Kotlin source directly. If the agent has to grep `kotlin/compiler/...` more than once or twice, the skill probably has a gap.

## Pre-existing reference

`verification/01-additional-checkers/` contains a similar (`@MustBeFinal`) implementation already validated to work. **Do not let agents copy from it during evaluation** — the test is whether the *skills* are sufficient, not whether the agent can copy. Configure the agent working directory to exclude `verification/` if needed.
