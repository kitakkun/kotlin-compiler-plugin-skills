# Skill Accuracy Benchmark

Six compiler plugin implementation tasks of increasing complexity, designed to measure how well a fresh Claude agent can produce a working plugin **using only this repo's skills as documentation**.

## Why this exists

The skills in `skills/` document the Kotlin compiler plugin API. They're only useful if a Claude session can read them and produce a working plugin. This evaluation suite gives a repeatable, scorable harness for measuring that:

- **Pass** = the plugin builds, runs, and meets every acceptance criterion in `SPEC.md`.
- **Fail** = anything breaks.

Run it after major skill updates, after Kotlin minor releases, or whenever you suspect the docs have drifted.

## How to run an evaluation

The recommended path uses the sandbox runner, which copies only the target `SPEC.md` into a tempdir so the agent has no spatial access to other tasks' `RESULT.md`, the `verification/` reference implementations, or the `examples/` reference layout:

```bash
scripts/run-evaluation.sh 03-high-trace-plugin
# Sandbox created at: /tmp/kotlin-skill-bench-03-high-trace-plugin-<timestamp>
```

Then:

1. `cd` into the printed sandbox path.
2. **Open a fresh Claude Code session** with the kotlin-compiler-plugin-skills plugin installed. Restrict its permissions to the sandbox directory if your environment supports it.
3. Tell the agent: `Implement what is described in SPEC.md.`
4. **Run the verification commands** in `SPEC.md` yourself (don't trust the agent's self-report).
5. **Record the result** using the rubric below into the *original* `evaluation/<task>/RESULT.md`, then delete the tempdir.

The runner provides spatial isolation; it does not block an agent that resolves absolute paths. For a stricter trust boundary use Claude Code permissions to limit reads to the sandbox.

## Scoring rubric

For each task, mark each acceptance criterion as:

- **PASS** ✅ — verified working
- **FAIL** ❌ — verifiably broken
- **SKIP** ⚠️ — agent didn't reach this far

Per-task score = (# PASS) / (# acceptance criteria).
Overall score = average across tasks (small / medium / high weighted equally).

Healthy thresholds:
- **≥ 95%**: skills are accurate and complete; OSS-ready.
- **80–95%**: minor doc gaps; usable, mark known issues.
- **< 80%**: significant doc drift; investigate which skills failed.

## What each evaluation stresses

| Task | Skills primarily exercised | Approx. lines of plugin code | Approx. agent rounds expected |
|---|---|---|---|
| **01-small** `@FinalOnly` checker | `compiler-plugin-bootstrap`, `fir-extensions-overview`, `fir-additional-checkers-extension`, `fir-predicate-system` | ~50–100 | 1 |
| **02-medium** `@AutoStringify` synthesis | adds `fir-declaration-generation-extension`, `ir-plugincontext-usage`, `ir-body-modification` | ~200–300 | 2–3 |
| **03-high** `@Trace` plugin | adds `ir-call-rewriting`, `ir-synthetic-class-generation` (optional), `compiler-plugin-testing` | ~400–500 | 3–5 |
| **04-veryhigh** `@Positive` / `@Negative` Int | adds `fir-type-attribute-extension`, `fir-session-components`, `ConeAttribute` round-trip in metadata | ~300–500 | 3–5 |
| **05-multiversion** `@Final` per-version JARs | adds `multi-version-kotlin-support`, exercises CHANGES.md for `fir-additional-checkers-extension` | ~200–400 across versioned modules | 3–5 |
| **06-extreme** `@JsonSerialize` ecosystem | combines `fir-declaration-generation-extension`, `ir-body-modification`, `ir-synthetic-class-generation`, IR-only metadata-visible members across modules | ~700–1000 | 5–8 |

## Anti-cheat: known failure modes

When evaluating, watch for:

- **Plugin appears to build but extension isn't wired** — `META-INF/services` missing or wrong FQN. Sample compile shows no plugin output.
- **`Class is not abstract and does not implement abstract member 'pluginId'`** — agent forgot the Kotlin 2.3 requirement.
- **Checker `check(declaration, context, reporter)` doesn't override** — agent used pre-2.3 value-parameter form instead of `context(...)`.
- **`KtDiagnosticFactoryToRendererMap("Name")` constructor not callable** — agent used the wrong package or the direct constructor instead of the `by` delegate factory.
- **`-Xcontext-parameters` flag missing** — checker compilation fails.
- **`getPluginArtifactForNative()` referenced** — invented API; the agent used stale knowledge.
- **`createParameterDeclarations()` referenced** — deprecated since 2.1.20; should use `createThisReceiverParameter()`.
- **`registerClassAsMetadataVisible` referenced** — invented API.

If you see any of these, the relevant skill probably needs another correction round.

## Reporting template

Use `evaluation/<task>/RESULT.md` to record an evaluation:

```markdown
# Evaluation Result: <task> — <date>

**Agent**: <model identifier>
**Skills version**: HEAD of `main` at evaluation time
**Kotlin version**: <e.g. 2.3.20>

## Acceptance Criteria

| # | Criterion | Result | Notes |
|---|---|---|---|
| 1 | ... | ✅ / ❌ / ⚠️ | ... |

## Score: X / Y

## Failure analysis (if any)

- Skill `<name>` was missing/incorrect on `<topic>`. Suggested fix: ...
```

## Task index

1. **[01-small](01-small-final-only-checker/SPEC.md)** — `@FinalOnly` checker (single FIR diagnostic)
2. **[02-medium](02-medium-auto-stringify/SPEC.md)** — `@AutoStringify` synthesis (FIR generation + IR fill)
3. **[03-high](03-high-trace-plugin/SPEC.md)** — `@Trace` plugin (body wrapping + diagnostics + tests)
4. **[04-veryhigh](04-veryhigh-positive-negative-types/SPEC.md)** — `@Positive` / `@Negative` Int sign refinement (`FirTypeAttributeExtension` + `ConeAttribute` round-trip + `FirFunctionCallChecker`)
5. **[05-multiversion](05-multiversion-final-checker/SPEC.md)** — `@Final` checker with per-version JARs for Kotlin 2.2.20 and 2.3.20 (`multi-version-kotlin-support` + `fir-additional-checkers-extension` CHANGES.md)
6. **[06-extreme](06-extreme-json-serialize/SPEC.md)** — `@JsonSerialize` mini-serializer ecosystem: 5 interacting annotations, FIR declaration generation + IR body fill + IR-only metadata-visible `parse()`, multi-module verification (combines `fir-declaration-generation-extension`, `ir-body-modification`, `ir-synthetic-class-generation`, `ir-plugincontext-usage`'s metadata registrar)
