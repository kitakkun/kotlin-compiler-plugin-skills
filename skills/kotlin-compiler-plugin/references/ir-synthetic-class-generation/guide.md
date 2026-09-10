---
name: ir-synthetic-class-generation
description: Build entirely new IR classes (with members, supertypes, and constructors) at IR-generation time — typically as the IR-side counterpart of FIR-generated declarations whose backend implementation needs to be synthesised. Covers IrFactory, IrBuilder helpers, declaration parents, and the FIR↔IR pairing pattern. Read ir-plugincontext-usage and fir-declaration-generation-extension first. NOT for modifying existing functions (see ir-body-modification) or for replacing calls (see ir-call-rewriting). If the user references `createParameterDeclarations()` (deprecated for removal) or calls `registerClassAsMetadataVisible` / `registerPropertyAsMetadataVisible` while targeting Kotlin older than 2.4.20 (where those did not exist), ALSO Read CHANGES.md in this skill's directory.
---

# IR Synthetic Class Generation

Two scenarios call for IR-side class synthesis:

1. **FIR-paired** — `FirDeclarationGenerationExtension` declared a class; the IR side fills in member bodies and possibly synthesises companion methods. Source-visible from the same module; metadata-visible to downstream modules. This is the dominant pattern; see `kotlin/plugins/kotlinx-serialization/`, `kotlin/plugins/parcelize/`, and `kotlin/plugins/atomicfu/` for production references.
2. **IR-only** — the entire class doesn't exist at the FIR level; the IR plugin invents it during its `generate(...)` pass. Source-invisible *within the defining module* (its FIR has no record of it) but the bytecode is real — and since Kotlin 2.4.20 a single `registerClassAsMetadataVisible` call (step 6) makes the class, its constructor, properties, functions, and nested classes resolvable from downstream modules' source and IDE exactly like a library class. Used when a plugin needs runtime helpers that user code never refers to directly — e.g. injected book-keeping types or compiler-internal trampolines — or when the consumer is always another module.

Pattern 1 is much more common. Pattern 2 has more limitations: inside the defining module there is no source visibility, no IDE awareness, and no checker support, and on Kotlin 2.4.10 and older that is also true for every other module. On 2.4.20+ downstream modules see the class once it is registered as metadata-visible; only the module that generates it never does.

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

**Order note:** if the constructor initializes a field — the normal shape for a holder / data-carrier class — build the property and its backing field (step 3) *before* the constructor, because the constructor body references the `IrField`. The snippet below assumes `nameField` from step 3 already exists; for a no-arg class drop the `addValueParameter` and `irSetField` lines.

```kotlin
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.constructors            // exposes IrClass.constructors as a Sequence
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities

@OptIn(UnsafeDuringIrConstructionAPI::class)   // anyClass.owner.constructors below requires this
val ctor = newClass.addConstructor {
    isPrimary = true
    visibility = DescriptorVisibilities.PUBLIC
    // returnType is auto-set to newClass.defaultType by addConstructor — no need to set explicitly
}.apply {
    val nameParam = addValueParameter("name", pluginContext.irBuiltIns.stringType)
    val builder = DeclarationIrBuilder(pluginContext, symbol)
    body = builder.irBlockBody {
        +irDelegatingConstructorCall(pluginContext.irBuiltIns.anyClass.owner.constructors.single())
        +IrInstanceInitializerCallImpl(
            startOffset, endOffset,
            type = pluginContext.irBuiltIns.unitType,
            classSymbol = newClass.symbol,
        )
        // Assign the constructor parameter to the backing field built in step 3.
        +irSetField(irGet(newClass.thisReceiver!!), nameField, irGet(nameParam))
    }
}
```

`addConstructor { ... }` and `addValueParameter(name, type)` extend `IrClass` / `IrFunction` (both in `org.jetbrains.kotlin.ir.builders.declarations`); `irSetField(receiver, field, value)` and `irGet(value)` are `IrBuilder` extensions in `org.jetbrains.kotlin.ir.builders`. The body must call the superclass constructor (`irDelegatingConstructorCall`) and then run the instance initialiser. Reading `anyClass.owner` is gated by `@OptIn(UnsafeDuringIrConstructionAPI::class)` (as annotated above; the same opt-in is documented in [`ir-plugincontext-usage`](../ir-plugincontext-usage/guide.md)).

### 3. Add fields

```kotlin
import org.jetbrains.kotlin.ir.builders.declarations.addBackingField
import org.jetbrains.kotlin.ir.builders.declarations.addDefaultGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty

val nameProperty = newClass.addProperty {
    name = Name.identifier("name")
    visibility = DescriptorVisibilities.PUBLIC
    modality = Modality.FINAL
}
val nameField = nameProperty.addBackingField {
    type = pluginContext.irBuiltIns.stringType
    isFinal = true
}
nameProperty.addDefaultGetter(newClass, pluginContext.irBuiltIns)
```

Properties typically need a backing field plus a getter (and a setter for `var`s). All three helpers live in `org.jetbrains.kotlin.ir.builders.declarations`. `addProperty` appends the property to `newClass.declarations` and sets its `parent`; `IrProperty.addBackingField { }` builds the field with the property's name, `origin = PROPERTY_BACKING_FIELD`, `visibility = PRIVATE`, sets `backingField`, `correspondingPropertySymbol`, and `parent` in one call (this is what upstream `plugin-sandbox`'s `GeneratedTopLevelClassIrGenerator` uses). Prefer it over a hand-built `factory.buildField { }` + manual `backingField =` / `parent =`, which compiles but silently skips `correspondingPropertySymbol` and the backing-field origin. `addDefaultGetter` reads `backingField!!`, so call it after `addBackingField`.

### 4. Add member functions

```kotlin
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.util.createDispatchReceiverParameterWithClassParent

val toStringFn = newClass.addFunction {
    name = Name.identifier("toString")
    visibility = DescriptorVisibilities.PUBLIC
    modality = Modality.OPEN
    returnType = pluginContext.irBuiltIns.stringType
}.apply {
    // addFunction { } does NOT add the dispatch receiver — without this line the JVM method is static.
    parameters = listOf(createDispatchReceiverParameterWithClassParent())
    overriddenSymbols = listOf(/* Any.toString symbol */)
    val builder = DeclarationIrBuilder(pluginContext, symbol)
    body = builder.irBlockBody {
        +irReturn(irString("MyGeneratedClass instance"))
    }
}
```

`addFunction { ... }` is also in `org.jetbrains.kotlin.ir.builders.declarations`; `createDispatchReceiverParameterWithClassParent()` is in `org.jetbrains.kotlin.ir.util`. The `parameters = ...` line is mandatory for every member function you build this way, on a brand-new class just as much as on an existing one — see the gotcha below.

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

### 6. Make the class visible to metadata (K2 only)

Since Kotlin 2.4.20 the registrar can register a whole class in one call. It recursively registers the class's constructors, functions, properties (through the new `registerPropertyAsMetadataVisible`), and nested/inner classes, skipping fake overrides:

```kotlin
val registrar = pluginContext.metadataDeclarationRegistrar

// Wire everything up first (steps 1-5: parent, superTypes, all members — each property
// with a getter — and sealedSubclasses for sealed classes), then register the outermost class ONCE.
registrar.registerClassAsMetadataVisible(newClass)

// To attach annotations onto a generated declaration.
// Kotlin 2.4.0+: the list element type is IrAnnotation, not IrConstructorCall.
// Build each with DeclarationIrBuilder.irAnnotation(ctorSymbol, typeArguments)
// (irCallConstructor(...) still returns a plain IrConstructorCall — wrong type here).
registrar.addMetadataVisibleAnnotationsToElement(declaration, listOfAnnotations) // List<IrAnnotation>
```

Constraints of `registerClassAsMetadataVisible` (fir2ir implementation, `Fir2IrIrGeneratedDeclarationsRegistrar`):

- Enum classes are rejected with `error("Enum classes are not supported for registerClassAsMetadataVisible: ...")`.
- Every `IrProperty` reached (directly or via recursion) must have a getter; a property without one fails with `error("Property without getter is not supported: ...")`. Local properties are silently skipped.
- Only `IrConstructor`, `IrSimpleFunction` (non-accessor), `IrProperty`, and `IrClass` members are walked; anonymous initializers and non-backing `IrField`s are ignored.
- For an inner class the captured outer type parameters are re-derived from the enclosing chain, so set `parent` on every level before registering.
- Sealed classes: `sealedSubclasses` is read at registration time, so populate it first.
- Do not also call the per-member `register*AsMetadataVisible` on members of a class you registered as a whole — the class walk already did it.
- The recursive walk only covers classes nested inside a class **you registered**. A generated class nested inside a *source-declared* outer (`sourceClass.declarations += nested`) must be registered with its own `registerClassAsMetadataVisible(nested)` call — the outer is never registered, so nothing walks into it. Upstream `plugin-sandbox` does exactly this for its "nested class inside a source-declared outer" case, and it was verified cross-module on 2.4.20 (`verification/14-cross-module-ir-class-visibility`).

On Kotlin 2.4.10 and older (or when you only add a member to an *existing* class, see step 7), register **functions and constructors** individually — properties and whole classes could not be registered before 2.4.20:

```kotlin
val registrar = pluginContext.metadataDeclarationRegistrar
newClass.functions.forEach { fn ->
    if (fn is IrSimpleFunction) registrar.registerFunctionAsMetadataVisible(fn)
}
newClass.constructors.forEach { ctor ->
    registrar.registerConstructorAsMetadataVisible(ctor)
}
```

Available `IrGeneratedDeclarationsRegistrar` methods:

| Method | Purpose |
|---|---|
| `registerFunctionAsMetadataVisible(IrSimpleFunction)` | Make a generated function visible to consumers' metadata |
| `registerConstructorAsMetadataVisible(IrConstructor)` | Same, for constructors |
| `registerPropertyAsMetadataVisible(IrProperty)` | Same, for properties (getter required; backing field and setter are picked up if present). **New in Kotlin 2.4.20** (KT-63881) |
| `registerClassAsMetadataVisible(IrClass)` | Register a whole class and, recursively, its constructors, functions, properties, and nested/inner classes. Enum classes are not supported. **New in Kotlin 2.4.20** (KT-79565) |
| `addMetadataVisibleAnnotationsToElement(IrDeclaration, List<IrAnnotation>)` | Attach annotations to be saved into metadata. **Kotlin 2.4.0+: element type is `IrAnnotation`** (was `IrConstructorCall` through 2.3.x); build with `DeclarationIrBuilder.irAnnotation(...)` |
| `getMetadataVisibleAnnotationsForElement(IrDeclaration): MutableList<IrAnnotation>` | Read back the metadata-visible annotations that were attached (`IrAnnotation` since 2.4.0) |
| `addCustomMetadataExtension(declaration, id, data)` | Attach raw bytes under a custom extension id (consumed by your own paired FIR-resolver / IR-extension on the read side) |
| `getCustomMetadataExtension(declaration, id): ByteArray?` | Read back the bytes attached via `addCustomMetadataExtension` |

Non-FIR pipelines (the plain `IrPluginContextImpl` used outside fir2ir) implement all `register*AsMetadataVisible` members as no-ops, so registration is harmless but ineffective there. The production reference for the whole-class API is `kotlin/plugins/plugin-sandbox/.../ir/GeneratedTopLevelClassIrGenerator.kt`.

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

### `IrClass.addFunction { ... }` produces a JVM-static method unless you add the dispatch receiver

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

kotlinx-serialization's `IrBuilderWithPluginContext` documents this inline. This is a property of the `addFunction { }` overload, not of the target class: it applies identically to a class you just built in step 1 (where `registerClassAsMetadataVisible` would bake the member-vs-static mismatch into the metadata of every function) and to an existing class. Member synthesis should always set `parameters` explicitly.

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
- **Mark the class as metadata-visible to downstream modules** → covered above with `metadataDeclarationRegistrar.registerClassAsMetadataVisible` (2.4.20+) or per-member registration

## What this skill does NOT cover

- The full `IrFactory` API for every IR node type — see `kotlin/compiler/ir/ir.tree/src/.../IrFactory.kt`
- Generic type parameters on synthetic classes (the patterns extend; consult `org.jetbrains.kotlin.ir.builders.declarations.addTypeParameter`)
- Inline class / value class generation (additional flag handling required)
- `external` / `expect` / `actual` synthetic declarations (multiplatform-specific; see `kotlin/plugins/kotlinx-serialization/.../FirSerializableIrIntrinsicSupport.kt` for examples)
