# Benchmark 03 — `@Trace` Plugin (High)

A compiler plugin that wraps annotated functions with entry/exit logging, including a custom diagnostic for invalid usage. Tests body modification, custom diagnostics, predicate-driven filtering, and (optionally) integration tests.

## Specification

Implement a plugin providing:

```kotlin
// User-defined annotation:
package com.example.app
annotation class Trace

// Function-level usage:
@Trace fun greet(name: String): String {
    return "Hello, $name"
}

// Class-level usage (applies to every public top-level/member function):
@Trace class Calculator {
    fun add(a: Int, b: Int): Int = a + b
    fun multiply(a: Int, b: Int): Int = a * b
    private fun helper() {}    // NOT traced — private excluded
}
```

Behaviour:

1. Calling a `@Trace` function prints `-> functionName(args)` before the body executes, where `args` is each argument's `toString()` joined by `, `.
2. Calling it prints `<- functionName` after the body returns (regardless of normal return or exception via `try { ... } finally { ... }`).
3. Class-level `@Trace` applies the same wrapping to all `public` member functions of the class (excluding `private`, `internal`, and `protected`).
4. Output goes to `System.out` via `kotlin.io.println`.

### Diagnostics (compile-time)

A custom checker emits these errors:

- **`TRACE_ON_INLINE`** — error when `@Trace` is on an `inline fun`. Message: `"@Trace cannot be applied to inline functions"`. Squiggle on the `inline` modifier.
- **`TRACE_ON_OPERATOR`** — error when `@Trace` is on an operator function. Message: `"@Trace cannot be applied to operator functions"`. Squiggle on the function name.

These don't block compilation of valid code; they only fire on invalid uses.

### Optional: plugin tests (counts toward acceptance)

Include an integration test that exercises the plugin against sample source. Per the `compiler-plugin-testing` skill, use a separate `sample/` module that loads the plugin via `-Xplugin=` and verifies the result (diagnostic test: compilation fails with expected error; box test: compilation succeeds and program prints expected output).

The test should:

- Compile a snippet with `@Trace fun foo() { }` and assert the resulting bytecode contains the `println` call.
- Compile a snippet with `@Trace inline fun bad() { }` and assert the compilation fails with `TRACE_ON_INLINE` in the messages.

## Project layout

```
evaluation/03-high-trace-plugin/work/
├── settings.gradle.kts, gradle.properties, build.gradle.kts
├── gradle/, gradlew      ← copy from skills/kotlin-compiler-plugin/references/compiler-plugin-bootstrap/example/
├── plugin/
│   └── src/test/kotlin/com/example/trace/TraceTest.kt   ← optional but recommended
└── sample/
    └── src/main/kotlin/com/example/app/Main.kt
```

`Main.kt`:

```kotlin
package com.example.app

annotation class Trace

@Trace fun greet(name: String): String = "Hello, $name"

@Trace class Calculator {
    fun add(a: Int, b: Int): Int = a + b
    fun multiply(a: Int, b: Int): Int = a * b
    private fun helper() {}
}

fun main() {
    println(greet("Alice"))
    val calc = Calculator()
    println(calc.add(3, 4))
    println(calc.multiply(2, 5))
    calc.javaClass.getDeclaredMethod("helper").apply { isAccessible = true }.invoke(calc)
}
```

(Reflection on `helper` is so we can verify it ISN'T traced even when called.)

## Acceptance criteria

| # | Criterion | How to verify |
|---|---|---|
| 1 | Plugin builds | `./gradlew :plugin:jar` |
| 2 | Sample compiles | `./gradlew :sample:compileKotlin` |
| 3 | Sample runs without crashing | `./gradlew :sample:run` exits 0 |
| 4 | `greet` is traced on entry | output contains `-> greet(Alice)` |
| 5 | `greet` is traced on exit | output contains `<- greet` |
| 6 | `add` is traced on entry/exit | output contains `-> add(3, 4)` and `<- add` |
| 7 | `multiply` is traced on entry/exit | output contains `-> multiply(2, 5)` and `<- multiply` |
| 8 | `helper` is NOT traced | output does NOT contain `-> helper(` |
| 9 | Original behavior preserved | `Hello, Alice`, `7`, `10` all appear in output |
| 10 | `@Trace inline fun` errors | a sample fragment `@Trace inline fun bad() {}` produces `TRACE_ON_INLINE` on compile |
| 11 | `@Trace operator fun` errors | a sample fragment `@Trace operator fun Int.foo() {}` produces `TRACE_ON_OPERATOR` |
| 12 | Trace order is correct around exceptions | a fragment that throws inside `@Trace fun thrower() { error("oops") }` still prints `<- thrower` (via `try/finally`) before propagating |
| 13 *(optional)* | Plugin tests pass | `./gradlew :plugin:test` exits 0 |

## Verification procedure

```bash
cd evaluation/03-high-trace-plugin/work

# Compile + run
./gradlew :sample:run --rerun-tasks --console=plain 2>&1 | tee /tmp/03-out.txt

# Behaviour checks
grep -q '^-> greet(Alice)' /tmp/03-out.txt
grep -q '^<- greet' /tmp/03-out.txt
grep -q '^-> add(3, 4)' /tmp/03-out.txt
grep -q '^<- add' /tmp/03-out.txt
grep -q '^-> multiply(2, 5)' /tmp/03-out.txt
grep -q '^<- multiply' /tmp/03-out.txt
grep -F '-> helper(' /tmp/03-out.txt && echo "FAIL: helper was traced" || echo "OK"
grep -q 'Hello, Alice' /tmp/03-out.txt
grep -q -E '^7$' /tmp/03-out.txt        # add(3, 4)
grep -q -E '^10$' /tmp/03-out.txt       # multiply(2, 5)

# Diagnostic checks (build separate sources)
# (Add @Trace inline fun bad() {} to a temp file, compile, expect TRACE_ON_INLINE)
# (Add @Trace operator fun Int.foo() {} to a temp file, compile, expect TRACE_ON_OPERATOR)

# Optional: tests
./gradlew :plugin:test
```

## Skills exercised

| Skill | Used for |
|---|---|
| `compiler-plugin-bootstrap` | project layout, registrar |
| `fir-extensions-overview` | extension registration |
| `fir-predicate-system` | predicate for `@Trace`-annotated detection |
| `fir-additional-checkers-extension` | `TRACE_ON_INLINE`, `TRACE_ON_OPERATOR` diagnostics with positioning |
| `ir-plugincontext-usage` | look up `kotlin.io.println`, `Any.toString()`, exception path |
| `ir-body-modification` | wrap function body in `try { print; original; print } finally { print }` |
| `ir-call-rewriting` | NOT needed — body modification is the right tool here |
| `compiler-plugin-testing` | (optional) integration tests for diagnostics / behaviour |

## Common failure modes (anti-cheat)

1. **`@Trace inline fun` is silently allowed (no diagnostic)** → checker not registered or condition wrong.
2. **`<- ` line missing on exception** → body wrapping done with prepend/append instead of `try { } finally { }`.
3. **Private `helper` is traced** → predicate / status filter doesn't exclude `private`. Need to check `function.visibility == Visibilities.Public`.
4. **Class-level `@Trace` doesn't propagate to members** → predicate registers `annotated(...)` only, missing `parentAnnotated(...) or ancestorAnnotated(...)`.
5. **Class members getting double-traced** (when both class and individual fns are `@Trace`) → predicate needs OR semantics, not double-application of two transformers.
6. **`-> name(args)` with wrong arg formatting** → arg list construction in IR uses raw types instead of `toString()` calls. Easy to mis-build with `IrStringConcatenation`.
7. **Body modification mutates original IR in place** → corrupts ordering for IR validation. Use `irBlockBody { ... }` builder.
8. **`patchDeclarationParents` not called** after restructuring → "declaration's parent is wrong" later in lowering.
9. **Plugin test sample module misconfigured** → verify `-Xplugin=` path points to the plugin JAR and `META-INF/services` is present.

## Estimated effort

A correctly-skilled agent should complete the core (criteria 1–9) in **3–5 rounds**. Diagnostics (10–11) and exception-safe wrapping (12) typically require an additional debug round each. The optional integration test (13) adds another round.

If the agent finishes in fewer than 3 rounds, suspect that they took shortcuts (e.g. skipping the `try/finally` wrapping by just prepending+appending statements, which fails criterion 12).

## Score weighting

This task carries **3× the weight** of the small task in overall scoring.

## Pre-existing reference

The `verification/` subdirectories contain working reference impls of the underlying extensions. Do not let agents copy from them during evaluation; they are hints, not templates.
