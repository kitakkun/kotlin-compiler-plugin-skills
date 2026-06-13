---
name: ir-synthetic-class-generation
description: Build entirely new IR classes (with members, supertypes, and constructors) at IR-generation time — typically as the IR-side counterpart of FIR-generated declarations whose backend implementation needs to be synthesised. Covers IrFactory, IrBuilder helpers, declaration parents, and the FIR↔IR pairing pattern. Read ir-plugincontext-usage and fir-declaration-generation-extension first. NOT for modifying existing functions (see ir-body-modification) or for replacing calls (see ir-call-rewriting). If the user references `createParameterDeclarations()` or `registerClassAsMetadataVisible` (both removed/non-existent), ALSO Read CHANGES.md in this skill's directory.
---

# IR Synthetic Class Generation

Two scenarios call for IR-side class synthesis:

1. **FIR-paired** — `FirDeclarationGenerationExtension` declared a class; the IR side fills in member bodies and possibly synthesises companion methods. Source-visible from the same module; metadata-visible to downstream modules. This is the dominant pattern; see `kotlin/plugins/kotlinx-serialization/`, `kotlin/plugins/parcelize/`, and `kotlin/plugins/atomicfu/` for production references.
2. **IR-only** — the entire class doesn't exist at the FIR level; the IR plugin invents it during its `generate(...)` pass. Source-invisible from the same module (FIR has no record of it) but the bytecode is real. Used when a plugin needs runtime helpers that user code never refers to directly — e.g. injected book-keeping types or compiler-internal trampolines.

Pattern 1 is much more common. Pattern 2 has more limitations (no source visibility, no IDE awareness, no checker support).

## FIR-paired pattern (recommended)

The FIR generator declares the shape (class, supertypes, member signatures) using `*BuildingContext` helpers. The IR generator then visits each declaration and fills in member bodies based on `IrDeclarationOrigin.GeneratedByPlugin(myKey)`.

This skill assumes the FIR side is already wired (see [`fir-declaration-generation-extension`](../fir-declaration-generation-extension/guide.md)). At the IR level you walk:

```kotlin
override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    moduleFragment.transformChildrenVoid(object : IrElementTransformerVoidWithContext() {
        override fun visitClassNew(declaration: IrClass): IrStatement {
            val processed = super.visitClassNew(declaration) as IrClass
            val origin = processed.origin
            if (origin !is IrDeclarationOrigin.GeneratedByPlugin) return processed
            if (origin.pluginKey != MyGeneratedKey) return processed
            // Fill in synthesised member bodies via ir-body-modification patterns.
            return processed
        }
    })
}
```

`visitClassNew` is the override hook on `IrElementTransformerVoidWithContext` (in `org.jetbrains.kotlin.backend.common`); the plain `IrElementTransformerVoid` only exposes `visitClass`.

Once you've located the FIR-generated class, all the heavy lifting (fields, constructors, member bodies) follows the patterns in [`ir-body-modification`](../ir-body-modification/guide.md) and [`ir-call-rewriting`](../ir-call-rewriting/guide.md). **The recommendation is: declare in FIR, fill bodies in IR.** Don't fight the framework.

## IR-only pattern (last resort)

If you genuinely need an IR-only class — no FIR declaration, no source visibility — the steps are:

### 1. Construct the IrClass

```kotlin
val pluginContext: IrPluginContext = ...
val factory = pluginContext.irFactory

val newClass = factory.buildClass {
    name = Name.identifier("MyGeneratedClass")
    kind = ClassKind.CLASS
    visibility = DescriptorVisibilities.PUBLIC
    modality = Modality.FINAL
    origin = IrDeclarationOrigin.DEFINED  // or your custom origin
}.apply {
    superTypes = listOf(pluginContext.irBuiltIns.anyType)
    parent = enclosingFile  // or enclosingClass
    createThisReceiverParameter()  // adds the implicit dispatch receiver. (Older `createParameterDeclarations()` is deprecated since 2.1.20.)
}
```

`pluginContext.irFactory` is the modern factory. The `buildClass { ... }` DSL is in `org.jetbrains.kotlin.ir.builders.declarations`.

### 2. Add a constructor

```kotlin
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.constructors            // exposes IrClass.constructors as a Sequence
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities

@OptIn(UnsafeDuringIrConstructionAPI::class)   // anyClass.owner.constructors below requires this
val ctor = newClass.addConstructor {
    isPrimary = true
    visibility = DescriptorVisibilities.PUBLIC
    // returnType is auto-set to newClass.defaultType by addConstructor — no need to set explicitly
}.apply {
    val builder = DeclarationIrBuilder(pluginContext, symbol)
    body = builder.irBlockBody {
        +irDelegatingConstructorCall(pluginContext.irBuiltIns.anyClass.owner.constructors.single())
        +IrInstanceInitializerCallImpl(
            startOffset, endOffset,
            type = pluginContext.irBuiltIns.unitType,
            classSymbol = newClass.symbol,
        )
    }
}
```

`addConstructor { ... }` extends `IrClass` (in `org.jetbrains.kotlin.ir.builders.declarations`). The body must call the superclass constructor (`irDelegatingConstructorCall`) and then run the instance initialiser. Reading `anyClass.owner` is gated by `@OptIn(UnsafeDuringIrConstructionAPI::class)` (as annotated above; the same opt-in is documented in [`ir-plugincontext-usage`](../ir-plugincontext-usage/guide.md)).

### 3. Add fields

```kotlin
val nameField = newClass.addProperty {
    name = Name.identifier("name")
    visibility = DescriptorVisibilities.PUBLIC
    modality = Modality.FINAL
}.apply {
    backingField = factory.buildField {
        name = this@apply.name
        type = pluginContext.irBuiltIns.stringType
        visibility = DescriptorVisibilities.PRIVATE
    }.apply { parent = newClass }
    addDefaultGetter(newClass, pluginContext.irBuiltIns)
}
```

Properties typically need a backing field plus a getter (and a setter for `var`s). `addDefaultGetter` is a helper in `org.jetbrains.kotlin.ir.builders.declarations`.

### 4. Add member functions

```kotlin
val toStringFn = newClass.addFunction {
    name = Name.identifier("toString")
    visibility = DescriptorVisibilities.PUBLIC
    modality = Modality.OPEN
    returnType = pluginContext.irBuiltIns.stringType
}.apply {
    overriddenSymbols = listOf(/* Any.toString symbol */)
    val builder = DeclarationIrBuilder(pluginContext, symbol)
    body = builder.irBlockBody {
        +irReturn(irString("MyGeneratedClass instance"))
    }
}
```

`addFunction { ... }` is also in `org.jetbrains.kotlin.ir.builders.declarations`.

### 5. Insert into a parent

```kotlin
// Add to the file's declarations:
enclosingFile.declarations.add(newClass)
newClass.parent = enclosingFile

// Or add as a nested class:
existingClass.declarations.add(newClass)
newClass.parent = existingClass
```

`parent` must be set correctly or IR validation fails. The parent contributes to the `kotlinFqName` of the new class.

### 6. Make members visible to metadata (K2 only)

For external module consumption, register the **functions and constructors** (the registrar does not currently support whole-class registration):

```kotlin
val registrar = pluginContext.metadataDeclarationRegistrar
newClass.functions.forEach { fn ->
    if (fn is IrSimpleFunction) registrar.registerFunctionAsMetadataVisible(fn)
}
newClass.constructors.forEach { ctor ->
    registrar.registerConstructorAsMetadataVisible(ctor)
}

// To attach annotations onto a generated declaration.
// Kotlin 2.4.0: the list element type is IrAnnotation, not IrConstructorCall.
// Build each with DeclarationIrBuilder.irAnnotation(ctorSymbol, typeArguments)
// (irCallConstructor(...) still returns a plain IrConstructorCall — wrong type here).
registrar.addMetadataVisibleAnnotationsToElement(declaration, listOfAnnotations) // List<IrAnnotation>
```

Available `IrGeneratedDeclarationsRegistrar` methods:

| Method | Purpose |
|---|---|
| `registerFunctionAsMetadataVisible(IrSimpleFunction)` | Make a generated function visible to consumers' metadata |
| `registerConstructorAsMetadataVisible(IrConstructor)` | Same, for constructors |
| `addMetadataVisibleAnnotationsToElement(IrDeclaration, List<IrAnnotation>)` | Attach annotations to be saved into metadata. **Kotlin 2.4.0: element type is `IrAnnotation`** (was `IrConstructorCall` through 2.3.x); build with `DeclarationIrBuilder.irAnnotation(...)` |
| `getMetadataVisibleAnnotationsForElement(IrDeclaration): MutableList<IrAnnotation>` | Read back the metadata-visible annotations that were attached (`IrAnnotation` since 2.4.0) |
| `addCustomMetadataExtension(declaration, id, data)` | Attach raw bytes under a custom extension id (consumed by your own paired FIR-resolver / IR-extension on the read side) |
| `getCustomMetadataExtension(declaration, id): ByteArray?` | Read back the bytes attached via `addCustomMetadataExtension` |

There is currently **no** `registerClassAsMetadataVisible` or `registerPropertyAsMetadataVisible` (KT-63881 tracks the property variant). For whole-class metadata visibility, register every function and constructor individually. Most plugins (kotlinx-serialization, plugin-sandbox) take this approach.

### 7. Pattern: FIR-generated companion + IR-only metadata-visible factory

A common shape is "the user's class has a companion (synthesised by FIR if absent), and the plugin attaches a factory method to that companion that's IR-only but cross-module visible". Worked end-to-end:

**FIR side** (in your `FirDeclarationGenerationExtension` — the companion is declared so the IR side has somewhere to attach to):

```kotlin
override fun getNestedClassifiersNames(classSymbol: FirClassSymbol<*>, context: NestedClassGenerationContext) =
    if (matchesMarker(classSymbol)) setOf(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) else emptySet()

override fun generateNestedClassLikeDeclaration(owner, name, context) =
    if (name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT && matchesMarker(owner))
        createCompanionObject(owner, MyKey).symbol else null

override fun getCallableNamesForClass(classSymbol, context) =
    if (classSymbol.origin is FirDeclarationOrigin.Plugin && /* it's our companion */)
        setOf(SpecialNames.INIT) else emptySet()    // we only want the constructor; parse() comes from IR

override fun generateConstructors(context) = listOf(createDefaultPrivateConstructor(context.owner, MyKey).symbol)
```

Note: FIR only declares the companion + its constructor. **`parse()` is NOT in `getCallableNamesForClass`** — that's the whole point. If FIR declared it, the source within the same module could call it (defeating the IR-only design).

**IR side** (find the synthesised companion, attach the factory, register as metadata-visible):

```kotlin
override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    moduleFragment.acceptChildrenVoid(object : IrVisitorVoid() {
        override fun visitElement(element: IrElement) = element.acceptChildrenVoid(this)

        override fun visitClass(declaration: IrClass) {
            super.visitClass(declaration)
            if (!declaration.hasAnnotation(MARKER_FQ)) return
            val companion = declaration.companionObject() ?: return  // FIR generated this above

            val parseFun = pluginContext.irFactory.buildFun {
                name = Name.identifier("parse")
                returnType = declaration.defaultType.makeNullable()
                visibility = DescriptorVisibilities.PUBLIC
                modality = Modality.FINAL
                origin = IrDeclarationOrigin.GeneratedByPlugin(MyKey)
            }.apply {
                parent = companion
                addValueParameter("input", pluginContext.irBuiltIns.stringType)
                body = /* irBlockBody { ... } that parses + constructs */
            }
            companion.declarations += parseFun

            // CRITICAL: without this, downstream modules can't see parse() at all.
            pluginContext.metadataDeclarationRegistrar.registerFunctionAsMetadataVisible(parseFun)
        }
    })
}
```

The two sides coordinate via:
- The same `MyKey: GeneratedDeclarationKey` on both the FIR companion and the IR `parse` (so the IR side can identify "this is OUR companion and we're the one attaching members").
- `companionObject()` extension to find the companion the FIR side just generated.
- `metadataDeclarationRegistrar.registerFunctionAsMetadataVisible(parseFun)` to write `parse` into the module's `.kotlin_metadata` so consumers' FIR resolution sees it.

Result: source within the same module compiling `MyClass.parse(...)` fails with `Unresolved reference 'parse'` (FIR doesn't see the IR-added member). Source in a downstream module compiling `MyClass.parse(...)` succeeds (its FIR reads `parse` from the imported metadata). This is the canonical "IR-only metadata-visible" idiom.

## Common gotchas

### `parent` not set

`IrValidator` complains: `Declaration's parent is not set`. Always set `parent` on every freshly-built declaration. The DSL helpers don't infer it.

### Constructor body missing `IrInstanceInitializerCall`

A constructor must call its superclass constructor *and* invoke the instance initialiser (which runs property initialisers, init blocks, etc.). Skipping the initialiser leaves fields uninitialised at runtime.

### `createThisReceiverParameter()` forgotten

Member functions implicitly take the dispatch receiver as their first parameter. The `createThisReceiverParameter()` method on `IrClass` sets this up. Forgetting it makes member calls fail with `dispatchReceiver is null`. (The older `createParameterDeclarations()` is deprecated since Kotlin 2.1.20 — replace any references in pre-2.1.20 tutorials.)

### `IrClass.addFunction { ... }` produces a JVM-static method when adding to an existing class

The `addFunction { builder }` overload in `org.jetbrains.kotlin.ir.builders.declarations` does **not** add a dispatch receiver — the resulting `IrSimpleFunction` has no receiver parameter, so JVM codegen emits a `static` method on the enclosing class. If you also call `pluginContext.metadataDeclarationRegistrar.registerFunctionAsMetadataVisible(...)`, the metadata records it as a *member* function while the bytecode is *static* — downstream callers crash at runtime with `IncompatibleClassChangeError: Expected non-static method`.

The fix is to attach a dispatch receiver explicitly when building the function:

```kotlin
import org.jetbrains.kotlin.ir.util.createDispatchReceiverParameterWithClassParent

val greet = ownerClass.addFunction {
    name = Name.identifier("greet")
    returnType = pluginContext.irBuiltIns.stringType
    modality = Modality.FINAL
    visibility = DescriptorVisibilities.PUBLIC
}.apply {
    parameters = listOf(createDispatchReceiverParameterWithClassParent())
}
pluginContext.metadataDeclarationRegistrar.registerFunctionAsMetadataVisible(greet)
```

kotlinx-serialization's `IrBuilderWithPluginContext` documents this inline. Member synthesis on existing classes should always set `parameters` explicitly.

### `returnType = X` set before parent → wrong type

When using the DSL builders, set `parent` before any code that uses `defaultType`. Otherwise `defaultType` resolves against a wrong parent and your types reference the wrong scope.

### `overriddenSymbols` empty when overriding

If your synthetic function overrides `Any.toString`, `kotlin.Comparable.compareTo`, etc., set `overriddenSymbols = listOf(theirSymbol)`. Forgetting this makes the override invisible — at runtime you'll get the inherited implementation.

### Nested class can't see outer's fields

Inner classes (`isInner = true`) capture the outer instance; non-inner classes don't. If your generated class accesses `this@Outer`, mark it `isInner` and arrange for the outer reference to be passed to the constructor.

### `IrSyntheticBody` for data class auto-generated members

Don't try to compete with the data class lowering. If your class needs `equals`/`hashCode`/`toString`, mark it `isData = true` (when applicable) or generate them at FIR level so the data class lowering runs.

### Companion object placement

`IrClass.companionObject()` (extension function) returns the companion if any. To attach a generated companion: build it as a regular `IrClass` with `isCompanion = true`, set `parent = ownerClass`, and add to `ownerClass.declarations`.

## Relation to other extensions

- **Declare the class shape at frontend level** → [`fir-declaration-generation-extension`](../fir-declaration-generation-extension/guide.md)
- **Add bodies to functions of the generated class** → [`ir-body-modification`](../ir-body-modification/guide.md)
- **Look up the symbols of supertypes / referenced classes** → [`ir-plugincontext-usage`](../ir-plugincontext-usage/guide.md)
- **Mark the class as metadata-visible to downstream modules** → covered above with `metadataDeclarationRegistrar`

## What this skill does NOT cover

- The full `IrFactory` API for every IR node type — see `kotlin/compiler/ir/ir.tree/src/.../IrFactory.kt`
- Generic type parameters on synthetic classes (the patterns extend; consult `org.jetbrains.kotlin.ir.builders.declarations.addTypeParameter`)
- Inline class / value class generation (additional flag handling required)
- `external` / `expect` / `actual` synthetic declarations (multiplatform-specific; see `kotlin/plugins/kotlinx-serialization/.../FirSerializableIrIntrinsicSupport.kt` for examples)
