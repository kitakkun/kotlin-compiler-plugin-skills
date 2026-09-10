# Evidence — fir-sam-conversion-transformer-extension

Primary-source citations. Paths relative to the kotlin-lang clone root.

## API surface

| Claim | Citation |
|---|---|
| `FirSamConversionTransformerExtension` has a single abstract method `getCustomFunctionTypeForSamConversion(function: FirNamedFunction): ConeLookupTagBasedType?` | [`compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/FirSamConversionTransformerExtension.kt:16-31`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/FirSamConversionTransformerExtension.kt#L16-L31) |
| Service accessor `samConversionTransformers` exists | same file, line 31 |

## Invocation site

| Claim | Citation |
|---|---|
| The resolver consults the extension only when the class is `fun interface`: `if (!firRegularClass.status.isFun) return@getOrPut null` | [`compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/FirSamResolver.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/FirSamResolver.kt) (function `resolveFunctionTypeIfSamInterface` declared at line 299) |
| The resolver uses `firstNotNullOfOrNull` — first non-null wins, no priority/ambiguity | same file, line 304: `samConversionTransformers.firstNotNullOfOrNull { it.getCustomFunctionTypeForSamConversion(abstractMethod) }` |
| The result is cached per `FirRegularClass` via `resolvedFunctionType.getOrPut(firRegularClass)` | same file, in the same function |
| The custom type, when non-null, replaces the abstract method's default function type | same file, `SAMInfo(abstractMethod.symbol, typeFromExtension ?: abstractMethod.getFunctionTypeForAbstractMethod(session))` |

## Reference impl: sam-with-receiver

| Claim | Citation |
|---|---|
| `FirSamWithReceiverConventionTransformer` looks up the containing class's annotations and only fires when one matches the configured list | [`plugins/sam-with-receiver/sam-with-receiver.k2/src/org/jetbrains/kotlin/samWithReceiver/k2/FirSamWithReceiverConventionTransformer.kt:20-30`](https://github.com/JetBrains/kotlin/blob/v2.4.10/plugins/sam-with-receiver/sam-with-receiver.k2/src/org/jetbrains/kotlin/samWithReceiver/k2/FirSamWithReceiverConventionTransformer.kt#L20-L30) |
| Empty parameter list returns `null` (no receiver to extract) | same file, `if (parameterTypes.isEmpty()) return null` |
| Function-type kind is taken via `session.functionTypeService.extractSingleSpecialKindForFunction(function.symbol) ?: FunctionTypeKind.Function` so that `suspend` and other kinds are honoured | same file, `val kind = session.functionTypeService.extractSingleSpecialKindForFunction(function.symbol) ?: FunctionTypeKind.Function` |
| Receiver promotion uses `createFunctionType(kind, parameters = parameterTypes.subList(1, parameterTypes.size), receiverType = parameterTypes[0], rawReturnType = function.returnTypeRef.coneType)` | same file, the `createFunctionType` call |
| Registrar uses `+::FirSamWithReceiverConventionTransformer.bind(annotations)` to inject CLI-configured marker class names | [`plugins/sam-with-receiver/sam-with-receiver.k2/src/org/jetbrains/kotlin/samWithReceiver/k2/FirSamWithReceiverExtensionRegistrar.kt:10-15`](https://github.com/JetBrains/kotlin/blob/v2.4.10/plugins/sam-with-receiver/sam-with-receiver.k2/src/org/jetbrains/kotlin/samWithReceiver/k2/FirSamWithReceiverExtensionRegistrar.kt#L10-L15) |

## Second production usage

| Claim | Citation |
|---|---|
| The scripting plugin has a parallel `FirScriptSamWithReceiverConventionTransformer` reading annotations from `scriptConfigurators` | [`plugins/scripting/scripting-compiler/src/org/jetbrains/kotlin/scripting/compiler/plugin/FirScriptingSamWithReceiverExtensionRegistrar.kt:32`](https://github.com/JetBrains/kotlin/blob/v2.4.10/plugins/scripting/scripting-compiler/src/org/jetbrains/kotlin/scripting/compiler/plugin/FirScriptingSamWithReceiverExtensionRegistrar.kt#L32) |

## Wiring

| Claim | Citation |
|---|---|
| `FirSamConversionTransformerExtension` is in `AVAILABLE_EXTENSIONS` | [`compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt:31`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt#L31) |
| `+::Constructor` operator defined for `(FirSession) -> FirSamConversionTransformerExtension` | same file, lines 180-183 |
| `.bind(...)` helper for partial application of constructor parameters | same file, lines 240-247 (`bindLeft`) |

## Test data

| Claim | Citation |
|---|---|
| Simple-parameter promotion test with `@SamWithReceiver` on Java `interface Sam { void run(String a) }` | [`plugins/sam-with-receiver/testData/diagnostics/samConversionSimple.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/plugins/sam-with-receiver/testData/diagnostics/samConversionSimple.kt) |
| Multi-parameter codegen test (one becomes receiver, rest become params) | [`plugins/sam-with-receiver/testData/codegen/SamConversion.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/plugins/sam-with-receiver/testData/codegen/SamConversion.kt) |
| Empty-parameter SAM falls back to default conversion (no receiver promotion possible), `<!NO_THIS!>` marker on `this` reference | [`plugins/sam-with-receiver/testData/diagnostics/samConversionNoParameters.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/plugins/sam-with-receiver/testData/diagnostics/samConversionNoParameters.kt) |

## Notes

`ConeLookupTagBasedType` is the base type for class-like `Cone` types (the FIR-internal type representation); `createFunctionType` produces a `ConeClassLikeType` which is a subclass of `ConeLookupTagBasedType`. The exact path of `createFunctionType` has moved between `compiler/fir/types/` and `compiler/fir/cones/` across Kotlin versions; grep for the function name to find the current location.
