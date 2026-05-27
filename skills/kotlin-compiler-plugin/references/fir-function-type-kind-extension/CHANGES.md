# Changes affecting this skill

API migrations relevant to writing `FirFunctionTypeKindExtension`. This skill targets the **current stable Kotlin** (2.3.x).

## Kotlin 2.1.10 → 2.1.20

### `isInlineable` parameter added to `FunctionTypeKind` constructor

Commit `cfe1c161e1cc` (October 2024) introduced an `isInlineable: Boolean` parameter to the `FunctionTypeKind` constructor — first shipped in **Kotlin 2.1.20**. Plugin code that targets earlier compilers passed only `(packageFqName, classNamePrefix, annotationOnInvokeClassId, isReflectType)`; on 2.1.20+, the constructor requires `isInlineable` as well.

```kotlin
// Kotlin 2.1.10 and earlier
object MyKind : FunctionTypeKind(
    packageFqName = ...,
    classNamePrefix = ...,
    annotationOnInvokeClassId = ...,
    isReflectType = false,
)

// Kotlin 2.1.20+
object MyKind : FunctionTypeKind(
    packageFqName = ...,
    classNamePrefix = ...,
    annotationOnInvokeClassId = ...,
    isReflectType = false,
    isInlineable = true,        // new mandatory parameter
)
```

**Migration**: add `isInlineable = <true|false>` to every plugin-defined kind. Reflect kinds typically use `false`; non-reflect kinds use `true` if the runtime can inline functions of this type, `false` otherwise. The compiler uses this to gate diagnostics like `DECLARATION_CANT_BE_INLINED`.

## Kotlin 2.1.21 → 2.2.0

### `supportsConversionFromSimpleFunctionType` property added

Commit `00829174f651` (March 2025) added the `supportsConversionFromSimpleFunctionType: Boolean` property (defaulting to `true`) to `FunctionTypeKind`, first shipped in **Kotlin 2.2.0**. When `false`, a plain `() -> Unit` lambda cannot be implicitly coerced to your `MyKind0<Unit>` type — the user must explicitly annotate the lambda with the kind's marker.

```kotlin
object MyKind : FunctionTypeKind(...) {
    override val supportsConversionFromSimpleFunctionType: Boolean = false  // new in 2.2.0
}
```

This is the property Compose uses to require `@Composable` at every site rather than letting plain lambdas convert silently. **No migration is needed** if the default `true` works for your kind; opt in to `false` only when you need to enforce the marker annotation.

## Kotlin 2.0.0 → 2.1.10

No breaking API changes affecting plugin authors. The extension was stable since K2 introduction.

## Notes

The IR-side responsibility (custom kinds must be lowered by an `IrGenerationExtension` before the JVM bytecode generator sees them) has been a constant throughout the 2.0.0 → 2.3.x range. The IR transformer pattern in `plugin-sandbox/src/.../ir/PluginFunctionKindsTransformer.kt` is the canonical template for all versions.
