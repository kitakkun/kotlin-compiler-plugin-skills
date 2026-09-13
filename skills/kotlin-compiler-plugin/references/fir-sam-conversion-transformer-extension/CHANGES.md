# Changes affecting this skill

API migrations relevant to writing `FirSamConversionTransformerExtension`. This skill targets the **current stable Kotlin** (2.4.20).

## Kotlin 2.3.0 → 2.3.20

### `getCustomFunctionTypeForSamConversion` parameter type renamed

The FIR node `FirSimpleFunction` was renamed to `FirNamedFunction` (commit `2e1f4f401c03`, first shipped in **Kotlin 2.3.20**). The override signature on `FirSamConversionTransformerExtension` reflects the rename:

```kotlin
// Kotlin 2.3.0 and earlier
override fun getCustomFunctionTypeForSamConversion(function: FirSimpleFunction): ConeLookupTagBasedType?

// Kotlin 2.3.20+
override fun getCustomFunctionTypeForSamConversion(function: FirNamedFunction): ConeLookupTagBasedType?
```

`FirSimpleFunction` survives as a `typealias` to `FirNamedFunction` for short-term source compatibility, but the override's parameter must use the new name — using `FirSimpleFunction` produces "is not abstract and does not implement abstract member 'getCustomFunctionTypeForSamConversion'" because the typealias resolution doesn't help when overriding an abstract whose declared parameter is the new name.

**Migration**: rename `FirSimpleFunction` → `FirNamedFunction` in your override signature and any related local variables.

## Kotlin 2.0.0 → 2.3.0

No breaking API changes. The extension was stable since K2 introduction (~Kotlin 1.7). The `firstNotNullOfOrNull` invocation pattern in `FirSamResolver.resolveFunctionTypeIfSamInterface` and the lack of a priority/ambiguity diagnostic have been constants throughout this range.
