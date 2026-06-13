# Changes affecting this skill

API migrations relevant to generating synthetic IR classes. This skill targets the **current stable Kotlin** (2.4.0).

## Kotlin 2.3 → 2.4: metadata-annotation API uses `IrAnnotation`

`IrGeneratedDeclarationsRegistrar` (reached via `pluginContext.metadataDeclarationRegistrar`) switched its annotation element type from `IrConstructorCall` to the new `IrAnnotation` (which is `IrConstructorCall`'s subclass — `IrAnnotation : IrConstructorCall()`):

- `addMetadataVisibleAnnotationsToElement(declaration, annotations: List<IrAnnotation>)` (and its `vararg` overload) — was `List<IrConstructorCall>`.
- `getMetadataVisibleAnnotationsForElement(declaration): MutableList<IrAnnotation>` — was `MutableList<IrConstructorCall>`.

Build the annotations with `DeclarationIrBuilder.irAnnotation(ctorSymbol, typeArguments)` (returns `IrAnnotation`) rather than `irCallConstructor(...)` (returns a plain `IrConstructorCall`, which no longer type-checks here). `registerFunctionAsMetadataVisible` / `registerConstructorAsMetadataVisible` are unchanged.

## Kotlin 2.0 → 2.1.20

### `createParameterDeclarations()` deprecated → `createThisReceiverParameter()`

Older code initialised the implicit dispatch-receiver parameter on a freshly built `IrClass`:

```kotlin
val newClass = factory.buildClass { ... }.apply {
    parent = enclosingFile
    createParameterDeclarations()
}
```

Since 2.1.20, `createParameterDeclarations()` carries `@DeprecatedForRemovalCompilerApi(_2_1_20, replaceWith = "createThisReceiverParameter()")`. The replacement covers the same need — it adds the implicit `this` receiver to the new class.

**Migration**:

```kotlin
val newClass = factory.buildClass { ... }.apply {
    parent = enclosingFile
    createThisReceiverParameter()
}
```

## Kotlin 2.2 → 2.3

### `IrGeneratedDeclarationsRegistrar` does NOT have `registerClassAsMetadataVisible`

Some early tutorials suggested:

```kotlin
pluginContext.metadataDeclarationRegistrar.registerClassAsMetadataVisible(newClass)  // doesn't exist
```

The actual `IrGeneratedDeclarationsRegistrar` API (verified against source) has only:
- `registerFunctionAsMetadataVisible(IrSimpleFunction)`
- `registerConstructorAsMetadataVisible(IrConstructor)`
- `getMetadataVisibleAnnotationsForElement(IrDeclaration): MutableList<IrConstructorCall>`
- `addMetadataVisibleAnnotationsToElement(IrDeclaration, List<IrConstructorCall>)`  *(plus a `vararg IrConstructorCall` convenience overload)*
- `addCustomMetadataExtension(...)`

(KT-63881 tracks adding `registerPropertyAsMetadataVisible`. There is currently no whole-class registration method.)

**Migration**: register every member individually:

```kotlin
val registrar = pluginContext.metadataDeclarationRegistrar
newClass.functions.forEach { fn ->
    if (fn is IrSimpleFunction) registrar.registerFunctionAsMetadataVisible(fn)
}
newClass.constructors.forEach { registrar.registerConstructorAsMetadataVisible(it) }
```

### `IrInstanceInitializerCallImpl` argument order

There are TWO entry points and they take the last two arguments **in opposite order**:

| Entry point | Signature |
|---|---|
| Internal generated constructor (`IrInstanceInitializerCallImpl.kt:19-25`) | `(startOffset, endOffset, type: IrType, classSymbol: IrClassSymbol)` |
| Public factory function (`builders.kt:515-526`, the one you actually call) | `IrInstanceInitializerCallImpl(startOffset, endOffset, classSymbol: IrClassSymbol, type: IrType)` |

**Always use named arguments** so you don't accidentally rely on positional order — both entry points compile against `(symbol, type)` confused. The skill's example uses `type = ..., classSymbol = ...` named parameters which works correctly with the public factory.

### `addDefaultGetter` lives in `org.jetbrains.kotlin.ir.builders.declarations`

Not in `org.jetbrains.kotlin.ir.util` where readers might first look.
