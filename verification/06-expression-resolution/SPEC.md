# Verification 06 — `FirExpressionResolutionExtension`

## Goal

Verify that `FirExpressionResolutionExtension` injects an implicit extension receiver into call resolution. Inside a function annotated `@com.example.DslContext`, calls should resolve as if a `Dsl` receiver were in scope.

## Sample requirements

- `class Dsl { fun greet() = "from Dsl" }`
- `annotation class DslContext`
- `@DslContext fun runDsl(d: Dsl) { /* trigger */ ; println(greet()) }` — note `greet()` has no explicit receiver; the plugin must inject `Dsl` implicitly.
- `fun main() { runDsl(Dsl()) }` — must print `from Dsl`.

## PASS criterion

`./gradlew :sample:run` compiles AND prints `from Dsl`.

## Known traps (caveat)

- **Receivers attach between calls, not retroactively**: `addReceiversFromExtensions` runs after each call completes resolution. The first call in the body does NOT see extension-added receivers — a "trigger" call (e.g. a no-op) before the receiver-using call is required.
- **Synthetic receivers have no runtime value**: must pair with an `IrGenerationExtension` that rewrites `IrErrorCallExpression`s of the synthetic receiver type to `IrGetValue` of an actual parameter that supplies the value.

## Skills to consult

- `fir-expression-resolution-extension`
- `fir-predicate-system`
- `ir-body-modification`
- `compiler-plugin-bootstrap`
