# Verification 05 — `FirExtensionSessionComponent`

## Goal

Verify that two FIR extensions share a per-session cached state. Build a plugin where one session component (`TrackedRegistry`) maintains a set of `@Tracked` classes, and two consumer extensions — a `FirAdditionalCheckersExtension` (warns on tracked classes) and a `FirStatusTransformerExtension` (makes them `open`) — both read the SAME instance via `session.trackedRegistry.isTracked(symbol)`.

## Sample requirements

- `annotation class Tracked`
- `@Tracked class Base { fun greet() = "Base" }`
- `class Sub : Base()` — succeeds only because the opener flipped Base's modality.
- `fun main() { val s: Base = Sub(); println(s.greet()) }` — prints `Base`.

## PASS criterion

- `./gradlew :sample:run` compiles AND prints `Base`.
- The build log includes the `TRACKED_USAGE` warning on `Base` (proves the checker fired).
- `Sub : Base()` compiling without `FINAL_SUPERTYPE` (proves the opener fired).
- Both behaviours rely on the same `Set<ClassId>` collected once via the session component.

## Skills to consult

- `fir-session-components`
- `fir-additional-checkers-extension`
- `fir-status-transformer-extension`
- `fir-predicate-system`
