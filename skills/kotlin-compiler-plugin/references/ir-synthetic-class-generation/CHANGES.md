# Changes affecting this skill

API migrations relevant to generating synthetic IR classes. This skill targets the **current stable Kotlin** (2.4.20).

## Kotlin 2.4.10 → 2.4.20: `registerClassAsMetadataVisible` and `registerPropertyAsMetadataVisible` added

`IrGeneratedDeclarationsRegistrar` (reached via `pluginContext.metadataDeclarationRegistrar`) gained two new abstract members ([`compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt:26-27`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt#L26-L27)):

```kotlin
abstract fun registerPropertyAsMetadataVisible(irProperty: IrProperty)   // KT-63881, replaces the old TODO
abstract fun registerClassAsMetadataVisible(irClass: IrClass)            // KT-79565 (parts 1-3: top-level, nested, inner)
```

- `registerPropertyAsMetadataVisible` requires the property to have a getter (`error("Property without getter is not supported")`, [`compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrIrGeneratedDeclarationsRegistrar.kt:176-177`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrIrGeneratedDeclarationsRegistrar.kt#L176-L177)); local properties are skipped.
- `registerClassAsMetadataVisible` builds a FIR class for the IR class and then recursively registers its constructors, non-accessor functions, properties, and nested/inner classes, skipping `FAKE_OVERRIDE` members ([`compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrIrGeneratedDeclarationsRegistrar.kt:421-437`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrIrGeneratedDeclarationsRegistrar.kt#L421-L437)). Enum classes throw ([`compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrIrGeneratedDeclarationsRegistrar.kt:356-358`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrIrGeneratedDeclarationsRegistrar.kt#L356-L358)). Sealed subclasses are recorded from `irClass.sealedSubclasses`.
- If you subclass `IrGeneratedDeclarationsRegistrar` yourself (test doubles, custom pipelines) you must now implement both new members; the built-in non-FIR `IrPluginContextImpl` registrar implements them as no-ops.

**Before (2.4.10 and older)** — properties could not be registered at all, classes only member by member:

```kotlin
val registrar = pluginContext.metadataDeclarationRegistrar
newClass.functions.forEach { fn -> if (fn is IrSimpleFunction) registrar.registerFunctionAsMetadataVisible(fn) }
newClass.constructors.forEach { registrar.registerConstructorAsMetadataVisible(it) }
// properties: silently missing from downstream metadata
```

**After (2.4.20)**:

```kotlin
enclosingFile.declarations += newClass          // parent, superTypes, members, sealedSubclasses all set
pluginContext.metadataDeclarationRegistrar.registerClassAsMetadataVisible(newClass)
```

**Migration**: for whole synthetic classes, replace the per-member loop with one `registerClassAsMetadataVisible` call on the outermost class (do not also register the members individually). For a single member attached to an existing source class, keep `registerFunctionAsMetadataVisible` / `registerConstructorAsMetadataVisible`, and use `registerPropertyAsMetadataVisible` for properties. Plugins that must compile against both 2.4.10 and 2.4.20 need a version-split (see `multi-version-kotlin-support`), since the members do not exist on the older abstract class. Note that the guide's earlier claim "there is no `registerClassAsMetadataVisible`" (below, under 2.2 → 2.3) is only true through 2.4.10.

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

### `IrGeneratedDeclarationsRegistrar` does NOT have `registerClassAsMetadataVisible` (true through 2.4.10 — added in 2.4.20, see the top of this file)

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

(KT-63881 tracked adding `registerPropertyAsMetadataVisible`; both it and `registerClassAsMetadataVisible` landed in 2.4.20.)

**Migration** (for Kotlin ≤ 2.4.10): register every member individually:

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
