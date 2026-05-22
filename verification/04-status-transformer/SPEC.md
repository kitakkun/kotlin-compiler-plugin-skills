# Verification 04 — `FirStatusTransformerExtension`

## Goal

Verify that `FirStatusTransformerExtension` flips modifiers on existing user declarations. Build a plugin that makes regular classes annotated `@com.example.Open` (and their methods) `open`.

## Sample requirements

- Use a **regular class**, NOT `data class` (data classes can't be `open` regardless of plugin).
- `annotation class Open`
- `@Open class Base { fun greet(): String = "from Base" }`
- `class Sub : Base() { override fun greet(): String = "from Sub" }` — without the plugin this fails with "method greet in 'Base' is final".
- `fun main() { val s: Base = Sub(); println(s.greet()) }` — must print `from Sub`.

## PASS criterion

`./gradlew :sample:run` compiles and prints `from Sub`. Note that `status.modality` is `null` (not `Modality.FINAL`) when the user wrote no explicit keyword — the canonical fix is `copyWithNewDefaults(modality = OPEN, defaultModality = OPEN)`, mirrored from the production allopen plugin.

## Skills to consult

- `fir-status-transformer-extension`
- `fir-predicate-system`
- `compiler-plugin-bootstrap`
