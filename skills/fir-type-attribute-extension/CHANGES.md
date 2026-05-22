# Changes affecting this skill

API migrations relevant to writing `FirTypeAttributeExtension`. This skill targets the **current stable Kotlin** (2.3.x).

## Kotlin 2.0.0 → 2.3.x: stable

No breaking API changes. The two abstract methods (`extractAttributeFromAnnotation`, `convertAttributeToAnnotation`), the `ConeAttribute<T>` contract (`union`/`intersect`/`add`/`isSubtypeOf`/`key`/`keepInInferredDeclarationType`), and the mandatory `ConeAttributes.attributeAccessor<T>()` accessor pattern have been consistent throughout this range.

The `FirTypeAttributeExtension` class was introduced before 2.0.0 (commit `38877ba8424c`, "[FIR] Implement extension for providing additional type attributes"); the only post-2.0.0 commit on the file is `c8227743dc6f` ("Specify the return type for all public declarations") which is a cosmetic change with no behavioural effect.

## Notes on related infrastructure

- `ConeAttributes.filterNecessaryToKeep()` (which strips attributes with `keepInInferredDeclarationType = false` during declaration-type approximation) has been stable across 2.0.0 → 2.3.x.
- The known limitation that common-supertype calculation in generic functions does not propagate plugin attributes (visible in `signedNumbersCheckers.kt` test data) has been present throughout this range; it is a structural limitation of the type approximator rather than an API surface concern.
