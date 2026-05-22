# Verification 10 — `FirFunctionTypeKindExtension`

## Goal

Verify a new function-type family. `@com.example.MyKind () -> Unit` must be a different type from plain `() -> Unit` for overload resolution and inference.

## Sample requirements

- `annotation class MyKind`
- `fun acceptMyKind(block: @MyKind () -> Unit) { block() }`
- `fun acceptPlain(block: () -> Unit) { block() }`
- `fun main() { acceptMyKind @MyKind { println("MyKind") } }` — must compile and print `MyKind`.
- Bonus: passing the same `@MyKind` lambda to `acceptPlain` must fail with a type mismatch (proves distinctness).

## PASS criterion

`./gradlew :sample:run` compiles and prints `MyKind`. The plugin must include:

1. A `FunctionTypeKind` subclass `MyKind` and its reflect counterpart `KMyKind`, registered via `FirFunctionTypeKindExtension.registerKind(...)`.
2. An `IrGenerationExtension` that lowers the synthetic `MyKindFunctionN` / `KMyKindFunctionN` classes back to standard `kotlin.FunctionN` / `kotlin.reflect.KFunctionN` at codegen — without this, JVM lambda instantiation fails at runtime.

## Skills to consult

- `fir-function-type-kind-extension`
- `ir-body-modification`
- `compiler-plugin-bootstrap`
