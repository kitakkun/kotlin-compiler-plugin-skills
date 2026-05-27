---
name: ir-plugincontext-usage
description: Use IrPluginContext to look up classes, functions, properties, and constructors from inside an IrGenerationExtension — the modern DeclarationFinder API (finderForBuiltins / finderForSource) versus the deprecated reference* methods, IC-aware lookups via recordLookup, IrDiagnosticReporter for diagnostics, and the IrGeneratedDeclarationsRegistrar for metadata. Read compiler-plugin-bootstrap first. Foundational for all other ir-* skills. NOT a how-to for writing transformations (see ir-call-rewriting, ir-body-modification, ir-synthetic-class-generation). If the user is migrating from `referenceClass`/`referenceFunctions` or sees `messageCollector` deprecation warnings, ALSO Read CHANGES.md in this skill's directory.
---

# IrPluginContext Usage

Every `IrGenerationExtension.generate(moduleFragment, pluginContext)` receives a `pluginContext: IrPluginContext`. This is the gateway to:

- Looking up symbols from stdlib, the user's own code, and library dependencies
- Reporting IR-level diagnostics
- Adding metadata to plugin-generated declarations
- Recording incremental-compilation lookups

Source: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt).

## The modern lookup API (use this)

`IrPluginContext` exposes two factory methods that return a `DeclarationFinder`:

```kotlin
// For symbols you reference unconditionally (stdlib, your plugin's required library):
val builtins: DeclarationFinder = pluginContext.finderForBuiltins()

// For symbols you reference because of something in a specific user file:
val source: DeclarationFinder = pluginContext.finderForSource(irFile)
```

The difference is **incremental compilation correctness**:

- **`finderForBuiltins()`** — references are recorded as if every file referenced the symbol. Invalidating one symbol invalidates everything. Use for stdlib, hard plugin dependencies, JVM intrinsics.
- **`finderForSource(fromFile)`** — references are recorded as a lookup from `fromFile`. Only files that referenced the symbol get re-compiled when it changes. Use when you derive a lookup from something the user wrote in a specific file (e.g. an annotation on a class).

Each `DeclarationFinder` has five lookup methods:

```kotlin
interface DeclarationFinder {
    fun findClass(classId: ClassId): IrClassSymbol?
    fun findClassifier(classId: ClassId): IrSymbol?
    fun findConstructors(classId: ClassId): Collection<IrConstructorSymbol>
    fun findFunctions(callableId: CallableId): Collection<IrSimpleFunctionSymbol>
    fun findProperties(callableId: CallableId): Collection<IrPropertySymbol>
}
```

`findClass(classId)` — expands typealiases. `findClassifier(classId)` — preserves the typealias.

## Example: look up `kotlin.io.println(Any?)`

```kotlin
@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.example

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.types.isNullableAny
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class MyIrExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val builtins = pluginContext.finderForBuiltins()
        val printlnSymbol = builtins
            .findFunctions(CallableId(FqName("kotlin.io"), Name.identifier("println")))
            .single { fn ->
                val params = fn.owner.parameters       // requires UnsafeDuringIrConstructionAPI opt-in
                params.size == 1 && params.single().type.isNullableAny()
            }
        // Use printlnSymbol when emitting IrCalls — see ir-call-rewriting / ir-body-modification.
    }
}
```

`findFunctions` returns *all* overloads with that name. Filter by parameter shape. For `println` specifically, `kotlin.io.println` has 14 overloads (plus inline ones), so always filter.

The `@file:OptIn(UnsafeDuringIrConstructionAPI::class)` is needed because `fn.owner.parameters` on an unbound symbol triggers an opt-in warning (see the gotcha "`IrSymbol.owner` requires `@OptIn(UnsafeDuringIrConstructionAPI::class)`" below). The annotation lives at `org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI` — **not** in `org.jetbrains.kotlin.ir.util` where you'd reasonably look first.

## Example: look up a user class by ClassId

```kotlin
val sourceFinder = pluginContext.finderForSource(irFile)
val userClassSymbol: IrClassSymbol? = sourceFinder.findClass(
    ClassId(FqName("com.example"), Name.identifier("Foo"))
)
```

If `userClassSymbol` is null, the class doesn't exist in the current compilation's scope (not in this module, not in any visible dependency, not in stdlib).

## The deprecated reference API

Older tutorials and Kotlin 1.x plugins use:

```kotlin
@Deprecated(level = WARNING, message = "Please use finderForBuiltins() or finderForSource(fromFile) instead.")
fun referenceClass(classId: ClassId): IrClassSymbol?
fun referenceClassifier(classId: ClassId): IrSymbol?
fun referenceConstructors(classId: ClassId): Collection<IrConstructorSymbol>
fun referenceFunctions(callableId: CallableId): Collection<IrSimpleFunctionSymbol>
fun referenceProperties(callableId: CallableId): Collection<IrPropertySymbol>
```

Functionally these still work, but they don't record lookups for incremental compilation correctly — they behave like `finderForBuiltins()` for everything. **For new code, use the finder API.** Migrating an existing plugin: replace `pluginContext.referenceFunctions(...)` with `pluginContext.finderForBuiltins().findFunctions(...)` (preserves behaviour) or `pluginContext.finderForSource(fromFile).findFunctions(...)` (correct IC) depending on the use case.

## Reporting IR diagnostics

`IrPluginContext.diagnosticReporter: IrDiagnosticReporter` is the modern entry point:

```kotlin
val reporter: IrDiagnosticReporter = pluginContext.diagnosticReporter

reporter.at(irElement, irFile).report(
    KtDiagnosticFactory0( /* … */ ),
    /* args if any */
)
```

The deprecated alternative is `pluginContext.messageCollector` (`MessageCollector`) which emits raw text without source-position richness. New code should use `diagnosticReporter`. Note: KT-78277 (https://youtrack.jetbrains.com/issue/KT-78277) tracks the messageCollector deprecation.

## Adding metadata to generated declarations

When your plugin generates declarations at IR stage that should be visible to other modules' compilation (i.e. saved to the `.kotlin_metadata` payload), use `pluginContext.metadataDeclarationRegistrar`:

```kotlin
val registrar: IrGeneratedDeclarationsRegistrar = pluginContext.metadataDeclarationRegistrar
registrar.registerFunctionAsMetadataVisible(generatedFunction)
registrar.addMetadataVisibleAnnotationsToElement(declaration, listOfAnnotations)
```

Without this, your IR-stage-generated declaration exists in this compilation only; downstream consumers can't see it from the published artefact. The registrar only works in K2 mode (`afterK2 == true`).

## Recording lookups for incremental compilation

If you derive a lookup that the `DeclarationFinder` API doesn't naturally cover (e.g. you've cached something that points at a user declaration), record it manually:

```kotlin
pluginContext.recordLookup(targetDeclaration, fromFile)
```

This tells the IC system: "if `targetDeclaration` changes, re-compile `fromFile`". Most plugins don't need this — the finder API handles it — but for advanced cases (e.g. a generator that walks a metadata file and references types based on its contents) it's the only correct way.

## What `pluginContext` provides at a glance

| Member | Purpose |
|---|---|
| `languageVersionSettings: LanguageVersionSettings` | Compiler config — language version, API version, feature flags |
| `afterK2: Boolean` | True if frontend was K2 (always true for new plugins) |
| `platform: TargetPlatform?` | JVM / JS / Native / Wasm / Common |
| `diagnosticReporter: IrDiagnosticReporter` | Modern diagnostic reporter |
| `metadataDeclarationRegistrar: IrGeneratedDeclarationsRegistrar` | Metadata for IR-generated declarations (K2 only) |
| `finderForBuiltins(): DeclarationFinder` | Lookups recorded as global |
| `finderForSource(fromFile): DeclarationFinder` | Lookups recorded per file (correct IC) |
| `recordLookup(declaration, fromFile)` | Manual IC lookup recording |
| `messageCollector` | **Deprecated** — use `diagnosticReporter` |
| `symbolTable` | **`@ObsoleteDescriptorBasedAPI`** — for legacy K1 plugins only |
| `moduleDescriptor` | **`@ObsoleteDescriptorBasedAPI`** — for legacy K1 plugins only |

`IrPluginContext` extends `IrGeneratorContext` which provides:

- `irBuiltIns: IrBuiltIns` — the built-in symbol table for primitive types, `Any`, `Unit`, `Nothing`, etc.

So `pluginContext.irBuiltIns.intType`, `pluginContext.irBuiltIns.anyNType`, etc. are also available.

## Common gotchas

### `findFunctions` returns multiple results

Always filter by parameter shape, return type, or extension receiver:

```kotlin
val correctOverload = builtins.findFunctions(callableId).single { fn ->
    fn.owner.parameters.size == 2 &&
    fn.owner.parameters[0].type.isString() &&
    fn.owner.parameters[1].type.isInt()
}
```

`single` throws if zero or multiple match — use `singleOrNull` to fail gracefully, or `firstOrNull { … }` if multiple matches are acceptable.

### `IrSymbol.owner` requires `@OptIn(UnsafeDuringIrConstructionAPI::class)` in some contexts

When iterating `parameters`, `returnType`, etc. on a function symbol's owner before lowering completes, the compiler warns. Either opt in at file level (`@file:OptIn(UnsafeDuringIrConstructionAPI::class)`) or restructure to delay the access until after the symbol is bound. The warning is real — owner access can return half-constructed IR during certain phases.

The opt-in annotation FQN is **`org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI`** — it lives under `ir.symbols`, not under `ir.util` where many readers expect it.

### `findClass` expands typealiases unexpectedly

`findClass(typealiasId)` returns the **expansion** target, not the typealias. If you need the typealias itself, use `findClassifier(classId)` and check `is IrTypeAliasSymbol`.

### `IrType.isString()` does NOT match `String?`

The type predicates on `IrType` (`isString()`, `isInt()`, `isBoolean()`, etc.) check for the **non-null** form only. `String?` returns `false` from `isString()`. When you're filtering function overloads to find one that accepts `String?` (e.g. `StringBuilder.append(String?)` — note the nullable signature), `isString()` silently rejects the right candidate.

The recommended forms:

```kotlin
// Match exactly String (non-null):
type.isString()

// Match String?:
type.isNullableString()

// Match either:
type.isString() || type.isNullableString()
```

Same trap for `isInt()` / `isNullableInt()`, `isAny()` / `isNullableAny()`, etc. The `isNullable*` variants live in `org.jetbrains.kotlin.ir.types`.

When matching against an overload that accepts a `kotlin.CharSequence` parameter (most string-builder methods do), the type to check is `isClassWithFqName(...)` or compare classifier FQN explicitly:

```kotlin
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.name.FqName

val isCharSequenceParam = type.classifierOrNull?.owner?.let {
    (it as? IrClass)?.fqNameWhenAvailable == FqName("kotlin.CharSequence")
} == true
```

Symptom of using the wrong predicate: `findFunctions(callableId).single { ... }` throws `Sequence contains no element matching the predicate` at runtime, with no helpful error pointing at the type-mismatch. Always cross-check the parameter type via `kotlinc -Xprint-ir` on a tiny snippet first.

### `referenceClass` (deprecated) returns null but `findClass` works

The deprecated API and the finder API have different lookup paths in some cases. Always migrate to `findClass`.

### `pluginContext.symbolTable` and `moduleDescriptor` are obsolete

These are `@ObsoleteDescriptorBasedAPI` — they're K1 holdovers. In K2, descriptors are not the source of truth; symbols are. Don't reach for descriptors unless maintaining a legacy plugin.

### `kotlin.String` does not expose member methods you'd expect from source

A trap when looking up runtime helpers: `findClass(StandardClassIds.String).owner.declarations` is **not** the place to find `substring(Int, Int)`, `indexOf(...)`, `length`, or `get(Int)`. In Kotlin's stdlib those operations are defined on `kotlin.CharSequence` (the supertype) or as top-level extensions in `kotlin.text`, not on `kotlin.String` itself. So:

```kotlin
// Wrong — empty result, then a confusing crash much later:
builtins.findFunctions(CallableId(FqName("kotlin.String"), Name.identifier("substring")))

// Right — substring(start, end) is a CharSequence extension in kotlin.text:
builtins.findFunctions(CallableId(FqName("kotlin.text"), Name.identifier("substring")))
    .single { fn -> fn.owner.parameters.size == 3 /* receiver + start + end */ }

// Or, for length/get/indexOf, look on CharSequence:
val charSeq = builtins.findClass(ClassId(FqName("kotlin"), Name.identifier("CharSequence")))!!
val length = charSeq.owner.declarations.filterIsInstance<IrProperty>().single { it.name.asString() == "length" }
```

Same trap for `String.toInt()`, `String.toDouble()`, etc. — those live in `kotlin.text` as top-level extensions, not on `kotlin.String`. Same for collection helpers: `List.size` is on `kotlin.collections.Collection`, not `kotlin.collections.List`.

When in doubt, run `javap kotlin/String.class` (or `kotlinc -Xprint-ir` on a tiny snippet) and look at where the method *actually lives* in stdlib.

### Lookups inside `IrElementTransformerVoid`

Construct the `DeclarationFinder` once at the top of `generate(...)` and reuse it inside the transformer. Don't call `finderForSource(fromFile)` per visit — the file may not be in scope, and you incur lookup overhead.

### Builtins from `IrBuiltIns` vs from `findClass`

`pluginContext.irBuiltIns.anyClass` returns the IR symbol for `kotlin.Any`. `pluginContext.finderForBuiltins().findClass(StandardClassIds.Any)` returns the same symbol. Prefer `irBuiltIns` for built-in primitives — it's typed (no `?`), no need to look up.

## Relation to other extensions

- **Bootstrap an IR plugin** → [`compiler-plugin-bootstrap`](../compiler-plugin-bootstrap/guide.md)
- **Replace function calls** → [`ir-call-rewriting`](../ir-call-rewriting/guide.md)
- **Modify function bodies** → [`ir-body-modification`](../ir-body-modification/guide.md)
- **Generate new IR classes** → [`ir-synthetic-class-generation`](../ir-synthetic-class-generation/guide.md)
- **Generate FIR-level declarations whose IR bodies you fill** → [`fir-declaration-generation-extension`](../fir-declaration-generation-extension/guide.md) + IR

## What this skill does NOT cover

- The full `IrBuiltIns` surface — see `kotlin/compiler/ir/ir.tree/src/.../IrBuiltIns.kt`
- The `StandardClassIds` / `StandardCallableIds` catalogue (well-known stdlib symbols) — see `kotlin/core/compiler.common/src/.../StandardClassIds.kt`
- Cross-module symbol stability — for that, consult `kotlin/docs/fir/k2-plugins.md` and the IR section of compiler docs
