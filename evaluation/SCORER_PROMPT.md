# Scorer Agent Prompt

Reusable prompt template for evaluating a evaluation implementation. Substitute `<TASK>` with `01-small-final-only-checker`, `02-medium-auto-stringify`, or `03-high-trace-plugin`.

## Usage

When spawning the scorer agent, pass this prompt with the substituted task:

```
You are scoring a Kotlin compiler plugin implementation against an evaluation spec.

**Specification**: /Users/kitakkun/Documents/GitHub/kotlin-compiler-plugin-skills/evaluation/<TASK>/SPEC.md
**Implementation**: /Users/kitakkun/Documents/GitHub/kotlin-compiler-plugin-skills/evaluation/<TASK>/work/

Read both, then score on a 100-point scale across three categories.

## Functionality (60 points)

1. Read every acceptance criterion in SPEC.md.
2. Run the exact verification commands the SPEC specifies — do not skip any. Use the `Bash` tool.
3. For each criterion: PASS = full credit, FAIL = 0, no partial credit.
4. Score = (passed_count / total_count) × 60.

If the project does not even build, all functionality criteria after the build step fail.

## Code Quality (20 points)

Evaluate by reading the implementation files. Each sub-axis is 0–5:

| Sub-axis | Looking for |
|---|---|
| File organization (5) | Logical module/package layout, sensible filenames, plugin/sample separation matches the bootstrap convention |
| Idiomatic Kotlin (5) | Uses `object` for singletons, `fun interface` where appropriate, no Java-style getters/setters, no unnecessary nullability |
| Readability (5) | Clear naming (no `tmp`, `xx`, `data1`); reasonable comments where the WHY isn't obvious; no commented-out blocks |
| No anti-patterns (5) | No `Thread.sleep`, no `try { ... } catch (e: Exception) {}` empty catches, no `@Suppress("ALL")`, no copy-pasted long blocks across files |

## Skill Adherence (20 points)

Evaluate against the skills referenced in the SPEC's "Skills exercised" table. Each sub-axis is 0–5:

| Sub-axis | Looking for |
|---|---|
| Recommended patterns (5) | `MyPluginNames.PLUGIN_ID` shared constant; `compileOnly` for `kotlin-compiler-embeddable`; `@OptIn(ExperimentalCompilerApi::class)` at file or class level; `supportsK2 = true` |
| Modern APIs only (5) | Uses `pluginContext.finderForBuiltins()`/`finderForSource(file)` rather than deprecated `referenceClass`/`referenceFunctions`; uses `IrElementTransformerVoidWithContext` rather than plain `IrElementTransformerVoid` when scope is needed; uses `pluginContext.diagnosticReporter` rather than `messageCollector` for IR diagnostics |
| No invented or deprecated APIs (5) | No `getPluginArtifactForNative()`, no `createParameterDeclarations()` (use `createThisReceiverParameter()`), no `registerClassAsMetadataVisible`, no `KtDiagnosticFactoryToRendererMap(...)` direct constructor (use `by` delegate), no `dispatchReceiver = ...` setter (use `arguments[0] = ...`) |
| Correct API forms (5) | Checker `check(...)` uses context parameters: `context(context: CheckerContext, reporter: DiagnosticReporter) override fun check(declaration: D)`; predicate registration is on `FirExtension.registerPredicates()` not on the registrar; `-Xcontext-parameters` flag is in plugin module's `freeCompilerArgs` |

## Output Format

Write your evaluation to `/Users/kitakkun/Documents/GitHub/kotlin-compiler-plugin-skills/evaluation/<TASK>/RESULT.md` using this template:

```markdown
# Evaluation Result: <TASK> — <date YYYY-MM-DD>

**Skills version**: <output of `git -C /Users/kitakkun/Documents/GitHub/kotlin-compiler-plugin-skills rev-parse HEAD` if available, else "uncommitted">
**Kotlin version validated against**: 2.3.20

## Final Score: X / 100

| Category | Score | Max |
|---|---|---|
| Functionality | A | 60 |
| Code Quality | B | 20 |
| Skill Adherence | C | 20 |

## Functionality breakdown

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | <quote from SPEC> | ✅ PASS / ❌ FAIL | <command output snippet or grep result> |
...

## Code Quality breakdown

| Sub-axis | Score | Notes |
|---|---|---|
| File organization | 0–5 | ... |
| Idiomatic Kotlin | 0–5 | ... |
| Readability | 0–5 | ... |
| No anti-patterns | 0–5 | ... |

## Skill Adherence breakdown

| Sub-axis | Score | Notes |
|---|---|---|
| Recommended patterns | 0–5 | ... |
| Modern APIs | 0–5 | ... |
| No invented/deprecated APIs | 0–5 | ... |
| Correct API forms | 0–5 | ... |

## Anti-cheat findings

(List any of the failure modes from the SPEC that triggered, with file:line if applicable.)

## Overall assessment

(1–2 sentences summarising the implementation's quality and what to focus on next.)

## Suggested skill fixes

(If a Functionality or Skill Adherence failure points at a documentation gap rather than agent error, list it here.)
```

Then return a brief summary in your response (under 100 words).

## Important rules for the scorer

- **Do not trust the implementer's self-report**. Run the verification commands yourself.
- **Do not modify the implementation** to make it pass. You're a judge, not a fixer.
- **Be specific about failures**: cite file:line, command output, or exact error.
- **Distinguish agent error from skill-doc error**: if a failure traces to "the skill should have said X", note it under "Suggested skill fixes".
- **Your bias check**: re-read the SPEC's "Common failure modes" before finalising. If the implementation triggered any, flag them.
