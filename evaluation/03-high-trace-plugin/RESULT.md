# Evaluation Result: 03-high-trace-plugin — 2026-05-27 (post-consolidation re-run)
**Skills version**: kotlin-compiler-plugin@0.1.1 (single-skill consolidated layout, commit 463287a on real repo)
**Kotlin version validated against**: 2.3.21

**Agent**: Claude Opus 4.7 (claude-opus-4-7)

## Summary

Implemented `@Trace` plugin end-to-end from the consolidated skill router in 1 build round + 2 diagnostic verification rounds + 1 exception-path verification round. The plugin builds cleanly, the sample runs, and all 12 SPEC acceptance criteria pass. Optional criterion 13 (`:plugin:test` integration tests) was not pursued to stay within iteration budget.

## Acceptance Criteria

| # | Criterion | Result | Notes |
|---|---|---|---|
| 1 | Plugin builds (`./gradlew :plugin:jar`) | PASS | Clean build, no warnings |
| 2 | Sample compiles | PASS | |
| 3 | Sample runs without crashing | PASS | `BUILD SUCCESSFUL`, exit 0 |
| 4 | `greet` traced on entry | PASS | `-> greet(Alice)` present |
| 5 | `greet` traced on exit | PASS | `<- greet` present |
| 6 | `add` traced on entry/exit | PASS | `-> add(3, 4)` / `<- add` |
| 7 | `multiply` traced on entry/exit | PASS | `-> multiply(2, 5)` / `<- multiply` |
| 8 | `helper` NOT traced (private excluded) | PASS | Reflection call succeeds without emitting `-> helper(` |
| 9 | Original behavior preserved | PASS | `Hello, Alice`, `7`, `10` all printed |
| 10 | `@Trace inline fun` → `TRACE_ON_INLINE` | PASS | `e: ... @Trace cannot be applied to inline functions` |
| 11 | `@Trace operator fun` → `TRACE_ON_OPERATOR` | PASS | `e: ... @Trace cannot be applied to operator functions` (fires alongside Kotlin's own operator-name error on `foo`, as expected) |
| 12 | Exception path still prints `<- name` | PASS | With `@Trace fun thrower() { error("oops") }` invoked under `try/catch`, output shows `-> thrower()` then `<- thrower` then the caught exception message |
| 13 | (optional) Plugin tests pass | SKIP | Not attempted; iteration budget reserved for criteria 1-12 |

## Score: 12 / 12 mandatory (13 / 13 if optional counted; the optional was skipped, not failed)

## What was built

Final tree (under `/tmp/kotlin-skill-eval-03-high-trace-plugin-20260527-204059/`):

```
settings.gradle.kts        (root project + 2 includes)
build.gradle.kts           (mavenCentral)
gradle.properties          (empty)
gradle/                    (copied from skill's bootstrap example)
gradlew, gradlew.bat
plugin/
  build.gradle.kts         (kotlin("jvm") 2.3.21 + kotlin-compiler-embeddable
                            compileOnly + -Xcontext-parameters)
  src/main/
    resources/META-INF/services/
      org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
      org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
    kotlin/com/example/trace/
      TracePluginNames.kt
      TraceCommandLineProcessor.kt
      TraceComponentRegistrar.kt
      fir/
        TraceDiagnostics.kt           (KtDiagnosticsContainer + renderer)
        TraceCheckers.kt              (FirSimpleFunctionChecker x2 + extension)
        TraceFirExtensionRegistrar.kt
      ir/
        TraceIrGenerationExtension.kt (body-wrap try/finally with println)
sample/
  build.gradle.kts         (application + compilerPlugin configuration + -Xplugin=)
  src/main/kotlin/com/example/app/Main.kt
```

Approximate plugin LoC: ~210 (10 source files, all small).

## Key implementation decisions

- **One IR transform, no `FirStatusTransformerExtension`.** The "class-level `@Trace` propagates to public members" rule is handled directly in IR by checking `function.parent as IrClass` for the annotation. Cleaner than a status transformer + predicate dance and avoids the "double-trace" failure mode listed in SPEC's "common failure modes" #5.
- **Visibility filter via `DescriptorVisibilities.PUBLIC`.** The skill correctly warns against the `Visibilities` (capital V) lowercase trap; using `DescriptorVisibilities` worked first try.
- **Try/finally via builder DSL.** Used `irBlockBody { +irCall(printlnSymbol) ... ; +irTry(type, tryResult=irBlock {...}, catches=emptyList(), finallyExpression=irCall(println)) }` exactly as documented in `ir-body-modification/guide.md`. Both the "wrong type" trap and the "helper extension scope" trap from that guide were avoided by keeping the build inline.
- **Expression-body normalisation.** `greet` is `fun greet(...) = "Hello, $name"` which is `IrExpressionBody`. The guide's recipe — wrap the single expression in `irReturn` before re-emitting into `irBlockBody` — worked.
- **Argument formatting via `irConcat()`.** Built `-> name(arg1, arg2, ...)` as `IrStringConcatenation`. Kotlin auto-calls `.toString()` on each interpolated value at runtime, which is the desired behaviour (Int → `"3"`, String → `"Alice"`).
- **Filter inline / operator at IR time too**, even though the FIR checker rejects them: the compiler still proceeds to IR for other files when one file has a diagnostic-only error, and the safety belt prevents accidental IR-level wrapping of an `inline fun` (which would not work correctly anyway).
- **No `FirPredicateBasedProvider` needed.** Two checkers, two `hasAnnotation` calls — fast enough and far simpler than registering predicates. The predicate system pays off when you're filtering broad searches; for two checkers with one ClassId each, direct annotation lookup is fine.

## Skill-doc gaps / friction noted

- The `compiler-plugin-bootstrap/guide.md` example `MyComponentRegistrar` snippet pulls `MESSAGE_COLLECTOR_KEY` and instantiates the IR extension with it, but the matching `MyIrGenerationExtension(messageCollector)` constructor is only shown later in the same guide. A first-pass reader can be tripped up. Suggestion: lift the message-collector wiring into a "the wired-up registrar end-to-end" snippet that includes both halves.
- `fir-additional-checkers-extension/guide.md` is excellent on `error0<KtClass>(...)`, `KtDiagnosticFactoryToRendererMap`, and `registerDiagnosticContainers(...)`. The `INLINE_FUN_MODIFIER` and `OPERATOR` positioning strategies are in the table — that was the only doc lookup needed for this task's diagnostics.
- `ir-body-modification/guide.md` has both the try/finally wrapping recipe AND the expression-body normalisation recipe — these were the two most important bits and both were spot-on. The "helper extensions on `IrBlockBodyBuilder` don't work inside an inner `irBlock`" trap was relevant when I considered factoring out a helper for the entry-string build; I just inlined it instead.
- `fir-predicate-system/guide.md` was read but ultimately not used (see decision above). No friction; the trade-off was straightforward.
- One minor harmless compiler warning (`No cast needed.`) from my first attempt at `processed.parent as? IrDeclarationParent` — `processed.parent` is already `IrDeclarationParent`. Self-resolved.

No invented APIs, no `getPluginArtifactForNative`, no stale `valueParameters` references, no `Visibilities.Public` trap. The "anti-cheat" failure modes listed in `evaluation/README.md` were all avoided.

## Verification log

Verification commands actually run, in order:

1. `:plugin:jar` (criterion 1) — BUILD SUCCESSFUL.
2. `:sample:run --rerun-tasks` (criteria 2-9) — output matches every grep in SPEC's verification procedure.
3. Add `@Trace inline fun bad() {}` to sample, run `:sample:compileKotlin` — fails with `e: ... @Trace cannot be applied to inline functions` (criterion 10).
4. Replace with `@Trace operator fun Int.foo() {}`, run `:sample:compileKotlin` — fails with `e: ... @Trace cannot be applied to operator functions` (criterion 11). A second pre-existing Kotlin diagnostic about `foo` not being a valid operator name also fires, which is independent of this plugin.
5. Add `@Trace fun thrower() { error("oops") }` plus a `try { thrower() } catch ...` in `main`, re-run `:sample:run` — output shows `-> thrower()` / `<- thrower` / `caught: oops` in that order (criterion 12).
6. Restore `Main.kt` to spec-exact form, re-run `:sample:run` — output matches spec exactly (criteria 1-9 final pass).

`/tmp/03-out.txt` captures the final pristine run.

## Conclusion

The consolidated `kotlin-compiler-plugin` skill (single SKILL.md router + per-topic `references/<topic>/guide.md`) was sufficient to implement this high-complexity task on the first build attempt with no failed compile cycles for the plugin module itself. The guides I read in order: `compiler-plugin-bootstrap`, `fir-extensions-overview`, `fir-additional-checkers-extension`, `ir-plugincontext-usage`, `ir-body-modification`. Each had the right level of detail at the right place. The router itself is fast to navigate — the "Topic index" table answered every routing question I had.

Skill consolidation introduced no observable regressions vs. what the SPEC expects an evaluator to do.
