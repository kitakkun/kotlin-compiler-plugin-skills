# Changes affecting this skill

API migrations relevant to writing `FirAssignExpressionAltererExtension`. This skill targets the **current stable Kotlin** (2.4.20).

## Kotlin 2.0.0 → 2.3.x: stable

No breaking API changes. The single abstract method `transformVariableAssignment(variableAssignment: FirVariableAssignment): FirStatement?`, the resolver's iterate-and-collect-non-null invocation (`mapNotNull` followed by a `when (alteredAssignments.size)` switch at `FirExpressionsResolveTransformer.kt:1335-1356`), and the `ConeAmbiguousAlteredAssign` diagnostic on multi-extension claim have been consistent throughout this range.

The few commits on the file since 2.0.0 are bug fixes in the assign-plugin's `assign` method resolution (`KT-57468` and follow-ups, commits `bb5b98280f703`, `bca8f2871542`, `178881021221`) — these are issues in the *consumer* of the API, not the API itself.

## Notes on related infrastructure

- The KDoc requirement that the returned statement be **unresolved** has been a constant. Returning a pre-resolved `FirFunctionCall` produces a non-obvious crash in the resolver's re-resolution pass; this is consistent across all 2.x versions.
- `contextArguments` propagation (the explicit `contextArguments += variableAssignment.contextArguments` line in the assign-plugin) has been required since context parameters were introduced as a language feature; if you're upgrading a plugin that predates context parameters, you'll need to add this propagation when supporting Kotlin 2.x.
