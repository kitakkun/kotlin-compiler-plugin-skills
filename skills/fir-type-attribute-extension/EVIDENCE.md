# Evidence — fir-type-attribute-extension

Primary-source citations for the non-obvious claims in `SKILL.md`. All paths relative to `kotlin/` root unless otherwise stated.

## API surface

| Claim | Citation |
|---|---|
| `FirTypeAttributeExtension` has exactly two abstract methods: `extractAttributeFromAnnotation` and `convertAttributeToAnnotation` | [`compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirTypeAttributeExtension.kt:31,37`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirTypeAttributeExtension.kt#L31) |
| KDoc explicitly tells plugin authors to declare an accessor: `val ConeAttributes.myAttribute: MyAttribute? by ConeAttributes.attributeAccessor<MyAttribute>()` | same file, KDoc at lines 14-19 |
| KDoc on `convertAttributeToAnnotation` warns: "Please don't convert attributes which you didn't create. If [attribute] came from compiler or another plugin just return null" | same file, lines 33-36 |
| Service accessor `val FirExtensionService.typeAttributeExtensions: List<FirTypeAttributeExtension>` exists | same file, line 42 |

## ConeAttribute base class

| Claim | Citation |
|---|---|
| `union`, `intersect`, `add`, `isSubtypeOf`, `key`, `keepInInferredDeclarationType` are abstract on `ConeAttribute<T>` | [`compiler/fir/cones/src/org/jetbrains/kotlin/fir/types/ConeAttributes.kt:16-46`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/cones/src/org/jetbrains/kotlin/fir/types/ConeAttributes.kt#L16-L46) |
| `add` is documented for typealias expansion: KDoc shows `typealias C = @SomeAttribute(2) B` stacking with `B = @SomeAttribute(1) A` resolving to `@SomeAttribute(1) + @SomeAttribute(2)` | same file, KDoc at `add` |
| `keepInInferredDeclarationType` controls survival through declaration-type approximation | same file, KDoc on `keepInInferredDeclarationType`; usage at [`compiler/fir/resolve/src/.../body/resolve/DeclarationApproximationUtils.kt`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/transformers/body/resolve/DeclarationApproximationUtils.kt) (`UnnecessaryAttributesRemover`) and `ConeAttributes.filterNecessaryToKeep()` at `ConeAttributes.kt:146-149` |
| `ConeAttributes.attributeAccessor<T>()` factory generates a delegated property reading from the type-keyed array | `ConeAttributes.kt:77-80` |

## Reference implementation in plugin-sandbox

| Claim | Citation |
|---|---|
| Plugin-sandbox provides `FirNumberSignAttributeExtension` mapping `@Positive` / `@Negative` to `ConeNumberSignAttribute` | [`plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/types/FirNumberSignAttributeExtension.kt:1-53`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/types/FirNumberSignAttributeExtension.kt#L1-L53) |
| `ConeNumberSignAttribute` caches `Positive` and `Negative` as singletons via `fromSign()` | [`plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/types/ConeNumberSignAttribute.kt:13-23`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/types/ConeNumberSignAttribute.kt#L13-L23) |
| `ConeNumberSignAttribute` returns `null` from `combine` when signs disagree | same file, `Sign.Positive.combine` / `Sign.Negative.combine` at lines 26-40 |
| `ConeNumberSignAttribute.keepInInferredDeclarationType = true` | same file, lines 71-72 |
| Accessor declared: `val ConeAttributes.numberSign: ConeNumberSignAttribute? by ConeAttributes.attributeAccessor<ConeNumberSignAttribute>()` | same file, line 75 |
| `SignedNumberCallChecker` enforces parameter-attribute vs argument-attribute matching at call sites | [`plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/checkers/SignedNumberCallChecker.kt:1-40`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/checkers/SignedNumberCallChecker.kt#L1-L40) |
| Sandbox registrar registers `+::FirNumberSignAttributeExtension` | [`plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/FirPluginPrototypeExtensionRegistrar.kt:31`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/FirPluginPrototypeExtensionRegistrar.kt#L31) |

## Round-trip mechanics

| Claim | Citation |
|---|---|
| `extractAttributeFromAnnotation` is invoked from `computeTypeAttributes` during analysis-time annotation processing | [`compiler/fir/providers/src/org/jetbrains/kotlin/fir/CopyUtils.kt:56-121`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/providers/src/org/jetbrains/kotlin/fir/CopyUtils.kt#L56-L121) |
| `convertAttributeToAnnotation` is invoked during metadata serialization in `FirElementSerializer` | [`compiler/fir/fir-serialization/src/org/jetbrains/kotlin/fir/serialization/FirElementSerializer.kt`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/fir-serialization/src/org/jetbrains/kotlin/fir/serialization/FirElementSerializer.kt) (grep `convertAttributeToAnnotation`) |
| Both extension methods iterate `session.extensionService.typeAttributeExtensions` with `firstNotNullOfOrNull` semantics | same files |

## Wiring

| Claim | Citation |
|---|---|
| `FirTypeAttributeExtension` is in `AVAILABLE_EXTENSIONS` | [`compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt:28`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt#L28) |
| `+::Constructor` operator is defined for `(FirSession) -> FirTypeAttributeExtension` | same file, lines 165-168 |
| Type-attribute extensions are also enabled for library FIR sessions | same file, line 46 (in the library-allowed list) |

## Test data

| Claim | Citation |
|---|---|
| Plugin-sandbox has a checkers test exercising the round-trip with `<!ILLEGAL_NUMBER_SIGN!>` markers | [`plugins/plugin-sandbox/testData/diagnostics/checkers/signedNumbersCheckers.kt:1-32`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/plugin-sandbox/testData/diagnostics/checkers/signedNumbersCheckers.kt#L1-L32) |
| Common-supertype calculation in generic `select<T>(x, y)` does NOT propagate plugin attributes (limitation noted in test data) | same file, lines 28-29 with the comment "Should be ok, but currently attributes are not passed through common super type calculation" |

## Notes

The path prefix in citations is the kotlin-lang clone root; in the user's environment that's `/Users/kitakkun/Documents/GitHub/kotlin-lang/`. Files are stable across recent Kotlin minors (2.0+) and the API has not had breaking changes since `FirTypeAttributeExtension` was introduced in early K2 development (~2021).
