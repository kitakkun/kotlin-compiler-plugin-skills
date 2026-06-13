# Evidence — fir-assign-expression-alterer-extension

Primary-source citations. Paths relative to the kotlin-lang clone root.

## API surface

| Claim | Citation |
|---|---|
| Single abstract method `transformVariableAssignment(variableAssignment: FirVariableAssignment): FirStatement?` | [`compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirAssignExpressionAltererExtension.kt:16-30`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirAssignExpressionAltererExtension.kt#L16-L30) |
| KDoc: "It's allowed to transform [variableAssignment] into any kind of statement. This state should be unresolved (modulo usages of already resolved parts, like lValue). Later this statement will be resolved by compiler itself using regular resolution algorithms" | same file, KDoc above the method |
| Service accessor `assignAltererExtensions` exists | same file, line 35 |

## Invocation site and resolver semantics

| Claim | Citation |
|---|---|
| Resolver iterates all extensions and collects non-null results: `val alteredAssignments = assignAltererExtensions.mapNotNull { ... }` | [`compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/transformers/body/resolve/FirExpressionsResolveTransformer.kt:1323-1357`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/transformers/body/resolve/FirExpressionsResolveTransformer.kt#L1323-L1357) (around `transformVariableAssignment`) |
| If exactly one extension returns non-null, the returned statement is re-resolved with `transform(transformer, ContextIndependent)` at `FirExpressionsResolveTransformer.kt:1343` | within the `1 -> { ... }` branch of the `mapNotNull`-based dispatch |
| If multiple extensions return non-null, `ConeAmbiguousAlteredAssign(extensionNames)` is reported and the lValue is replaced with an error expression | same site, the `else -> { ... }` branch |
| Extension only consulted when `resolvedReference is FirResolvedNamedReference` (i.e. property assignments where LHS resolved cleanly) | same site, the `if (assignAltererExtensions != null && resolvedReference is FirResolvedNamedReference)` guard |

## Reference impl: assign-plugin

| Claim | Citation |
|---|---|
| Production usage in the Kotlin Assignment Compiler Plugin (JetBrains-maintained; the only production user in the kotlin-lang tree) | [`plugins/assign-plugin/assign-plugin.k2/src/org/jetbrains/kotlin/assignment/plugin/k2/FirAssignmentPluginAssignAltererExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/plugins/assign-plugin/assign-plugin.k2/src/org/jetbrains/kotlin/assignment/plugin/k2/FirAssignmentPluginAssignAltererExtension.kt) |
| Filters by `lSymbol.isVal && lSymbol.hasSpecialAnnotation()` for property/backing-field/field symbols | same file, `supportsTransformVariableAssignment` |
| Builds replacement as `FirFunctionCall { explicitReceiver = (property access of LHS); calleeReference = buildSimpleNamedReference { name = ASSIGN_METHOD } }` — unresolved, the resolver re-binds | same file, `buildFunctionCall` block in `buildFunctionCall` private function |
| Propagates `contextArguments += variableAssignment.contextArguments` into the replacement's receiver | same file, in the same builder block |
| Registrar uses `+::FirAssignmentPluginAssignAltererExtension` | [`plugins/assign-plugin/assign-plugin.k2/src/org/jetbrains/kotlin/assignment/plugin/k2/FirAssignmentPluginExtensionRegistrar.kt:11-19`](https://github.com/JetBrains/kotlin/blob/v2.4.0/plugins/assign-plugin/assign-plugin.k2/src/org/jetbrains/kotlin/assignment/plugin/k2/FirAssignmentPluginExtensionRegistrar.kt#L11-L19) |

## Wiring

| Claim | Citation |
|---|---|
| `FirAssignExpressionAltererExtension` in `AVAILABLE_EXTENSIONS` | [`compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt) (search the list) |
| `+::Constructor` operator defined | same file (the corresponding `unaryPlus` overload, around lines 95-99 / 185-188) |

## Scope limitations

| Claim | Citation |
|---|---|
| Local `val` reassignment is `VAL_REASSIGNMENT` and never reaches the extension | [`plugins/assign-plugin/testData/diagnostics/localVariables.kt:16-44`](https://github.com/JetBrains/kotlin/blob/v2.4.0/plugins/assign-plugin/testData/diagnostics/localVariables.kt#L16-L44) shows `<!VAL_REASSIGNMENT!>` markers |
| Local `var` reassignment with same type is allowed but doesn't go through the alterer | same file |
| Method parameter reassignment is `VAL_REASSIGNMENT` | same file |

## Test data

| Claim | Citation |
|---|---|
| Codegen test verifies `task.input = "OK"` becomes `task.input.assign("OK")` | [`plugins/assign-plugin/testData/codegen/supportedUsage.kt:30-96`](https://github.com/JetBrains/kotlin/blob/v2.4.0/plugins/assign-plugin/testData/codegen/supportedUsage.kt#L30-L96) |
| Annotation-based filter test (`@ValueContainer` on the property's type, with an `assign(...)` member) | [`plugins/assign-plugin/testData/diagnostics/otherOperators.kt:1-12`](https://github.com/JetBrains/kotlin/blob/v2.4.0/plugins/assign-plugin/testData/diagnostics/otherOperators.kt#L1-L12) |

## Notes

The KDoc's emphasis on returning unresolved statements is critical: the resolver at line 1517 of `FirExpressionsResolveTransformer.kt` calls `.transform(transformer, ContextIndependent)` on the returned statement, and any pre-resolved sub-expression that conflicts with that transform produces a non-obvious crash. Always build the replacement with the FIR builder DSL using only `buildSimpleNamedReference { name = ... }` (not pre-resolved symbols) for the new callee.
