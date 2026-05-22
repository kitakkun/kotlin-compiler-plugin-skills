# Evaluation Result: 03-high-trace-plugin — 2026-04-30

**Skills version**: HEAD of `main` at evaluation time
**Kotlin version validated against**: 2.3.20

## Final Score: 100 / 100

| Category | Score | Max |
|---|---|---|
| Functionality | 60 | 60 |
| Code Quality | 20 | 20 |
| Skill Adherence | 20 | 20 |

## Functionality breakdown

12/12 mandatory criteria pass. Criterion 13 is optional and was not implemented (no deduction).

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Plugin builds (`./gradlew :plugin:jar`) | PASS | `BUILD SUCCESSFUL in 554ms`; `plugin/build/libs/plugin.jar` produced |
| 2 | Sample compiles | PASS | `:sample:compileKotlin` returns BUILD SUCCESSFUL on the unmodified `Main.kt` |
| 3 | Sample runs without crashing | PASS | `:sample:run` exits 0 |
| 4 | `greet` traced on entry | PASS | line 1 of `/tmp/03-out.txt`: `-> greet(Alice)` |
| 5 | `greet` traced on exit | PASS | line 2: `<- greet` |
| 6 | `add` traced on entry/exit | PASS | lines 4–5: `-> add(3, 4)` / `<- add` |
| 7 | `multiply` traced on entry/exit | PASS | lines 7–8: `-> multiply(2, 5)` / `<- multiply` |
| 8 | `helper` is NOT traced | PASS | `grep -F -- "-> helper(" /tmp/03-out.txt` produced no output |
| 9 | Original behaviour preserved | PASS | `Hello, Alice` (line 3), `7` (line 6), `10` (line 9) all present |
| 10 | `@Trace inline fun` errors | PASS | adding `@Trace inline fun bad() {}` produced `e: …InlineBad.kt:3:8 @Trace cannot be applied to inline functions`. Column 8 corresponds to the `inline` modifier — matches `SourceElementPositioningStrategies.INLINE_FUN_MODIFIER` declared at TraceDiagnostics.kt:11. |
| 11 | `@Trace operator fun` errors | PASS | adding `@Trace operator fun Int.foo() {}` produced `e: …OperatorBad.kt:3:25 @Trace cannot be applied to operator functions`. Column 25 corresponds to `foo` — matches `NAME_IDENTIFIER` at TraceDiagnostics.kt:12. |
| 12 | Trace order correct on exception | PASS | injecting `@Trace fun thrower() { error("oops") }` and calling it inside `try { … } catch` from `main` produced output `-> thrower()`, `<- thrower`, then `caught: oops` in that order — proves try/finally wrapping is exception-safe |
| 13 *(optional)* | Plugin tests | N/A | no `plugin/src/test` directory; SPEC marks as bonus only |

Functionality score: 12 / 12 × 60 = **60**.

## Code Quality breakdown

| Sub-axis | Score | Notes |
|---|---|---|
| File organization | 5 | Clean `plugin/` vs `sample/` separation. Subpackages `com.example.trace.fir` and `com.example.trace.ir` mirror the bootstrap convention. One concept per file: `TraceComponentRegistrar`, `TraceCommandLineProcessor`, `TraceNames`, `fir/TraceCheckersExtension`, `fir/TraceDiagnostics`, `fir/TraceFirExtensionRegistrar`, `ir/TraceIrGenerationExtension`. Both `META-INF/services/` files present and correctly named. |
| Idiomatic Kotlin | 5 | `object` for singletons (`TraceNames`, `TraceDiagnostics`, `TraceDefaultErrorMessages`, `TraceDeclarationCheckers`, `TraceFunctionChecker`); `class` only for the per-session `TraceCheckersExtension`. Uses delegated property `MAP by KtDiagnosticFactoryToRendererMap("Trace") { … }`. Compact early-return guards in `shouldTrace`. No Java-style getters, no needless nullability. |
| Readability | 5 | Names descriptive (`shouldTrace`, `buildEntryMessage`, `printlnSymbol`, `originalStatements`). KDoc on `buildEntryMessage` explains why `IrStringConcatenation` was chosen. Inline comments explain WHY at every non-obvious site (body normalisation at line 60–62, visibility filter at 116, fake-override skip at 118, abstract/external skip at 120). No `tmp`/`xx`/`data1`. No commented-out code. |
| No anti-patterns | 5 | No `Thread.sleep`, no empty catches, no `@Suppress("ALL")`, no copy-paste. The two `@OptIn` uses are the necessary `@file:OptIn(UnsafeDuringIrConstructionAPI::class)` (justified at the top of the IR file because `function.parameters` and similar are unsafe-during-construction in 2.3.x) and the standard `@OptIn(ExperimentalCompilerApi::class)` on the registrar / CLI processor. |

Code quality score: **20 / 20**.

## Skill Adherence breakdown

| Sub-axis | Score | Notes |
|---|---|---|
| Recommended patterns | 5 | `TraceNames.PLUGIN_ID` shared between `TraceComponentRegistrar.kt:13` and `TraceCommandLineProcessor.kt:9`. `compileOnly("...kotlin-compiler-embeddable:2.3.20")` at `plugin/build.gradle.kts:12`. `@OptIn(ExperimentalCompilerApi::class)` on registrar and CLI processor. `supportsK2 = true` at TraceComponentRegistrar.kt:14. |
| Modern APIs | 5 | `pluginContext.finderForBuiltins().findFunctions(CallableId(...))` (TraceIrGenerationExtension.kt:39–45). `IrElementTransformerVoidWithContext.visitFunctionNew` (line 47–48). FIR checker uses `reporter.reportOn(...)` from `org.jetbrains.kotlin.diagnostics`. IR `println` call uses `arguments[0] = ...` setter (lines 72, 82). |
| No invented/deprecated APIs | 5 | No `getPluginArtifactForNative`, no `referenceClass` / `referenceFunctions`, no `createParameterDeclarations`, no `registerClassAsMetadataVisible`. `KtDiagnosticFactoryToRendererMap` uses the `by` delegate form (TraceDiagnostics.kt:18), not the deprecated direct constructor. No `dispatchReceiver = ...` setter anywhere. |
| Correct API forms | 5 | Checker uses Kotlin 2.3 context-parameter form: `context(context: CheckerContext, reporter: DiagnosticReporter) override fun check(declaration: FirNamedFunction)` (TraceCheckersExtension.kt:26–27). `FirNamedFunction` is the post-2.3.20 rename. Diagnostic container registered via `registerDiagnosticContainers(TraceDiagnostics)` inside `FirExtensionRegistrar.configurePlugin()` (TraceFirExtensionRegistrar.kt:8). `-Xcontext-parameters` flag is on the **plugin** module's `freeCompilerArgs` (plugin/build.gradle.kts:16). `MppCheckerKind.Common` correctly chosen. |

Skill adherence score: **20 / 20**.

## Anti-cheat findings

Cross-checked all nine items in the SPEC's "Common failure modes". **None triggered.**

1. `@Trace inline fun` silently allowed → NO. Diagnostic fires (criterion 10).
2. `<- ` line missing on exception → NO. `<- thrower` printed before the exception propagates (criterion 12). Implementation uses `irTry(..., finallyExpression = irCall(printlnSymbol)…)` at TraceIrGenerationExtension.kt:75–84.
3. Private `helper` traced → NO. `function.visibility.delegate != Visibilities.Public` filter at TraceIrGenerationExtension.kt:117 excludes it. Verified empirically.
4. Class-level `@Trace` doesn't propagate → NO. `parent is IrClass && parent.hasAnnotation(TRACE_FQN)` at TraceIrGenerationExtension.kt:127–129 covers both `add` and `multiply`.
5. Double-traced members → NO. Single transformer with OR semantics in `shouldTrace`; functions are wrapped exactly once even when both class and function carry `@Trace`.
6. Wrong arg formatting → NO. `irConcat()` + `addArgument(irGet(param))` at lines 98–111 — each non-string arg is rendered through implicit `toString()` at runtime. Output verified as `-> add(3, 4)`.
7. **Body modification mutates original IR in place** → NO. Implementation builds a fresh body via `processed.body = builder.irBlockBody { … +irTry(...) }` (TraceIrGenerationExtension.kt:69–85). This is the documented `irBlockBody` pattern from the `ir-body-modification` skill — explicitly NOT the `body.statements.clear() + add(...)` anti-pattern.
8. `patchDeclarationParents` not called → NO. Called at TraceIrGenerationExtension.kt:86.
9. kctfork version issue → N/A (no plugin tests).

Beyond the SPEC, the implementation also defends against three pitfalls not in the failure-mode list:

- **`IrConstructor` exclusion** (line 114) — `IrFunction` includes constructors, and the class-level `@Trace` propagation would otherwise wrap `Calculator`'s constructor.
- **`isFakeOverride` filter** (line 119) — every class inherits fake `equals`/`hashCode`/`toString` from `Any`; without this filter, class-level `@Trace` propagation would wrap them too.
- **`IrExpressionBody` normalisation** (lines 63–67) — `fun greet(name: String): String = "Hello, $name"` has an `IrExpressionBody`, not an `IrBlockBody`. Naively skipping it (as the skill's example does with `body as? IrBlockBody`) would mean criterion 4 / 5 fail. The implementer correctly converts the expression to `irReturn(body.expression)` and wraps that.

## Overall assessment

A clean, complete, well-organised solution that passes all 12 mandatory acceptance criteria on first run with no warnings. Code is idiomatic Kotlin 2.3 with the modern post-2.2 IR APIs. The implementer correctly identified and guarded against several IR-level pitfalls that the SPEC anti-cheat list does not call out explicitly — an indication of careful work rather than over-engineering.

## Suggested skill fixes

The implementer flagged five potential skill gaps. Verified independently against the relevant skills:

1. **`kotlin.compiler.execution.strategy=in-process` not prominent enough** — *partially valid*. It IS in `compiler-plugin-bootstrap/SKILL.md` (line 405 in a debug-snippet, line 432 in a "daemon caching" gotcha), but only as a debugging knob. For day-to-day plugin development it is the recommended setting (avoids stale daemon classloader pinning the previous plugin JAR). Surface earlier in the bootstrap skill as a recommended dev-loop default, not just a debug fix.

2. **`IrExpressionBody` handling missing in `ir-body-modification`** — *valid*. SKILL.md lists the three `IrBody` subtypes (lines 14–17) but every wrapping example uses `body as? IrBlockBody` and bails out otherwise. There is no example showing how to wrap an expression-body function in try/finally (convert the expression to a return statement first). The implementer hit this case (`fun greet(...) = "Hello, $name"`) and got it right by inspection, but a fresh agent following the skill's example literally would silently fail to trace any expression-body function.

3. **`function.visibility.delegate` indirection not shown** — *valid*. Searching the skill set, `Visibilities` and `DescriptorVisibilities` only appear in `ir-synthetic-class-generation` in *creation* contexts. There is no documented pattern for *filtering* an existing `IrFunction` by visibility. The simpler form `function.visibility != DescriptorVisibilities.PUBLIC` would also work; the implementer's `.delegate != Visibilities.Public` form is one valid path. Either way, an example belongs in `ir-body-modification`'s "filtering" guidance.

4. **`IrConstructor` exclusion not documented** — *valid but narrow*. The skills do not warn that `IrFunction` is the supertype of both `IrSimpleFunction` and `IrConstructor`, and that `visitFunctionNew` will visit constructors too. For a "wrap every annotated function" plugin this is a real foot-gun. A one-line gotcha in `ir-body-modification` would help.

5. **`isFakeOverride` filter for synthesised members** — *valid*. The skill mentions `IrSyntheticBody` for *data class* members in passing (line 222) but does not mention the more common case: every class inherits fake overrides of `equals`/`hashCode`/`toString` from `Any`, and propagation rules that match "every member of an annotated class" will hit them unless filtered. Worth adding to the gotchas section.

All five claims are legitimate skill gaps rather than implementer over-elaboration. None are show-stoppers — the patterns are inferable — but each is a concrete pitfall a fresh agent could fall into.
