# Evidence — fir-function-call-refinement-extension

Primary-source citations. Paths relative to the kotlin-lang clone root.

## Stability gate

| Claim | Citation |
|---|---|
| The class is annotated `@FirExtensionApiInternals` and the KDoc reads `!!!! This extension is highly unstable and not recommended to use !!!!` | [`compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirFunctionCallRefinementExtension.kt:21-32`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirFunctionCallRefinementExtension.kt#L21-L32) |

## API surface

| Claim | Citation |
|---|---|
| Five abstract methods: `intercept`, `transform`, `ownsSymbol`, `anchorElement`, `restoreSymbol` | same file, full body |
| `intercept(callInfo, symbol): CallReturnType?` — KDoc says "When [intercept] returns non-null value, a copy will be created from FirFunction... Copy will be used in call completion instead of original function." | same file, KDoc on `intercept` |
| `transform(call, originalSymbol): FirFunctionCall` — KDoc says "[transform] needs to generate call to [let] with the same return type as [call] and put all generated declarations used in [FirResolvedTypeRef] in statements." | same file, KDoc on `transform` |
| `CallReturnType(typeRef: FirResolvedTypeRef, callback: ((FirNamedFunctionSymbol) -> Unit)? = null)` | same file, the nested class |
| KDoc constraint: "Generated declarations should be local because this [FirExtension] works at body resolve stage and thus cannot create new top level declarations" | same file, KDoc on `intercept` |

## Resolver call sites

| Claim | Citation |
|---|---|
| `intercept` is called from `CandidateFactory.replaceFromPluginsIfNeeded` after candidate selection; the resolver clones the function via `buildNamedFunctionCopy`, sets `returnTypeRef = result.typeRef`, and stores `originalCallDataForPluginRefinedCall` on the new function | [`compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/calls/candidate/CandidateFactory.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/calls/candidate/CandidateFactory.kt) (`replaceFromPluginsIfNeeded`) |
| Multiple non-null intercepts → `AmbiguousInterceptedSymbol` diagnostic, fallback to original | same file, around lines 239-242 |
| `transform` is called during outer-call completion in `FirExpressionsResolveTransformer` when the resolved callee carries `originalCallDataForPluginRefinedCall` | [`compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/transformers/body/resolve/FirExpressionsResolveTransformer.kt:606-614`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/transformers/body/resolve/FirExpressionsResolveTransformer.kt#L606-L614) |

## Reference impls

| Claim | Citation |
|---|---|
| Plugin-sandbox prototype: `DataFrameLikeCallsRefinementExtension` (~250 LOC) shows local-class generation + `let { ... }` wrapping | [`plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/DataFrameLikeCallsRefinementExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/DataFrameLikeCallsRefinementExtension.kt) |
| Production: `kotlin-dataframe` plugin's `FunctionCallTransformer` uses the same pattern with multiple internal sub-transformers | [`plugins/kotlin-dataframe/kotlin-dataframe.k2/src/org/jetbrains/kotlinx/dataframe/plugin/extensions/FunctionCallTransformer.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/plugins/kotlin-dataframe/kotlin-dataframe.k2/src/org/jetbrains/kotlinx/dataframe/plugin/extensions/FunctionCallTransformer.kt) |
| Production code includes a `shouldRefine` predicate filtering by call-site `@DisableInterpretation` and callee `@Refine` | same file, companion `shouldRefine` |
| Both impls start `transform` with `if (call.calleeReference is FirResolvedErrorReference) return call` to handle partial resolution | both reference files |

## Wiring

| Claim | Citation |
|---|---|
| `FirFunctionCallRefinementExtension::class` is in `AVAILABLE_EXTENSIONS` with `@OptIn(FirExtensionApiInternals::class)` | [`compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt:42`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt#L42) (verify line; in the AVAILABLE_EXTENSIONS list) |
| `unaryPlus` operator on the constructor reference is itself annotated `@FirExtensionApiInternals` | same file, the `plusFunctionCallRefinementExtension`-named overloads |
| Sandbox plugin registers via `@OptIn(FirExtensionApiInternals::class) +::DataFrameLikeCallsRefinementExtension` | [`plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/FirPluginPrototypeExtensionRegistrar.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/FirPluginPrototypeExtensionRegistrar.kt) |

## Test data

| Claim | Citation |
|---|---|
| Diagnostic test exercising the round-trip with `@Refine` annotation | [`plugins/plugin-sandbox/testData/diagnostics/receivers/callShapeBasedInjector.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/plugins/plugin-sandbox/testData/diagnostics/receivers/callShapeBasedInjector.kt) |
| The test uses `// RUN_PIPELINE_TILL: FRONTEND` so only the FIR side is exercised (IR-side codegen for refined calls is plugin-specific) | same file |

## Notes

The body of the cloned function is `null` — the refinement only changes the static type the resolver sees; runtime behaviour still goes through the original function. Plugins that need to also change runtime behaviour pair this extension with an `IrGenerationExtension` that recognises the refined-call pattern (e.g. by checking the return type's classifier against the plugin's marker, since the IR side sees only the original function symbol as the callee). The dataframe plugin combines both layers in production.

`@FirExtensionApiInternals` is a stable opt-in marker class in [`compiler/fir/extensions/.../FirExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirExtension.kt). Its presence is the compiler's way of signaling "this API may change in any minor release"; treat the API as semi-private.
