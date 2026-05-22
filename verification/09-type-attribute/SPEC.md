# Verification 09 — `FirTypeAttributeExtension`

## Goal

Verify that plugin-defined annotations on types participate in type-checking. `@com.example.Positive Int` must be a distinct type from plain `Int`, and a function `takePositive(x: @Positive Int)` should reject a plain `Int` argument with a custom diagnostic.

## Sample requirements

- `@Target(AnnotationTarget.TYPE) annotation class Positive` — the `@Target(TYPE)` is mandatory for type-position usage.
- `fun takePositive(x: @Positive Int) { println("ok: $x") }`
- `fun main() { val p: @Positive Int = 5; takePositive(p); val n: Int = 5; takePositive(n) }`

## PASS criterion

`./gradlew :sample:compileKotlin` fails with a custom diagnostic on the `takePositive(n)` call. The first call `takePositive(p)` (where `p: @Positive Int`) compiles cleanly.

## Notes

- The `FirPropertyChecker` example in the skill over-flags numeric literal initialisers (`val p: @Positive Int = 5` — the literal `5` carries no `@Positive`). The verification plugin should rely on the `FirFunctionCallChecker` only for argument-vs-parameter comparisons and not break on the property declaration.

## Skills to consult

- `fir-type-attribute-extension`
- `fir-additional-checkers-extension`
- `compiler-plugin-bootstrap`
