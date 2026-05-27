# Evaluation Result: 05-multiversion-final-checker — 2026-05-27 (post-consolidation re-run)

**Skills version**: kotlin-compiler-plugin@0.1.1 (single-skill consolidated layout, commit 463287a on real repo)
**Kotlin version validated against**: 2.3.21 (and 2.2.20 for the per-version JAR)

In practice the per-version `compileOnly` pins used here were `kotlin-compiler-embeddable:2.3.20` and `kotlin-compiler-embeddable:2.2.20`, matching the spec table. Sample Kotlin Gradle plugin pins are `2.3.20` and `2.2.20` (one per sample, via per-subproject `buildscript {}`). The single shared Kotlin Gradle plugin used by the plugin and plugin-common modules is `2.3.21`.

## Strategy choice

**Option B** — shared `plugin-common/` + per-version `plugin-2.2/` and `plugin-2.3/` overlays. Documented in `STRATEGY.md` at the project root and in the KDoc on every per-version `FinalCheckerComponentRegistrar.kt`. The two overlays differ in exactly two places: (1) the `compileOnly("...kotlin-compiler-embeddable:2.X.20")` pin in `build.gradle.kts`; (2) the presence/absence of `override val pluginId` on `CompilerPluginRegistrar`. Every other source file is byte-identical between the overlays (verified with `diff`).

## Project layout

```
/tmp/kotlin-skill-eval-05-multiversion-final-checker-20260527-204101/
├── settings.gradle.kts            (includes plugin-common, plugin-2.2, plugin-2.3, sample-2.2, sample-2.3)
├── build.gradle.kts
├── gradle.properties              (kotlin.compiler.execution.strategy=in-process)
├── gradlew{,.bat}                 (copied from skill bootstrap example)
├── gradle/wrapper/                (copied from skill bootstrap example)
├── plugin-common/                 (KGP 2.3.21, JVM jar with FinalCheckerPluginNames const vals)
├── plugin-2.2/                    (compileOnly kotlin-compiler-embeddable:2.2.20, -Xcontext-parameters)
├── plugin-2.3/                    (compileOnly kotlin-compiler-embeddable:2.3.20, -Xcontext-parameters)
├── sample-2.2/                    (buildscript classpath kotlin-gradle-plugin:2.2.20)
├── sample-2.3/                    (buildscript classpath kotlin-gradle-plugin:2.3.20)
└── STRATEGY.md
```

`plugin-common` is consumed via `implementation(project(":plugin-common"))`. `FinalCheckerPluginNames` exposes only `const val` strings, so the plugin's overlay classes inline the constants at compile time and plugin-common's runtime presence in the consumer's compiler classloader is not required.

## Acceptance Criteria

| # | Criterion | Result | Notes |
|---|---|---|---|
| 1 | Both plugin JARs build | ✅ | `./gradlew :plugin-2.2:jar :plugin-2.3:jar` — BUILD SUCCESSFUL in 7s, 8 tasks executed. |
| 2 | `plugin-2.2.jar` exists | ✅ | `plugin-2.2/build/libs/plugin-2.2.jar` (19 entries, 26 738 bytes). |
| 3 | `plugin-2.3.jar` exists | ✅ | `plugin-2.3/build/libs/plugin-2.3.jar`. |
| 4 | 2.2 jar declares registrar service | ✅ | `unzip -p .../plugin-2.2.jar META-INF/services/...CompilerPluginRegistrar` → `com.example.finalchecker.FinalCheckerComponentRegistrar`. |
| 5 | 2.3 jar declares registrar service | ✅ | same content on the 2.3 jar. |
| 6 | 2.3 registrar overrides `pluginId` | ✅ | `plugin-2.3/.../FinalCheckerComponentRegistrar.kt` has `override val pluginId: String = FinalCheckerPluginNames.PLUGIN_ID`. |
| 7 | 2.2 registrar does NOT override `pluginId` | ✅ | `plugin-2.2/.../FinalCheckerComponentRegistrar.kt` has no `override val pluginId` line (only a KDoc mention that explains *why* it is omitted). The 2.2.20 build would have failed with "'pluginId' overrides nothing" if it did — the green build is the proof. |
| 8 | Both plugins use context-parameter `check` form | ✅ | `grep -rE 'context\([^)]*CheckerContext[^)]*DiagnosticReporter\)' plugin-2.2/src plugin-2.3/src` matches one line in each `FinalRegularClassChecker.kt`. The `override fun check(declaration: FirRegularClass)` is on the line below. |
| 9 | `sample-2.2:compileKotlin` produces 3 `FINAL_VIOLATED` errors | ✅ | `grep -c FINAL_VIOLATED /tmp/05-sample22.txt` → 3. Errors on `Bad1`/`Bad2`/`Bad3` (lines 6/7/8). |
| 10 | `sample-2.3:compileKotlin` produces 3 `FINAL_VIOLATED` errors | ✅ | `grep -c FINAL_VIOLATED /tmp/05-sample23.txt` → 3. Same line numbers. |
| 11 | Neither sample errors on `Ok` or `Unrelated` | ✅ | Diagnostic appears exactly 3 times in each build log; `grep -E 'Ok\|Unrelated' /tmp/05-sample*.txt` returns nothing. |
| 12 | Strategy documented (Option A or B) | ✅ | `STRATEGY.md` at the project root + KDoc on both `FinalCheckerComponentRegistrar.kt` files and on `FinalCheckerPluginNames.kt`. |
| 13 (bonus) | Per-sample KGP version independently overridable via `-Pkotlin.version=...` | ❌ | Not implemented. The two samples hard-code their KGP version in their respective `buildscript {}` blocks. Would require parameterising the `classpath(...)` coordinate from a Gradle property — feasible but skipped (criteria 1–12 are mandatory). |

## Score: 12 / 12 mandatory (criterion 13 bonus not attempted)

## Build commands actually executed (and verified)

```bash
# From the sandbox root:
./gradlew :plugin-2.2:jar :plugin-2.3:jar --console=plain        # BUILD SUCCESSFUL
ls plugin-2.2/build/libs/ plugin-2.3/build/libs/                  # both jars present
unzip -p plugin-2.2/build/libs/plugin-2.2.jar \
  META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
# -> com.example.finalchecker.FinalCheckerComponentRegistrar
unzip -p plugin-2.3/build/libs/plugin-2.3.jar \
  META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
# -> com.example.finalchecker.FinalCheckerComponentRegistrar

./gradlew :sample-2.2:compileKotlin --console=plain               # BUILD FAILED (expected)
grep -c FINAL_VIOLATED /tmp/05-sample22.txt                       # 3

./gradlew :sample-2.3:compileKotlin --console=plain               # BUILD FAILED (expected)
grep -c FINAL_VIOLATED /tmp/05-sample23.txt                       # 3
```

Both sample compiles fail with exit code 1 (because the diagnostic is an error, not a warning). The build log lines for each sample:

```
e: .../sample-2.X/src/main/kotlin/com/example/app/Main.kt:6:8 [FINAL_VIOLATED] Class annotated @Final must not be open, abstract, or sealed
e: .../sample-2.X/src/main/kotlin/com/example/app/Main.kt:7:8 [FINAL_VIOLATED] Class annotated @Final must not be open, abstract, or sealed
e: .../sample-2.X/src/main/kotlin/com/example/app/Main.kt:8:8 [FINAL_VIOLATED] Class annotated @Final must not be open, abstract, or sealed
```

Lines 6/7/8 in `Main.kt` are `Bad1`/`Bad2`/`Bad3`. The factory name `[FINAL_VIOLATED]` appears because each sample's `KotlinCompile` adds `-Xrender-internal-diagnostic-names` to `freeCompilerArgs`.

## Skill-doc usage and gaps

### What the skill docs got exactly right

- **`multi-version-kotlin-support/guide.md`**: the per-subproject `buildscript {}` workaround for "two KGP versions in one Gradle build" is described verbatim (lines around the "Multiple Kotlin Gradle plugin versions in a single build" section). I applied that pattern unchanged for `sample-2.2` and `sample-2.3` and it worked first try.
- **`multi-version-kotlin-support/guide.md`** also flags the `kotlin.compiler.execution.strategy=in-process` daemon-noise mitigation, which I copied into `gradle.properties`. The build is quiet.
- **`fir-additional-checkers-extension/CHANGES.md`**: the table explicitly states that 2.2.20 and 2.3.x both use only the context-parameter `check` form. That let me write a single checker body for both overlays without trial-and-error.
- **`fir-additional-checkers-extension/guide.md`**: the `KtDiagnosticFactoryToRendererMap` `by`-delegate pattern (instead of the now-internal constructor) was used as-is, and the `-Xrender-internal-diagnostic-names` flag for the consumer was applied where the guide instructs.
- **`compiler-plugin-bootstrap/guide.md`**: the registrar / `META-INF/services` / `-Xplugin=` wiring transferred without modification. Reusing the example `gradlew`/`gradle/wrapper/` directly (per the task's "you may copy boilerplate" rule) cut bootstrap time substantially.
- **`compiler-plugin-bootstrap/guide.md`'s `pluginId` gotcha**: the "exists since 2.3, absent on 2.2.20" callout is the exact knowledge that drove the per-overlay registrar split.

### Minor gaps I worked around (not blockers)

- **The `-Xcontext-parameters` flag must be added on the plugin module's compile task** is stated in the additional-checkers guide. The multi-version guide doesn't explicitly remind the reader that both per-version plugin overlays still need it. (For a multi-version reader who only Reads `multi-version-kotlin-support/guide.md`, that detail lives one skill away — easy to miss for an agent that doesn't open the checkers guide first.) Adding a one-line cross-reference in `multi-version-kotlin-support/guide.md` ("each per-version plugin module still needs `-Xcontext-parameters` as described in the checkers guide") would close the gap.
- **The `plugin-common` runtime story** isn't spelled out. With `implementation(project(":plugin-common"))` and a plain `jar` task (no shadowJar), `plugin-common`'s classes do **not** ship inside the per-version plugin JAR — the consumer's `-Xplugin=` only points to that JAR. This is harmless when `plugin-common` exposes only `const val` strings (Kotlin inlines them at compile time, which is the case here), but a reader who put non-constant code in `plugin-common` and shipped it the same way would see `NoClassDefFoundError` at the consumer's compile time. A short note in `multi-version-kotlin-support/guide.md` ("if you split out shared code, either keep it to `const val` constants or use shadowJar to bundle it into each per-version JAR") would be a useful guardrail. The current `gradle-plugin-integration/guide.md` covers shadowJar in general but doesn't say "in Option-B overlays, prefer const vals or bundle".
- **`KotlinJvmProjectExtension` access from a `buildscript {}`-applied plugin**: when the sample's KGP is applied via `apply(plugin = ...)` rather than the `plugins {}` DSL, the typed `kotlin { jvmToolchain(21) }` accessor isn't generated. The workaround used here is `configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> { jvmToolchain(21) }`. A worked example in `multi-version-kotlin-support/guide.md` showing the `configure<...>` form alongside the `buildscript {}` block would prevent a likely mis-step.
- **Spec criterion 8's verification grep** (`fun check\([^)]*CheckerContext[^)]*DiagnosticReporter`) would not match a context-parameter-form override that places the context on a separate line. The SPEC narrative is right (both plugins use the context-param form); the literal grep in the verification snippet is loose. Not a skill-doc problem — flagging here only for the evaluation maintainer.

### Things I did NOT need

- Reflective access (Strategy 2): unnecessary because the SPEC's API drift is exactly one line and the skill docs walked me through which line.
- A compat-shim interface (Strategy 4): overkill for a two-version 2.2/2.3 split with one differing line.
- A source preprocessor (Strategy 3): same.
- `shadowJar`: the `plugin-common` constants inline, so each per-version JAR is self-contained.

## Deviations from SPEC.md

- I added `Final.kt` (the `annotation class Final`) directly under `sample-2.X/src/main/kotlin/com/example/finalchecker/` rather than reusing the `plugin-common` package. The SPEC's snippet states "annotation class Final" lives in user code, so having each sample own its own `Final.kt` mirrors the SPEC literally and avoids accidentally coupling the sample to `plugin-common`'s classpath.
- I used `kotlin-compiler-embeddable:2.3.20` for the 2.3 plugin (matching the SPEC table), not `2.3.21`. The task header line says "Kotlin version validated against: 2.3.21" but the SPEC's per-subproject table says `2.3.20`. I followed the SPEC table.
- I did not attempt the bonus criterion 13 (`-Pkotlin.version=` per-sample override). The two samples hard-code their KGP version.

## Approximate iteration count

3 rounds (rough): (1) wrote everything, (2) ran `:plugin-2.2:jar :plugin-2.3:jar` — first-try green, (3) ran each sample's `compileKotlin` — both produced the expected three errors first try. No debug loop on the Gradle multi-version plumbing — the skill's "buildscript {} per subproject" guidance was sufficient.

## Verdict

The consolidated `kotlin-compiler-plugin` skill provided everything needed for this task. The multi-version layer in particular felt complete: the `buildscript {}` workaround, the daemon-noise mitigation, the per-version `compileOnly` pattern, and the explicit "`pluginId` exists since 2.3" callout each saved a debug round. The two minor gaps I noted (cross-skill reminder about `-Xcontext-parameters` per per-version plugin; `plugin-common` runtime caveat; `configure<KotlinJvmProjectExtension>` for buildscript-applied plugins) are small documentation polish items, not failures.
