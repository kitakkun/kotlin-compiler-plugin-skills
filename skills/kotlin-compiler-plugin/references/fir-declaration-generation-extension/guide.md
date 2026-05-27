---
name: fir-declaration-generation-extension
description: Synthesise FIR-level declarations (classes, functions, properties, constructors, companion objects) visible to source code via FirDeclarationGenerationExtension. Use when a plugin needs to inject members or types that the user can refer to from their Kotlin source — kotlinx-serialization's KSerializer, Compose's stable annotations, parcelize's writeToParcel, lombok's @Data members. Read fir-extensions-overview and fir-predicate-system first. NOT for backend code generation (see ir-* skills) or for adding supertypes only (see fir-supertype-generation-extension).
---

# FirDeclarationGenerationExtension

The K2 extension point that adds **synthetic declarations** the rest of the frontend treats as if the user had written them. Use it when **source code in the same module** needs to reference plugin-generated members.

> **Source-visibility scope** — declarations created by this extension are visible to source code *within the module being compiled*. To make a synthetic member referenceable from a *downstream* module that depends on the current one, that's a different mechanism: generate at IR stage and call `pluginContext.metadataDeclarationRegistrar.registerFunctionAsMetadataVisible(...)` (see [`ir-plugincontext-usage`](../ir-plugincontext-usage/guide.md)). Notably, IR-side generation + metadata registration is the kotlinx-serialization "write$Self" pattern: visible from downstream modules, but *not* visible to source within the same module — the asymmetry is intentional. Empirically verified end-to-end: a function added only via `IrFactory.addFunction` + `registerFunctionAsMetadataVisible` resolves from another module's source but stays unresolved within the originating module.

Source: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt).

## What you can generate

| Method | Generates | Trigger |
|---|---|---|
| `generateNestedClassLikeDeclaration(owner, name, context)` | A nested class / companion / object on an existing class | nested-classifier name list |
| `generateFunctions(callableId, context)` | Functions on a class or top-level | callable name list |
| `generateProperties(callableId, context)` | Properties on a class or top-level | callable name list |
| `generateConstructors(context)` | Constructors on a class | callable names containing `SpecialNames.INIT` |
| `generateTopLevelClassLikeDeclaration(classId)` | A top-level class (experimental) | top-level class id list |

Each `generate*` method has a paired **discovery** method that tells the FIR pipeline *which names you intend to generate*; the pipeline then calls `generate*` for those names only:

| Discovery method | Returns | Pairs with |
|---|---|---|
| `getCallableNamesForClass(classSymbol, context)` | `Set<Name>` of function/property/constructor names | `generateFunctions`, `generateProperties`, `generateConstructors` |
| `getNestedClassifiersNames(classSymbol, context)` | `Set<Name>` of nested class names | `generateNestedClassLikeDeclaration` |
| `getTopLevelCallableIds()` | `Set<CallableId>` | `generateFunctions`, `generateProperties` |
| `getTopLevelClassIds()` | `Set<ClassId>` | `generateTopLevelClassLikeDeclaration` |
| `hasPackage(packageFqName)` | `Boolean` | for `getTopLevelClassIds()` to be queried for that package |

If the discovery method doesn't list a name, the corresponding `generate*` is **never called** for it. Returning generated declarations from `generate*` without listing the name in the discovery method silently does nothing.

## Critical contract: side-effect-free

> "The computation should be side-effect-free. … all `generate*` function implementations should not modify any state or leak the generated `FirElement` or `FirBasedSymbol` (e.g., by putting it to some cache). This restriction is imposed by the corresponding IDE cache implementation, which might retry the computation several times."

(Direct quote from the source KDoc.)

If you cache symbols across calls, the IDE's incremental cache can hand them out across invalidations and produce inconsistent FIR. Compute fresh each call. If you need shared *input* data (e.g. parsed annotation arguments per class), put it in a `FirExtensionSessionComponent` (see [`fir-extensions-overview`](../fir-extensions-overview/guide.md)).

## End-to-end example: generate a `serialize()` method on `@MyMarker` classes

### 1. Marker annotation (user code)

```kotlin
package com.example.gen
annotation class MyMarker
```

### 2. Plugin key (identifies generated declarations)

```kotlin
package com.example.gen.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey

object MyGeneratedDeclarationKey : GeneratedDeclarationKey() {
    override fun toString() = "MyMarkerPlugin"
}
```

`GeneratedDeclarationKey` is the marker that downstream IR transforms / checkers recognise to identify "this declaration came from my plugin". Use one per plugin.

### 3. Predicate (set of triggering classes)

```kotlin
package com.example.gen.fir

import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.name.FqName

private val MY_MARKER_FQN = FqName("com.example.gen.MyMarker")

val MY_MARKER_PREDICATE = DeclarationPredicate.create {
    annotated(MY_MARKER_FQN)
}
```

### 4. Generator extension

```kotlin
package com.example.gen.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

class MyGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {
    private val SERIALIZE_NAME = Name.identifier("serialize")

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(MY_MARKER_PREDICATE)
    }

    private fun matchesMarker(classSymbol: FirClassSymbol<*>): Boolean =
        session.predicateBasedProvider.matches(MY_MARKER_PREDICATE, classSymbol)
    // `matches(...)` accepts a `DeclarationPredicate`. To *enumerate* every annotated class
    // in the session (e.g. `session.predicateBasedProvider.getSymbolsByPredicate(...)`), you
    // need a separate `LookupPredicate` — declare both flavours over the same FQN. See
    // [`fir-predicate-system`](../fir-predicate-system/guide.md) for the LookupPredicate.create { annotated(...) } counterpart.

    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> {
        if (!matchesMarker(classSymbol)) return emptySet()
        return setOf(SERIALIZE_NAME)
    }

    override fun generateFunctions(callableId: CallableId, context: MemberGenerationContext?): List<FirNamedFunctionSymbol> {
        if (callableId.callableName != SERIALIZE_NAME) return emptyList()
        val owner = context?.owner ?: return emptyList()
        if (!matchesMarker(owner)) return emptyList()

        val function = createMemberFunction(
            owner = owner,
            key = MyGeneratedDeclarationKey,
            name = SERIALIZE_NAME,
            returnType = session.builtinTypes.stringType.coneType,
        )
        return listOf(function.symbol)
    }
}
```

`createMemberFunction(...)` is from `compiler/fir/plugin-utils/`. Sister helpers: `createTopLevelFunction`, `createMemberProperty`, `createTopLevelProperty`, `createConstructor`, `createNestedClass`, `createTopLevelClass`. Each takes the `key` (your `GeneratedDeclarationKey`) so the resulting FIR symbol carries `origin = key.origin` (a `FirDeclarationOrigin.Plugin` whose `key` field is your `GeneratedDeclarationKey`). The IR-side counterpart, when the declaration reaches IR, is `IrDeclarationOrigin.GeneratedByPlugin(yourKey)` — same key bridges both.

The most common helper signatures at v2.3.21 (all under `org.jetbrains.kotlin.fir.plugin`; receiver is `FirExtension`, which `FirDeclarationGenerationExtension` inherits from, so they are callable from inside any subclass body):

```kotlin
fun FirExtension.createMemberFunction(
    owner: FirClassSymbol<*>, key: GeneratedDeclarationKey,
    name: Name, returnType: ConeKotlinType,
    config: SimpleFunctionBuildingContext.() -> Unit = {},
): FirNamedFunction

fun FirExtension.createConstructor(
    owner: FirClassSymbol<*>, key: GeneratedDeclarationKey,
    isPrimary: Boolean = false, generateDelegatedNoArgConstructorCall: Boolean = false,
    config: ConstructorBuildingContext.() -> Unit = {},
): FirConstructor

fun FirExtension.createNestedClass(
    owner: FirClassSymbol<*>, name: Name, key: GeneratedDeclarationKey,
    classKind: ClassKind = ClassKind.CLASS,
    config: ClassBuildingContext.() -> Unit = {},
): FirRegularClass

fun FirExtension.createMemberProperty(
    owner: FirClassSymbol<*>, key: GeneratedDeclarationKey,
    name: Name, returnType: ConeKotlinType,
    isVal: Boolean = true, hasBackingField: Boolean = true,
    config: PropertyBuildingContext.() -> Unit = {},
): FirProperty
```

Note that `createConstructor`'s `generateDelegatedNoArgConstructorCall` defaults to `false` — if you generate a non-primary constructor whose enclosing class inherits from a class with no no-arg superconstructor, you must either flip this to `true` *only* when a no-arg supertype constructor actually exists, or emit the delegating call yourself in `config`. Leaving the default and silently producing a body-less constructor is the usual cause of `IllegalStateException: not generated yet` at codegen time.

Each `*BuildingContext` exposes lists like `typeParameters`, `valueParameters`, `modality`, `visibility` etc. that you mutate inside the `config` lambda.

### 5. Wire it up

```kotlin
class MyFirRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::MyGenerator
    }
}
```

The `+::MyGenerator` picks up `MyGenerator(session: FirSession)` constructor; `registerPredicates()` is then called on the extension instance per session.

## Generating a constructor

To generate a constructor for a class, **return `SpecialNames.INIT` from `getCallableNamesForClass`**, then implement `generateConstructors`:

```kotlin
override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> {
    if (!matchesMarker(classSymbol)) return emptySet()
    return setOf(SpecialNames.INIT)
}

override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
    val owner = context.owner
    if (!matchesMarker(owner)) return emptyList()
    return listOf(createConstructor(owner = owner, key = MyGeneratedDeclarationKey, isPrimary = false).symbol)
}
```

Easy to forget; `INIT` is the magic name that makes the pipeline ask for constructors. Without it, `generateConstructors` is never called.

## Generating a nested class (e.g. companion object)

A complete companion-object synthesis requires **three coordinated overrides** — `createCompanionObject` itself only declares the class shell; you also need to advertise that the companion has a constructor, and provide that constructor:

```kotlin
override fun getNestedClassifiersNames(classSymbol: FirClassSymbol<*>, context: NestedClassGenerationContext): Set<Name> {
    if (!matchesMarker(classSymbol)) return emptySet()
    return setOf(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)
}

override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext,
): FirClassLikeSymbol<*>? {
    if (name != SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) return null
    if (!matchesMarker(owner)) return null
    return createCompanionObject(owner = owner, key = MyGeneratedDeclarationKey).symbol
}

// REQUIRED: advertise that the synthesised companion has a constructor.
override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> {
    // For the synthesised companion (origin = our key), include INIT so generateConstructors fires.
    if (classSymbol.origin is FirDeclarationOrigin.Plugin &&
        (classSymbol.origin as FirDeclarationOrigin.Plugin).key == MyGeneratedDeclarationKey) {
        return setOf(SpecialNames.INIT) // plus any callable names you also synthesise on the companion
    }
    // For the OWNER class (where the companion lives), advertise other generated callables
    // here. If you don't generate anything on the owner itself, return emptySet().
    return emptySet()
}

override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
    val owner = context.owner
    if (owner.origin !is FirDeclarationOrigin.Plugin) return emptyList()
    if ((owner.origin as FirDeclarationOrigin.Plugin).key != MyGeneratedDeclarationKey) return emptyList()
    return listOf(createDefaultPrivateConstructor(owner = owner, key = MyGeneratedDeclarationKey).symbol)
}
```

Without `SpecialNames.INIT` returned for the *companion's* `getCallableNamesForClass`, `generateConstructors` is never asked about it, and the resulting companion class has no constructor — IR-side generation that tries to attach members (e.g. an `IrFactory`-built `parse` method on the companion) then fails downstream because the companion can't be instantiated. The three overrides are a unit; a "companion object generated but the build crashes at IR" is almost always a missing `INIT` advertisement on the companion.

**`INIT` is mandatory only for classes that will actually be instantiated.** If you generate a class purely as a *marker / discovery anchor* — never referenced as a constructor call from user code or from IR you also generate — you can omit `SpecialNames.INIT` and skip `generateConstructors` entirely. The backend tolerates a class with no constructor as long as nothing tries to call `new`. Empty top-level classes used purely to carry metadata for a downstream module to discover (e.g. cross-module aspect registration) are the canonical case. Whenever you *do* call the synthesised class's constructor from anywhere — IR codegen, user source, or another plugin — `INIT` is required.

## Generating a top-level class (experimental)

```kotlin
@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
class MyTopLevelGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {
    override fun getTopLevelClassIds(): Set<ClassId> = setOf(MY_GENERATED_CLASS_ID)
    override fun hasPackage(packageFqName: FqName): Boolean = packageFqName == MY_GENERATED_CLASS_ID.packageFqName
    override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
        if (classId != MY_GENERATED_CLASS_ID) return null
        return createTopLevelClass(classId = classId, key = MyGeneratedDeclarationKey).symbol
    }
}
```

`@ExperimentalTopLevelDeclarationsGenerationApi` is required at the call site. Top-level generation is more brittle than nested generation — prefer nested when possible.

## Resolution-phase awareness

Some methods can be called only after specific resolution phases:

| Method | Earliest phase |
|---|---|
| `generateTopLevelClassLikeDeclaration` | `SUPERTYPES` (companion-aware ClassId) |
| `getCallableNamesForClass` | `SUPERTYPES` |
| `generateFunctions` / `generateProperties` / `generateConstructors` | `STATUS` |
| `hasPackage` | `IMPORTS` |

You don't normally call these yourself — the FIR pipeline does — but the implication is that inside `generateFunctions`, the owner's FIR is at least at `STATUS`, so its supertypes and modifiers are queryable. Inside `generateTopLevelClassLikeDeclaration`, supertypes of the *generated* class can use the owner's symbol but cannot reference other plugin-generated classes whose generation hasn't run yet — beware the generation cycle.

## Common gotchas

### `generate*` returns symbols but the IDE can't see them

You returned them from `generateFunctions` but didn't list the name in `getCallableNamesForClass`. The pipeline never asked, so it never got them. Always pair generation with discovery.

### Symbol from a previous call comes back

You cached the result in a `var`/`mutableMapOf`. Don't — the side-effect-free contract requires fresh results every call. Cache *inputs* in a session component, not *outputs*.

### `context?.owner` is null in `generateFunctions`

`context` is `MemberGenerationContext?` (nullable) — null indicates a top-level callable. For class members, expect non-null and bail with `?: return emptyList()`. For top-level (`getTopLevelCallableIds()` path), the owner is null by definition; use `callableId.packageName` instead.

### Generated declaration's `origin` looks wrong

Use the `*BuildingContext` helpers (`createMemberFunction` etc.) so the origin is set via `key.origin` to a `FirDeclarationOrigin.Plugin` carrying your `GeneratedDeclarationKey`. Don't construct `FirNamedFunction` manually unless you really need to — you'll have to set status, type refs, and origin yourself.

### Nested generation never fires for a non-existent companion

If you want to *add* a companion object to a class that doesn't have one, use `generateNestedClassLikeDeclaration` returning `SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT`. If you want to *add members to* an existing companion, `getCallableNamesForClass` on the companion's `FirClassSymbol` is the right hook — the companion is just another class symbol.

### IDE shows the synthetic member but kotlinc complains

Both code paths use the same FIR generation when wired through `KotlinCompilerPluginSupportPlugin`. With the raw `-Xplugin=` harness from [`compiler-plugin-bootstrap`](../compiler-plugin-bootstrap/guide.md), only kotlinc loads the plugin; the IDE analyser sees only what the IntelliJ Kotlin plugin's own discovery brings in. See [`gradle-plugin-integration`](../gradle-plugin-integration/guide.md) for IDE-visible plugins.

### Backend doesn't see the generated declaration

`FirDeclarationGenerationExtension` runs in the frontend. The IR backend converts FIR to IR and includes plugin-generated declarations *as long as their bodies are produced*. Empty function bodies become `null` IR bodies — you usually pair declaration generation with an `IrGenerationExtension` that fills in the body. See [`ir-body-modification`](../ir-body-modification/guide.md) (planned) for the pattern.

## Relation to other extensions

- **Filter the trigger** → [`fir-predicate-system`](../fir-predicate-system/guide.md)
- **Generate a body for the synthesised member** → `IrGenerationExtension` (see [`ir-body-modification`](../ir-body-modification/guide.md))
- **Force the generated class to extend a marker interface** → [`fir-supertype-generation-extension`](../fir-supertype-generation-extension/guide.md)
- **Make the synthesised member final / open** → [`fir-status-transformer-extension`](../fir-status-transformer-extension/guide.md)
- **Stash analysis state across extensions** → [`fir-session-components`](../fir-session-components/guide.md)

## What this skill does NOT cover

- Building complex FIR types (generics, function types, intersection types) from scratch — the `*BuildingContext` helpers cover the common case; for advanced needs see `kotlin/compiler/fir/plugin-utils/src/`.
- IR-side body generation — see [`ir-body-modification`](../ir-body-modification/guide.md).
- Suppressing built-in synthetic generation (e.g. data class members) — that requires status transformer + checker coordination.
