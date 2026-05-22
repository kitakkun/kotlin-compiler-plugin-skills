# Verification 03 — `FirSupertypeGenerationExtension`

## Goal

Verify that `FirSupertypeGenerationExtension` injects supertypes onto **the companion** of an annotated class (the kotlinx-serialization pattern), not onto the class itself.

## Sample requirements

- `interface Marker { fun marked(): String }`
- `annotation class Tagged`
- `@Tagged class Foo { companion object { fun marked() = "yes" } }` — user must supply `marked()` impl since `Marker` has no default.
- `fun main() { println(Foo.Companion.marked()); println((Foo.Companion as Marker).marked()) }` — both calls succeed; the cast must NOT fail at runtime.

## PASS criterion

`./gradlew :sample:run` succeeds AND `Foo.Companion as Marker` does not throw a `ClassCastException`. The CHECKCAST in bytecode is the proof that the supertype was actually attached, not just declared in metadata.

## Skills to consult

- `fir-supertype-generation-extension`
- `fir-predicate-system`
- `compiler-plugin-bootstrap`
