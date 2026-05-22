# Verification 13 — `FirStatusTransformerExtension` flipping `isInline`

## Goal

Verify that `FirStatusTransformerExtension` can flip the `isInline` flag on a
top-level function and that the change takes effect end-to-end (the function
actually behaves as `inline` in later compiler phases). Verification 04 already
exercised the modality slot of the status; this verification covers a different
modifier slot (`isInline`) to confirm the SKILL.md claim that "visibility,
modality, isOpen, isFinal, isInline, etc." are all modifiable.

## Litmus test

The cleanest end-to-end probe of "is this function effectively inline?" is a
**non-local return** from a lambda passed to it. Non-local return from a
function-typed parameter compiles ONLY when the enclosing function is `inline`
(and the parameter is not `crossinline`/`noinline`). If the plugin flips the
status flag but the change is somehow ignored downstream, `Sample.kt` will fail
to compile with `'return' is not allowed here` (or `not inline`); if the flip
takes full effect, the sample compiles and runs.

## Sample requirements

- `annotation class MakeInline`
- `@MakeInline fun runIt(block: () -> Unit) { block() }` — declared **without**
  the `inline` keyword. The plugin should flip it.
- `fun findIt(): String { runIt { return "found" }; return "not-found" }` —
  the `return "found"` is a non-local return from the lambda, only legal when
  `runIt` is effectively `inline`.
- `fun main() { println(findIt()) }`

## PASS criterion

`../gradlew :sample:run` compiles and prints `found`. Without the plugin (or if
`isInline` were silently ignored) `Sample.kt` fails to compile with
`'return' is not allowed here` or a similar non-local return diagnostic.

## Skills to consult

- `fir-status-transformer-extension`
- `fir-predicate-system`
- `compiler-plugin-bootstrap`
