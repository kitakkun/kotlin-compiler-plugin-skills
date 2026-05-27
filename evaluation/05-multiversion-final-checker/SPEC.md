# Benchmark 05 — `@Final` Checker, Kotlin 2.2.20 + 2.3.20 Multi-Version (High)

A simple compiler plugin (the `@Final` declaration checker, semantically similar to evaluation 01's `@FinalOnly`) that **builds and runs against two distinct Kotlin compiler versions**. Tests `multi-version-kotlin-support` — the strategy of shipping a per-version plugin artefact so consumers on different Kotlin versions can use the plugin without forcing them to upgrade.

The functional behaviour is intentionally minimal so the evaluation scores the multi-version mechanics, not the checker logic.

## Specification

### The plugin

Provide a checker that errors when a class annotated `@Final` is declared with `open`, `abstract`, or `sealed` modality. Functional spec:

```kotlin
package com.example.finalchecker
annotation class Final

@Final class Ok                 // OK
@Final open class Bad1          // ERROR: FINAL_VIOLATED
@Final abstract class Bad2      // ERROR: FINAL_VIOLATED
@Final sealed class Bad3        // ERROR: FINAL_VIOLATED
class Unrelated                 // OK (no annotation)
```

The diagnostic factory is `FINAL_VIOLATED`, message `"Class annotated @Final must not be open, abstract, or sealed"`, positioning strategy `MODALITY_MODIFIER`.

### The multi-version requirement

The plugin must produce **two compiled JARs**, one per Kotlin compiler version:

| Subproject | `kotlin-compiler-embeddable` version | Output |
|---|---|---|
| `plugin-2.2` | 2.2.20 | `plugin-2.2/build/libs/plugin-2.2.jar` |
| `plugin-2.3` | 2.3.20 | `plugin-2.3/build/libs/plugin-2.3.jar` |

Each JAR must be loadable by its corresponding Kotlin compiler at runtime — a JAR built against 2.3.20 will *not* load on 2.2.20 (and vice versa) because of ABI drift in `kotlin-compiler-embeddable`.

### How sources are split

The agent has two reasonable structures (either is acceptable; the SPEC doesn't dictate which):

**Option A — full duplication**: `plugin-2.2/src/main/kotlin/...` and `plugin-2.3/src/main/kotlin/...` each contain the entire plugin tailored to that version.

**Option B — shared common + version-specific overlays**:

```
plugin-common/       # source-version-agnostic: annotation FQN, plugin ID constants, names
plugin-2.2/          # depends on plugin-common; checker code using 2.2.x API forms
plugin-2.3/          # depends on plugin-common; checker code using 2.3.x API forms
```

Option B is preferred (it minimises duplication), but Option A scores equivalently if the implementation is correct.

### Why the API differs across versions

The actual divergence between Kotlin 2.2.20 and 2.3.20 (verified empirically against `git show v2.2.20:` and `v2.3.20:` of the relevant files):

- **`CompilerPluginRegistrar.pluginId`** is **abstract in 2.3.20**, **does not exist in 2.2.20**. A 2.3-targeted plugin must override it; a 2.2.20-targeted plugin must NOT (overriding fails with "'pluginId' overrides nothing"). The two `ComponentRegistrar` files therefore diverge on this single line.
- **`FirDeclarationChecker.check`** signature: **both 2.2.20 and 2.3.x use the same context-parameter form** (`context(context: CheckerContext, reporter: DiagnosticReporter) abstract fun check(declaration: D)`). The value-parameter migration completed *between* 2.2.0 and 2.2.20 — 2.2.0 had both forms, 2.2.20 had only the context-param one. So **the checker class body can be identical across the two plugin variants**.
- **`KtDiagnosticFactoryToRendererMap`**: the constructor became `internal` somewhere in the 2.x series; using the `by` delegate factory (`override val MAP by KtDiagnosticFactoryToRendererMap("...") { ... }`) works on both 2.2.20 and 2.3.x.
- `FirSimpleFunction` → `FirNamedFunction` rename happened in 2.3.20, but only matters if you write a `FirSimpleFunctionChecker`. This evaluation uses `FirRegularClassChecker`, so the rename doesn't apply.
- `-Xcontext-parameters` flag is required on both plugin subprojects (the plugin Kotlin code uses context parameters for its own `check` override; this is a separate concern from the 2.2-vs-2.3 distinction).

So the only line that genuinely differs between the two `ComponentRegistrar` files is `override val pluginId`. Both checker classes can be byte-identical (modulo the trivially-different package-or-class names, if the agent chose to keep them in sibling subprojects). The evaluation's hard part is the **Gradle multi-version plumbing**, not the API code per se.

### Sample modules

Two sample modules verify each plugin variant:

| Sample | Kotlin version | Plugin JAR loaded |
|---|---|---|
| `sample-2.2` | 2.2.20 | `plugin-2.2.jar` |
| `sample-2.3` | 2.3.20 | `plugin-2.3.jar` |

Each sample contains `Main.kt` with the test classes from the spec. Compiling either sample must produce three `FINAL_VIOLATED` errors (on `Bad1`, `Bad2`, `Bad3`) and zero on `Ok`/`Unrelated`.

### Project layout

```
evaluation/05-multiversion-final-checker/work/
├── settings.gradle.kts            ← includes plugin-common, plugin-2.2, plugin-2.3, sample-2.2, sample-2.3
├── build.gradle.kts
├── gradle.properties              ← can default Kotlin version, but builds use per-subproject pinning
├── gradle/, gradlew, gradlew.bat  ← copy from skills/kotlin-compiler-plugin/references/compiler-plugin-bootstrap/example/
├── plugin-common/                 ← (if using Option B)
│   └── src/main/kotlin/com/example/finalchecker/FinalCheckerPluginNames.kt
├── plugin-2.2/
│   ├── build.gradle.kts           ← compileOnly("...kotlin-compiler-embeddable:2.2.20"), -Xcontext-parameters NOT required (2.2 uses value-param form)
│   └── src/main/kotlin/...        ← FirRegularClassChecker using value-param check(declaration, context, reporter)
├── plugin-2.3/
│   ├── build.gradle.kts           ← compileOnly("...kotlin-compiler-embeddable:2.3.20"), -Xcontext-parameters required
│   └── src/main/kotlin/...        ← FirRegularClassChecker using context(...) check(declaration)
├── sample-2.2/
│   ├── build.gradle.kts           ← Kotlin 2.2.20 toolchain, -Xplugin=plugin-2.2.jar
│   └── src/main/kotlin/com/example/app/Main.kt
└── sample-2.3/
    ├── build.gradle.kts           ← Kotlin 2.3.20 toolchain, -Xplugin=plugin-2.3.jar
    └── src/main/kotlin/com/example/app/Main.kt
```

The Gradle setup must configure DIFFERENT Kotlin Gradle plugin versions per sample subproject — typically achieved by declaring the plugins block once at the root with `apply false` and applying `kotlin("jvm") version "<version>"` per subproject. Or use Gradle's `pluginManagement.resolutionStrategy` to switch versions. The agent picks an approach.

### `Main.kt` (identical in both samples):

```kotlin
package com.example.app

import com.example.finalchecker.Final

@Final class Ok
@Final open class Bad1
@Final abstract class Bad2
@Final sealed class Bad3
class Unrelated

fun main() {
    println("compiled with @Final loaded")
}
```

A separate `Main.kt` without violations should compile cleanly (used to verify the plugin doesn't error on annotation-free code). The evaluation uses the violating sample for diagnostic verification and a clean sample for non-error verification.

## Acceptance criteria

| # | Criterion | How to verify |
|---|---|---|
| 1 | Both plugin JARs build | `./gradlew :plugin-2.2:jar :plugin-2.3:jar` succeeds |
| 2 | `plugin-2.2.jar` exists in expected location | `test -f plugin-2.2/build/libs/plugin-2.2.jar` |
| 3 | `plugin-2.3.jar` exists in expected location | `test -f plugin-2.3/build/libs/plugin-2.3.jar` |
| 4 | `plugin-2.2.jar` declares the registrar service | `unzip -p plugin-2.2/build/libs/plugin-2.2.jar META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar` returns the FQN |
| 5 | `plugin-2.3.jar` declares the registrar service | same check on 2.3 jar |
| 6 | 2.3 plugin's `ComponentRegistrar` overrides `pluginId` (mandatory abstract in 2.3) | grep `override val pluginId` in plugin-2.3 sources |
| 7 | 2.2 plugin's `ComponentRegistrar` does **NOT** override `pluginId` (the member does not exist in 2.2.20's `CompilerPluginRegistrar`; overriding fails with "'pluginId' overrides nothing") | grep -L `override val pluginId` in plugin-2.2/src/.../ComponentRegistrar.kt |
| 8 | Both plugins use context-parameter `check` form (verified empirically: `v2.2.20` and `v2.3.20` of `FirDeclarationChecker.kt` both have only the context-parameter abstract; the value-parameter migration completed between 2.2.0 and 2.2.20) | grep `context(.*CheckerContext.*DiagnosticReporter)` in plugin-2.2 AND plugin-2.3 sources |
| 9 | `sample-2.2:compileKotlin` produces 3 `FINAL_VIOLATED` errors on `Bad1`/`Bad2`/`Bad3` | run with `-Xrender-internal-diagnostic-names` and grep |
| 10 | `sample-2.3:compileKotlin` produces 3 `FINAL_VIOLATED` errors on `Bad1`/`Bad2`/`Bad3` | same |
| 11 | Neither sample errors on `Ok` or `Unrelated` | the diagnostic appears exactly 3 times in each build log |
| 12 | The plugin's source code is documented for which strategy was chosen (Option A or B) | a brief `STRATEGY.md` or top-level comment in `plugin-2.x/src/.../FinalCheckerComponentRegistrar.kt` |
| 13 *(optional)* | Both samples' Kotlin Gradle plugin versions are independently configurable | running with `-Pkotlin.version=...` overrides per sample (advanced) |

Criteria 1–12 are mandatory; 13 is bonus.

## Verification procedure

```bash
cd evaluation/05-multiversion-final-checker/work

# Build both plugin JARs
./gradlew :plugin-2.2:jar :plugin-2.3:jar --console=plain 2>&1 | tee /tmp/05-build.txt

# Verify JAR existence and service registrations
test -f plugin-2.2/build/libs/plugin-2.2.jar
test -f plugin-2.3/build/libs/plugin-2.3.jar
unzip -p plugin-2.2/build/libs/plugin-2.2.jar META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
unzip -p plugin-2.3/build/libs/plugin-2.3.jar META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar

# Compile sample-2.2 — expect FINAL_VIOLATED ×3
./gradlew :sample-2.2:compileKotlin --console=plain 2>&1 | tee /tmp/05-sample22.txt
[ "$(grep -c 'FINAL_VIOLATED' /tmp/05-sample22.txt)" -ge 3 ]

# Compile sample-2.3 — expect FINAL_VIOLATED ×3
./gradlew :sample-2.3:compileKotlin --console=plain 2>&1 | tee /tmp/05-sample23.txt
[ "$(grep -c 'FINAL_VIOLATED' /tmp/05-sample23.txt)" -ge 3 ]

# Source-shape checks
grep -rE 'override val pluginId' plugin-2.2/src plugin-2.3/src
grep -rE 'fun check\([^)]*CheckerContext[^)]*DiagnosticReporter' plugin-2.2/src
grep -rE 'context\([^)]*CheckerContext[^)]*DiagnosticReporter\)' plugin-2.3/src
```

## Skills exercised

| Skill | Used for |
|---|---|
| `compiler-plugin-bootstrap` | project layout, `CompilerPluginRegistrar`, META-INF/services |
| `fir-extensions-overview` | FIR extension wiring |
| `fir-additional-checkers-extension` | `FirRegularClassChecker`, `MODALITY_MODIFIER`, diagnostic factory |
| `fir-additional-checkers-extension` CHANGES.md | the 2.2→2.3 context-parameter migration that distinguishes the two plugin variants |
| `multi-version-kotlin-support` | strategy choice (multi-source-set with per-version `compileOnly`), Gradle wiring for two `kotlin-compiler-embeddable` versions, per-sample Kotlin Gradle plugin pinning |
| `gradle-plugin-integration` | Gradle module layout, task dependencies, classpath scoping |

## Forbidden reference material

- All `verification/` subdirectories — they contain working reference implementations of the FIR / IR extensions; off-limits per evaluation policy.
- `/Users/kitakkun/Documents/GitHub/kotlin-compiler-plugin-skills/evaluation/01-small-final-only-checker/work/` — previous evaluation answer; the SPEC of 01 is allowed but not the work/ directory's source code
- `/Users/kitakkun/Documents/GitHub/kotlin-compiler-plugin-skills/evaluation/02-medium-auto-stringify/work/`, `03-high-trace-plugin/work/`, `04-veryhigh-positive-negative-types/work/`

`skills/kotlin-compiler-plugin/references/compiler-plugin-bootstrap/example/` is allowed for project structure boilerplate.

## Common failure modes (anti-cheat)

1. **One JAR for both versions** — using `compileOnly("...embeddable:2.3.20")` for both subprojects produces a single-version plugin; the 2.2 sample compile then fails with `NoSuchMethodError` or similar at plugin load time. Each subproject must pin to ITS Kotlin version.
2. **Overriding `pluginId` on the 2.2 ComponentRegistrar** — the abstract member doesn't exist in 2.2.20's `CompilerPluginRegistrar`, so an `override val pluginId` line fails with "'pluginId' overrides nothing". The 2.2 registrar must omit this override; the 2.3 registrar must include it.
3. **NOT overriding `pluginId` on the 2.3 ComponentRegistrar** — symmetric: 2.3's registrar makes `pluginId` abstract, and a registrar without it fails to load with "is not abstract and does not implement abstract member 'pluginId'".
4. **Trying to use value-parameter `check`** — both 2.2.20 and 2.3.x use the context-parameter form; the value-parameter form was removed between 2.2.0 and 2.2.20. Either both subprojects use context parameters or one of them silently fails to override.
5. **`-Xcontext-parameters` flag missing** — both plugin subprojects need it because both use the context-parameter `check` override. Compile fails with "context parameters are experimental".
6. **`-Xrender-internal-diagnostic-names` missing on samples** — diagnostic factory name doesn't appear in compile output, so the grep-based verification can't match.
7. **Single `plugins {}` block trying to apply different Kotlin versions** — Gradle's modern `plugins {}` block resolves each plugin once per build. To use different KGP versions in different subprojects, fall back to per-subproject `buildscript {}` blocks (see `multi-version-kotlin-support` SKILL.md for the canonical pattern).
8. **Wrong Kotlin Gradle plugin version per sample** — `sample-2.2` must use KGP 2.2.20 and `sample-2.3` must use KGP 2.3.20. If the same KGP is applied to both, one of them is compiling with the wrong toolchain.
9. **Strategy not documented** — criterion 12 looks for explicit acknowledgement of the split (Option A or B). A bare implementation without a `STRATEGY.md` or comment loses that point.
10. **`KtDiagnosticFactoryToRendererMap` direct constructor** — the constructor is `internal`; use the `by` delegate factory in both versions.

## Estimated effort

A correctly-skilled agent should complete the core (criteria 1–12) in **6–8 rounds**. The Gradle multi-subproject configuration with per-version Kotlin compiler pinning is the trickiest part — expect at least one debug round on the build script. The `check` signature divergence between 2.2 and 2.3 is well-documented in `fir-additional-checkers-extension/CHANGES.md`, so the agent should hit it on first pass.

If the agent finishes in fewer than 6 rounds, suspect that they took shortcuts (e.g. one shared source set with reflection workarounds, or shipping a single 2.3-only JAR that crashes on 2.2 sample compile).

## Score weighting

This task carries **3× the weight** of the small task in overall scoring, reflecting the meaningful Gradle setup complexity and the deep dependency on both `multi-version-kotlin-support` and `fir-additional-checkers-extension` CHANGES.md.

## Pre-existing reference

`skills/kotlin-compiler-plugin/references/compiler-plugin-bootstrap/example/` provides single-version boilerplate. The agent must extend that pattern for two versions.
