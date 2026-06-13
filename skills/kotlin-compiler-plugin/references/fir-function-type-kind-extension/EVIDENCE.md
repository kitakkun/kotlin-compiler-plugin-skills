# Evidence — fir-function-type-kind-extension

Primary-source citations. Paths relative to the kotlin-lang clone root.

## API surface

| Claim | Citation |
|---|---|
| `FirFunctionTypeKindExtension` exposes a single method `FunctionTypeKindRegistrar.registerKinds()` | [`compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirFunctionTypeKindExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirFunctionTypeKindExtension.kt) (full file) |
| `registerKind(nonReflectKind, reflectKind)` is the registration call | same file, in the `FunctionTypeKindRegistrar` interface |
| Service accessor `functionTypeKindExtensions` | same file (bottom) |

## FunctionTypeKind base class

| Claim | Citation |
|---|---|
| Plugin-facing constructor takes `packageFqName, classNamePrefix, annotationOnInvokeClassId, isReflectType, isInlineable, maxArity` (annotation is mandatory for plugins) | [`core/compiler.common/src/org/jetbrains/kotlin/builtins/functions/FunctionTypeKind.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/core/compiler.common/src/org/jetbrains/kotlin/builtins/functions/FunctionTypeKind.kt) (search for the secondary constructor with `annotationOnInvokeClassId: ClassId,`) |
| `prefixForTypeRender` defaults to `null`; overriding controls how the type renders in errors/IDE | same file |
| `serializeAsFunctionWithAnnotationUntil` defaults to `null`; setting to a `LanguageVersion.versionString` enables emit-as-`FunctionN+annotation` for older targets | same file |
| `supportsConversionFromSimpleFunctionType` defaults to `true` | same file |
| Built-in kinds `Function`, `SuspendFunction`, `KFunction`, `KSuspendFunction` are objects in this same file with their `nonReflectKind`/`reflectKind` overrides | same file |
| `SuspendFunction.maxArity = Function.maxArity - 1` (reserves a slot for `Continuation`) | same file |
| `DEFAULT_MAX_ARITY = 254` | same file, companion object |
| KDoc mandates: "if you provide some new functional type kind it's your responsibility to handle all references to it in backend with [IrGenerationExtension] implementation" | KDoc on `FunctionTypeKind` class |

## Reference impl: plugin-sandbox

| Claim | Citation |
|---|---|
| `SandboxFunctionTypeKindExtension` registers two pairs (inlineable + non-inlineable) | [`plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/types/SandboxFunctionTypeKindExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/types/SandboxFunctionTypeKindExtension.kt) (full file) |
| `MyInlineable` and `MyNotInlineable` annotations declared with `AnnotationTarget.FUNCTION, TYPE, PROPERTY_GETTER` | [`plugins/plugin-sandbox/plugin-annotations/src/commonMain/kotlin/org/jetbrains/kotlin/plugin/sandbox/annotations.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/plugins/plugin-sandbox/plugin-annotations/src/commonMain/kotlin/org/jetbrains/kotlin/plugin/sandbox/annotations.kt) |
| IR transformer rewrites `some.MyInlineableFunctionN` to `kotlin.FunctionN` and adds the annotation | [`plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/ir/PluginFunctionKindsTransformer.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/ir/PluginFunctionKindsTransformer.kt) |
| The IR transformer is registered in `GeneratedDeclarationsIrBodyFiller` | [`plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/ir/GeneratedDeclarationsIrBodyFiller.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/ir/GeneratedDeclarationsIrBodyFiller.kt) |

## Resolver path

| Claim | Citation |
|---|---|
| `FirFunctionTypeKindServiceImpl` builds the extractor by collecting built-ins + extension kinds, asserting `nonReflectKind.reflectKind() === reflectKind && reflectKind.nonReflectKind() === nonReflectKind` | [`compiler/fir/providers/src/org/jetbrains/kotlin/fir/types/FirFunctionTypeKindServiceImpl.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/types/FirFunctionTypeKindServiceImpl.kt) |
| Extension kinds are matched to lambdas by `annotationOnInvokeClassId` | same file, `extractKindsFromAnnotations` |
| Multiple matching annotations on a single function trigger `ConeAmbiguousFunctionTypeKinds` | [`compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/ResolveUtils.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/ResolveUtils.kt) (`constructFunctionTypeRef`, the `else -> { diagnostic = ConeAmbiguousFunctionTypeKinds(kinds); FunctionTypeKind.Function }` branch) |

## Wiring

| Claim | Citation |
|---|---|
| `FirFunctionTypeKindExtension` in `AVAILABLE_EXTENSIONS` (and library-allowed list) | [`compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt:38, 47`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt#L38) |
| Sandbox registrar uses `+::SandboxFunctionTypeKindExtension` (no opt-in needed) | [`plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/FirPluginPrototypeExtensionRegistrar.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/FirPluginPrototypeExtensionRegistrar.kt) (search for the line) |

## Type resolution and metadata

| Claim | Citation |
|---|---|
| Cross-module deserialization of custom kinds via `extractSingleExtensionKindForDeserializedConeType(classId, annotations)` | [`compiler/fir/fir-deserialization/src/org/jetbrains/kotlin/fir/deserialization/FirTypeDeserializer.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/fir-deserialization/src/org/jetbrains/kotlin/fir/deserialization/FirTypeDeserializer.kt) (search for the function name) |
| Type predicates / converters live in `FunctionalTypeUtils.kt` | [`compiler/fir/providers/src/org/jetbrains/kotlin/fir/types/FunctionalTypeUtils.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/types/FunctionalTypeUtils.kt) (`isBasicFunctionType`, `customFunctionTypeToSimpleFunctionType`, `createFunctionTypeWithNewKind`) |

## Test data

| Claim | Citation |
|---|---|
| Box test for `@MyInlineable () -> Unit` round-trip (FIR + IR) | [`plugins/plugin-sandbox/testData/box/inlineableFunction.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/plugins/plugin-sandbox/testData/box/inlineableFunction.kt) |

## Notes

The IR-side requirement is mandatory, not optional — the FIR side resolves your kind to `some.MyKindN`, but the JVM bytecode generator has no idea what that class is. The reference IR transformer in `PluginFunctionKindsTransformer.kt` is ~150 lines and shows the pattern: visit `IrFunctionExpression` / `IrFunctionReference` / type usages, rewrite the classifier symbol from `some.MyKindN` to `kotlin.FunctionN` (or `kotlin.reflect.KFunctionN` for reflect kinds), and add the marker annotation to the lambda's IR function.
