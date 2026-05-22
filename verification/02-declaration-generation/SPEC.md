# Verification 02 — `FirDeclarationGenerationExtension`

## Goal

Verify synthesis of source-visible members. Build a plugin that generates a synthetic `companion object` containing `fun greet(): String` on classes annotated with `@com.example.WithCompanion`.

## Sample requirements

- `annotation class WithCompanion`
- `@WithCompanion class Foo`
- `fun main() { println(Foo.greet()) }` — must compile and print `hello` at runtime.

## PASS criterion

`./gradlew :sample:run` prints `hello`. The plugin must:

1. Use FIR to declare the companion + `greet` member (`getNestedClassifiersNames`, `generateNestedClassLikeDeclaration`, `getCallableNamesForClass`, `generateFunctions`).
2. Use IR (`IrGenerationExtension`) to fill `greet`'s body with `return "hello"` — declarations from FIR generation arrive at IR with `body = null`.

## Skills to consult

- `fir-declaration-generation-extension`
- `ir-body-modification`
- `fir-predicate-system`
- `compiler-plugin-bootstrap`
