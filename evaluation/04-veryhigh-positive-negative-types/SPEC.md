# Benchmark 04 — `@Positive` / `@Negative` Number-Sign Type Attribute (Very High)

A compiler plugin that introduces *type-level refinement attributes*: `@Positive Int` and `@Negative Int` are distinct from `Int` for the purpose of compatibility checking, and the compiler reports an error when a value of the wrong sign is passed where a refined type is expected. Tests `FirTypeAttributeExtension` (the round-trip between annotations and `ConeAttribute`), the mandatory `ConeAttributes.attributeAccessor<T>()` pattern, and a `FirFunctionCallChecker` that enforces the attribute at call sites.

This evaluation deliberately exercises a corner of the compiler plugin API that is **not covered by the standard plugins** (allopen, parcelize, kotlinx-serialization, lombok) — type attributes are a specialised tool for refinement-style typing. The closest reference is plugin-sandbox's `FirNumberSignAttributeExtension`, but the agent must not consult it directly (see "Forbidden references" below).

## Specification

Provide a plugin that defines two annotations and a checker:

```kotlin
// User-facing API:
package com.example.signs
annotation class Positive
annotation class Negative

// User-side usage (must compile):
fun takePositive(x: @Positive Int) {}
fun takeNegative(x: @Negative Int) {}
fun takeAny(x: Int) {}
fun makePositive(): @Positive Int = 5
fun makeNegative(): @Negative Int = -7

fun ok() {
    val p: @Positive Int = makePositive()
    val n: @Negative Int = makeNegative()
    takePositive(p)
    takeNegative(n)
    takeAny(p)            // un-annotated parameter accepts anything
    takeAny(n)
    takeAny(42)
}

// User-side usage (must produce ILLEGAL_NUMBER_SIGN error):
fun bad() {
    takePositive(makeNegative())   // ← error here
    takeNegative(makePositive())   // ← error here
    val nope: @Positive Int = makeNegative()   // ← error here
}
```

### Behaviour

1. The plugin recognises `com.example.signs.Positive` and `com.example.signs.Negative` annotations on **type usages** (e.g. on the `Int` of a parameter type, return type, or local-variable type).
2. The compiler attaches a corresponding `ConeAttribute` (the plugin's `ConeNumberSignAttribute(Sign.Positive | Sign.Negative)`) to the type at FIR level.
3. The attribute survives metadata serialization — i.e. `convertAttributeToAnnotation` is implemented, so a downstream module reading the compiled signature still sees `@Positive` / `@Negative`.
4. A custom `FirFunctionCallChecker` examines argument-vs-parameter attribute compatibility for every call. If a call passes a value whose type carries `@Negative` to a parameter typed `@Positive`, or vice versa, the checker reports `ILLEGAL_NUMBER_SIGN` with a message like `expected @Positive, got @Negative`.
5. The checker uses positioning strategy `DEFAULT` (whole expression) on the offending argument source.
6. The accessor `val ConeAttributes.numberSign: ConeNumberSignAttribute?` is declared at top level (mandatory for the FIR runtime to fish the attribute out of the type's attribute registry).

### Diagnostics

- **`ILLEGAL_NUMBER_SIGN`** — error. Message: `"@Positive expected, but @Negative was passed"` (or symmetric) when the actual attribute differs from the expected one. Squiggle on the argument expression.

The diagnostic does *not* fire when:
- The parameter has no number-sign attribute (`takeAny(positive)` is fine).
- Both argument and parameter carry the same sign.
- The parameter has an attribute but the argument has no attribute *and* the value is a literal of the right sign — note: this evaluation does NOT require literal inference (numeric literal `5` does not automatically receive `@Positive`); only explicitly annotated types carry the attribute. Don't try to infer signs from constant values; that's an inference layer the evaluation doesn't require.

### Project layout

```
evaluation/04-veryhigh-positive-negative-types/work/
├── settings.gradle.kts, gradle.properties, build.gradle.kts
├── gradle/, gradlew                ← copy from skills/kotlin-compiler-plugin/references/compiler-plugin-bootstrap/example/
├── plugin/
│   └── src/main/kotlin/com/example/signs/
│       ├── SignsPluginNames.kt
│       ├── SignsComponentRegistrar.kt
│       ├── SignsCommandLineProcessor.kt
│       └── fir/
│           ├── ConeNumberSignAttribute.kt              ← the ConeAttribute<...> + the accessor
│           ├── NumberSignAttributeExtension.kt          ← FirTypeAttributeExtension
│           ├── SignedNumberCallChecker.kt               ← FirFunctionCallChecker
│           ├── SignsAdditionalCheckers.kt               ← FirAdditionalCheckersExtension wrapper
│           ├── SignsDiagnostics.kt                      ← KtDiagnosticsContainer with ILLEGAL_NUMBER_SIGN
│           └── SignsFirExtensionRegistrar.kt
└── sample/
    └── src/main/kotlin/com/example/app/Main.kt
```

`sample/Main.kt` should:

```kotlin
package com.example.app

import com.example.signs.Positive
import com.example.signs.Negative

fun takePositive(x: @Positive Int): Int = x
fun takeNegative(x: @Negative Int): Int = x
fun takeAny(x: Int): Int = x

fun makePositive(): @Positive Int = 5
fun makeNegative(): @Negative Int = -7

fun main() {
    val p: @Positive Int = makePositive()
    val n: @Negative Int = makeNegative()
    println(takePositive(p))
    println(takeNegative(n))
    println(takeAny(p))
    println(takeAny(n))
    println(takeAny(42))
}
```

This file must compile cleanly with the plugin loaded. A separate "negative-test" snippet (placed in a temp file by the verifier, **not** in `sample/`) exercises the error case:

```kotlin
package com.example.app
import com.example.signs.Positive
import com.example.signs.Negative

fun makePositive(): @Positive Int = 5
fun makeNegative(): @Negative Int = -7
fun takePositive(x: @Positive Int): Int = x

fun bad() {
    takePositive(makeNegative())   // expect: ILLEGAL_NUMBER_SIGN
}
```

## Acceptance criteria

| # | Criterion | How to verify |
|---|---|---|
| 1 | Plugin builds | `./gradlew :plugin:jar` |
| 2 | `ConeNumberSignAttribute` extends `ConeAttribute<ConeNumberSignAttribute>` and overrides `union`, `intersect`, `add`, `isSubtypeOf`, `key`, `keepInInferredDeclarationType` | inspect `ConeNumberSignAttribute.kt`; all 6 members present |
| 3 | The accessor `val ConeAttributes.numberSign: ConeNumberSignAttribute? by ConeAttributes.attributeAccessor<ConeNumberSignAttribute>()` is declared at top level | grep `attributeAccessor` in plugin sources |
| 4 | The `FirTypeAttributeExtension` returns `null` from `convertAttributeToAnnotation` for attributes that aren't `ConeNumberSignAttribute` | inspect the extension; presence of `if (attribute !is ConeNumberSignAttribute) return null` (or equivalent guard) |
| 5 | Sample compiles | `./gradlew :sample:compileKotlin` |
| 6 | Sample runs without crashing | `./gradlew :sample:run` exits 0 |
| 7 | Sample output is correct | output contains `5`, `-7`, `5`, `-7`, `42` (one per line, in order) |
| 8 | Negative test produces `ILLEGAL_NUMBER_SIGN` | a temp file with `takePositive(makeNegative())` compiled against the plugin produces a diagnostic identifying `ILLEGAL_NUMBER_SIGN` (factory name visible because of `-Xrender-internal-diagnostic-names`) |
| 9 | Symmetric negative test produces `ILLEGAL_NUMBER_SIGN` | a temp file with `takeNegative(makePositive())` produces the same diagnostic |
| 10 | Mismatched local-var assignment produces `ILLEGAL_NUMBER_SIGN` | a temp file with `val x: @Positive Int = makeNegative()` produces the diagnostic |
| 11 | `takeAny(makePositive())` does NOT trigger the diagnostic | un-annotated parameter accepts any sign |
| 12 | Same-sign call does NOT trigger the diagnostic | `takePositive(makePositive())` is clean |
| 13 *(optional)* | Diagnostic survives metadata round-trip | a small two-module project where module B imports module A's `@Positive`-annotated function still sees the attribute (verifiable by introducing a violation in module B and observing the same diagnostic) |

Criteria 1–12 are mandatory; 13 is bonus.

## Verification procedure

```bash
cd evaluation/04-veryhigh-positive-negative-types/work

# Build + run
./gradlew :sample:run --rerun-tasks --console=plain 2>&1 | tee /tmp/04-out.txt

# Output checks
grep -q '^5$' /tmp/04-out.txt
grep -q '^-7$' /tmp/04-out.txt
grep -q '^42$' /tmp/04-out.txt

# Plugin-shape checks
grep -F 'ConeAttribute<ConeNumberSignAttribute>' plugin/src/main/kotlin/com/example/signs/fir/ConeNumberSignAttribute.kt
grep -F 'attributeAccessor<ConeNumberSignAttribute>' plugin/src/main/kotlin/com/example/signs/fir/ConeNumberSignAttribute.kt
grep -E 'override (fun|val)' plugin/src/main/kotlin/com/example/signs/fir/ConeNumberSignAttribute.kt | grep -E 'union|intersect|add|isSubtypeOf|key|keepInInferredDeclarationType'

# Diagnostic checks (build separate sources)
# 1. takePositive(makeNegative()) → ILLEGAL_NUMBER_SIGN
# 2. takeNegative(makePositive()) → ILLEGAL_NUMBER_SIGN
# 3. val x: @Positive Int = makeNegative() → ILLEGAL_NUMBER_SIGN
# (Compile each fragment via the sample's gradle config; the consumer module must have
#  -Xrender-internal-diagnostic-names so the factory name appears in build output.)

# Positive control: takeAny(...) and same-sign calls should NOT emit ILLEGAL_NUMBER_SIGN.
```

## Skills exercised

| Skill | Used for |
|---|---|
| `compiler-plugin-bootstrap` | project layout, `CompilerPluginRegistrar`, META-INF/services |
| `fir-extensions-overview` | extension wiring; understanding session lifecycle |
| `fir-type-attribute-extension` | the core: `extractAttributeFromAnnotation` / `convertAttributeToAnnotation`, `ConeAttribute` contract, `attributeAccessor<T>()` |
| `fir-additional-checkers-extension` | `SignedNumberCallChecker` (a `FirFunctionCallChecker`), diagnostic factory, `KtDiagnosticsContainer`, `-Xcontext-parameters`, `-Xrender-internal-diagnostic-names` |
| `compiler-plugin-debugging` | `MessageCollector`, IR phase dumps if metadata round-trip fails |
| `compiler-plugin-testing` | (optional, for criterion 13) two-module verification |

## Forbidden reference material

- **`/Users/kitakkun/Documents/GitHub/kotlin-lang/plugins/plugin-sandbox/src/.../FirNumberSignAttributeExtension.kt`** and its companions (`ConeNumberSignAttribute.kt`, `SignedNumberCallChecker.kt`) — these are the exact reference impl that this evaluation is designed to exercise. The agent must produce equivalent code from the SKILL.md and EVIDENCE.md alone, not by transliterating.
- All `verification/` subdirectories — they contain working reference implementations; off-limits per evaluation policy even though most are not directly applicable here.

`skills/kotlin-compiler-plugin/references/compiler-plugin-bootstrap/example/` is allowed for the project structure boilerplate (Gradle wrapper, settings, registrar shape).

## Common failure modes (anti-cheat)

1. **`ConeNumberSignAttribute` missing required `ConeAttribute<T>` overrides** — `union`/`intersect`/`add`/`isSubtypeOf`/`key`/`keepInInferredDeclarationType` are all abstract; forgetting any one prevents compilation. (Common omission: `keepInInferredDeclarationType` because it's a `val` not `fun`.)
2. **Forgetting the `attributeAccessor` declaration** — without `val ConeAttributes.numberSign: ConeNumberSignAttribute? by ConeAttributes.attributeAccessor<ConeNumberSignAttribute>()` at top level, the checker code can't read the attribute back from a type. Compiles fine; checker silently never fires.
3. **`union` returns non-null when signs differ** — joining `@Positive` and `@Negative` with `Positive` (or `Negative`) is unsound. Should return `null` to drop the attribute. Without this, branch unification produces wrong-sign types in `if`/`when` results.
4. **Implementing `convertAttributeToAnnotation` without the type guard** — returning a `FirAnnotation` for any incoming `ConeAttribute<*>` (not just yours) corrupts metadata when other extensions or compiler-internal attributes flow through. Always start with `if (attribute !is YourAttribute) return null`.
5. **Forgetting `keepInInferredDeclarationType = true`** — defaults aren't given here (the property is abstract). Setting `false` causes the attribute to silently disappear from inferred function return types, so `fun makePositive(): @Positive Int` declared with explicit type works but `fun makePositive() = positive(5)` (relying on inference) loses the attribute. Set `true` for refinement attributes you want users to interact with.
6. **Using value-parameter `check(declaration, context, reporter)` instead of context-parameter form** — Kotlin 2.3.x checker signature uses `context(context: CheckerContext, reporter: DiagnosticReporter) override fun check(...)`. Old form doesn't override, leaves abstract member unimplemented.
7. **`-Xcontext-parameters` flag missing from plugin module** — the checker code itself uses context parameters, so the plugin module must enable the flag.
8. **`-Xrender-internal-diagnostic-names` flag missing from sample/consumer module** — without it, build output shows only the message text; CI greps for `ILLEGAL_NUMBER_SIGN` won't match.
9. **Forgetting `registerDiagnosticContainers(SignsDiagnostics)` in the FIR registrar** — produces `IllegalStateException: Diagnostic factory was not registered` at compile time of the consumer.
10. **Ambiguity with another type attribute extension** — not a real risk in this evaluation (only one extension), but worth noting: type-attribute extensions iterate all registered extensions for round-tripping; an over-eager `convertAttributeToAnnotation` (returning non-null for someone else's attribute) silently corrupts metadata.

## Estimated effort

A correctly-skilled agent should complete the core (criteria 1–12) in **5–7 rounds**. The `ConeAttribute` contract has six abstract members and most agents will need at least one debug iteration to get the `union`/`intersect`/`add` semantics right. The metadata round-trip (criterion 13) typically adds another 2–3 rounds.

If the agent finishes in fewer than 5 rounds, suspect that they took shortcuts (e.g. skipping `convertAttributeToAnnotation`, returning a constant `true` from `isSubtypeOf` when something more nuanced was needed, or declaring `attributeAccessor` but never actually reading from it).

## Score weighting

This task carries **4× the weight** of the small task in overall scoring, reflecting the increased number of moving parts (type-level metadata + checker + diagnostic + round-trip).

## Pre-existing reference

The forbidden references above are the canonical implementation. `skills/kotlin-compiler-plugin/references/compiler-plugin-bootstrap/example/` provides the project boilerplate; the agent may freely copy `gradle/`, `gradlew`, `settings.gradle.kts`, and the registrar pattern from there.
