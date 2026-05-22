# Verification 08 — `FirSamConversionTransformerExtension`

## Goal

Verify the sam-with-receiver pattern: SAM interfaces annotated `@com.example.WithReceiver` should have their first parameter promoted to a receiver, so lambda bodies can use `this` to refer to it.

## Sample requirements

- `annotation class WithReceiver`
- `@WithReceiver fun interface Action<T> { fun run(receiver: T) }`
- `fun execute(action: Action<String>) { action.run("hello") }`
- `fun main() { execute { println("got: " + this) } }` — `this` inside the lambda refers to the `String` parameter, only possible because the plugin moved it into the receiver position.

## PASS criterion

`./gradlew :sample:run` compiles AND prints `got: hello`.

## Skills to consult

- `fir-sam-conversion-transformer-extension`
- `fir-predicate-system`
- `compiler-plugin-bootstrap`
