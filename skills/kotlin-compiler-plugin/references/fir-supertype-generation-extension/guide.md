---
name: fir-supertype-generation-extension
description: Inject supertypes (interfaces, base classes) onto existing user-written classes via FirSupertypeGenerationExtension. Use when an annotation-driven plugin needs every annotated class to implement some marker interface (e.g. kotlinx-serialization injecting KSerializer-related supertypes onto annotated companions) without users writing the supertype themselves. Read fir-extensions-overview and fir-predicate-system first. NOT for declaring new classes (see fir-declaration-generation-extension) or for IR-level type changes (which don't exist as a concept — types live in FIR).
---

# FirSupertypeGenerationExtension

The K2 extension point that adds **supertypes** (interfaces or base classes) to existing classes. Use it when annotated user classes (or their companions) should implicitly extend or implement something — e.g. kotlinx-serialization injects supertypes onto the companion of an annotated `@Serializable` class so the synthesised serializer fits into the type hierarchy.

(Note: Parcelize is *not* a user of this extension — `@Parcelize class` requires the user to still write `: Parcelable` by hand. The plugin only generates members and runs checkers.)

Source: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirSupertypeGenerationExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirSupertypeGenerationExtension.kt).

## API surface

```kotlin
abstract class FirSupertypeGenerationExtension(session: FirSession) : FirExtension(session) {
    abstract fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean
    abstract fun computeAdditionalSupertypes(
        classLikeDeclaration: FirClassLikeDeclaration,
        resolvedSupertypes: List<FirResolvedTypeRef>,
        typeResolver: TypeResolveService,
    ): List<ConeKotlinType>
    fun interface Factory : FirExtension.Factory<FirSupertypeGenerationExtension>
}
```

Two abstract members:

| Member | Purpose |
|---|---|
| `needTransformSupertypes(decl)` | Cheap pre-filter — return `true` for classes you want to transform. Called for every class. |
| `computeAdditionalSupertypes(decl, resolvedSupertypes, typeResolver)` | Returns the **extra** supertypes (as `List<ConeKotlinType>`). Existing supertypes already in `resolvedSupertypes` are preserved unchanged. |

## End-to-end example: implement a marker interface for `@Tagged` classes

### 1. User-facing types

```kotlin
// User code
package com.example.tag

annotation class Tagged
interface Tag { fun tagName(): String }
```

### 2. Predicate

```kotlin
package com.example.tag.fir

import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.name.FqName

private val TAGGED_FQN = FqName("com.example.tag.Tagged")
val TAGGED_PREDICATE = DeclarationPredicate.create { annotated(TAGGED_FQN) }
```

### 3. Supertype generator

```kotlin
package com.example.tag.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class TaggedSupertypeGenerator(session: FirSession) : FirSupertypeGenerationExtension(session) {
    private val TAG_CLASS_ID = ClassId(FqName("com.example.tag"), Name.identifier("Tag"))

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(TAGGED_PREDICATE)
    }

    override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean =
        session.predicateBasedProvider.matches(TAGGED_PREDICATE, declaration)

    override fun computeAdditionalSupertypes(
        classLikeDeclaration: FirClassLikeDeclaration,
        resolvedSupertypes: List<FirResolvedTypeRef>,
        typeResolver: TypeResolveService,
    ): List<ConeKotlinType> {
        // Don't add Tag if a supertype is already Tag itself
        if (resolvedSupertypes.any { it.coneType.classId == TAG_CLASS_ID }) return emptyList()
        return listOf(TAG_CLASS_ID.constructClassLikeType(emptyArray(), isMarkedNullable = false))
    }
}
```

Build the supertype directly from the `ClassId` via `ClassId.constructClassLikeType(typeArguments, isMarkedNullable)` (declared in `org.jetbrains.kotlin.fir.types`). The pattern `tagSymbol.toLookupTag().constructClassLikeType(...)` does **not** compile: `ConeClassLikeLookupTag` exposes `constructClassType(...)`, not `constructClassLikeType(...)`. The `ClassId`-rooted form is what the verification project uses and is generally simpler.

`needTransformSupertypes` should be cheap — it's called for every class. Doing predicate matching is fine. Doing reflection over annotation arguments isn't.

`computeAdditionalSupertypes` only returns the **extra** types. The pipeline merges your additions with the original supertypes; you don't reconstruct the full list.

### 4. Wire it up

```kotlin
class MyFirRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::TaggedSupertypeGenerator
    }
}
```

## Resolving annotation-argument types via TypeResolveService

The `typeResolver: TypeResolveService` parameter is for the case where the supertype to add is *named in an annotation argument*: e.g. `@AddSuper(SomeInterface::class) class Foo` should make `Foo` extend `SomeInterface`. The KClass argument arrives as a `FirGetClassCall`; the canonical helper for converting it into a resolved type is `typeFromQualifierParts` (in [`compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/RawUserTypeBuilder.kt`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/RawUserTypeBuilder.kt)). kotlinx-serialization uses it in [`SerializationFirSupertypesExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/kotlinx-serialization/kotlinx-serialization.k2/src/org/jetbrains/kotlinx/serialization/compiler/fir/SerializationFirSupertypesExtension.kt):

```kotlin
override fun computeAdditionalSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: TypeResolveService,
): List<ConeKotlinType> {
    val annotation = classLikeDeclaration.annotations.find { it.fqName(session) == ADD_SUPER_FQN } ?: return emptyList()
    val getClassCall = annotation.argumentMapping.mapping.values.firstOrNull() as? FirGetClassCall ?: return emptyList()
    val resolved: ConeKotlinType = typeFromQualifierParts(
        isMarkedNullable = false,
        typeResolver = typeResolver,
        source = getClassCall.source!!,
    ) {
        fun visitQualifiers(expression: FirExpression) {
            if (expression !is FirPropertyAccessExpression) return
            expression.explicitReceiver?.let { visitQualifiers(it) }
            (expression.calleeReference as? FirSimpleNamedReference)?.name?.let { part(it) }
        }
        visitQualifiers(getClassCall.argument)
    }
    return listOf(resolved)
}
```

Note: `typeFromQualifierParts` returns a non-null `ConeKotlinType` (`buildUserTypeRef { ... }` followed by `typeResolver.resolveUserType(...).coneType` — see `org.jetbrains.kotlin.fir.extensions.RawUserTypeBuilder`). Older snippets used `?: return emptyList()` and `resolved.coneType`; both are wrong.

`TypeResolveService.resolveUserType(userType: FirUserTypeRef): FirResolvedTypeRef` is the underlying API the helper uses — it converts an unresolved type reference into a resolved one. You can't use the session's regular resolver here because supertype generation runs *during* the supertype resolution phase; the regular resolver would deadlock. Use the provided service (directly, or through `typeFromQualifierParts`).

## Generating supertypes for *generated* nested classes

If your `FirDeclarationGenerationExtension` synthesises a nested class, and that nested class needs a supertype that depends on annotation arguments (the canonical `@AddNestedClassesBasedOnArgument(XXX::class) class Some { class Generated : XXX() }` shape), use the experimental `computeAdditionalSupertypesForGeneratedNestedClass` override. The method itself is gated by `@ExperimentalSupertypesGenerationApi` (`RequiresOptIn.Level.ERROR`), so callers must opt in:

```kotlin
@OptIn(ExperimentalSupertypesGenerationApi::class)
override fun computeAdditionalSupertypesForGeneratedNestedClass(
    klass: FirRegularClass,
    typeResolver: TypeResolveService,
): List<ConeKotlinType> {
    // Inspect klass.containingClass annotations and synthesise the right supertype
    return listOf(/* the resolved type */)
}
```

If the override returns non-empty, the default `Any` supertype is automatically removed.

Limitations called out in the source KDoc:
- One level only — won't be called for nested classes of the generated class
- Doesn't work for top-level generated classes

## Companion-object case

Real plugins often inject supertypes onto the **companion** of an annotated parent class, not the parent itself. The pattern (mirroring kotlinx-serialization):

```kotlin
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.resolve.getContainingDeclaration
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol

override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean {
    if (declaration !is FirRegularClass || !declaration.isCompanion) return false
    val parent = declaration.symbol.getContainingDeclaration(session) as? FirClassSymbol<*>
        ?: return false
    return session.predicateBasedProvider.matches(MARKER_PREDICATE, parent)
}
```

Note `getContainingDeclaration` lives in `org.jetbrains.kotlin.fir.resolve` (not under `.utils`). `isCompanion` lives in `org.jetbrains.kotlin.fir.declarations.utils`.

Use this when "things annotated with `@X` should produce a companion that implements `Factory<...>`".

## Multiplatform / target gating

Different platforms have different runtime support for the supertypes you inject. A factory class or interface that only exists on JS/Native/Wasm should be omitted on JVM/Metadata (and vice versa). Read `session.moduleData.platform` and bail on the wrong targets. The exact gating depends on which runtime supplies your supertype:

```kotlin
// Pattern A: inject ONLY on non-JVM (JS/Native/Wasm) — what kotlinx-serialization
// actually does for its SerializerFactory supertype.
val isJvmOrMetadata = !session.moduleData.platform.run { isNative() || isJs() || isWasm() }
override fun computeAdditionalSupertypes(...): List<ConeKotlinType> {
    if (isJvmOrMetadata) return emptyList()
    // ...
}

// Pattern B: inject ONLY on JVM.
override fun computeAdditionalSupertypes(...): List<ConeKotlinType> {
    if (!session.moduleData.platform.isJvm()) return emptyList()
    // ...
}
```

kotlinx-serialization's `SerializationFirSupertypesExtension` uses **Pattern A** (verified at v2.3.21): `private val isJvmOrMetadata = !session.moduleData.platform.run { isNative() || isJs() || isWasm() }`, then early-returns when `isJvmOrMetadata` is true. The `SerializerFactory` supertype is non-JVM only — on JVM, factories are emitted differently. Get the gating direction right for your runtime: a flipped check silently corrupts the generated bytecode shape on one target.

## Common gotchas

### Adding a supertype that's already there causes "supertype appears twice"

Filter out classes that already extend the desired type:

```kotlin
if (resolvedSupertypes.any { it.coneType.classId == TAG_CLASS_ID }) return emptyList()
```

The example above used a placeholder `isTagSubtype` — for production code, prefer the simple `classId == TARGET_CLASS_ID` check shown here, or walk the supertype graph if you need full subtype semantics. Adding `Comparable<X>` to a class that already implements `Comparable<Y>` may also cause issues — be conservative.

### Classes from dependencies don't get the new supertype

`needTransformSupertypes` is called for declarations in the **current source session**. Library classes (already-compiled dependencies) don't get re-transformed. If you need that behaviour, the consumer of the dependency must also have your plugin enabled — supertypes don't propagate through compiled artefacts.

### `computeAdditionalSupertypes` return must be `List<ConeKotlinType>`, not `FirTypeRef`

The pipeline wraps the returned `ConeKotlinType` into a `FirResolvedTypeRef` itself. Returning a `FirResolvedTypeRef` directly is a type error at the override.

### Supertype with type parameters

For generic supertypes:

```kotlin
val listOfStringType = StandardClassIds.List.constructClassLikeType(
    arrayOf(session.builtinTypes.stringType.coneType),
    isMarkedNullable = false,
)
return listOf(listOfStringType)
```

`ClassId.constructClassLikeType(typeArguments: Array<out ConeTypeProjection>, isMarkedNullable: Boolean)` is the canonical builder. `ConeClassLikeLookupTag.constructClassType(...)` is an alternative on the lookup-tag side, but `ClassId`-rooted construction is simpler and matches the verification project.

### "TypeResolveService called outside supertype resolution"

You're caching `typeResolver` and using it later. Don't — it's only valid during the supertype generation phase. Use it inside `computeAdditionalSupertypes` and discard.

### Sealed class hierarchies

Adding a supertype to a sealed class can break exhaustiveness checks elsewhere — `when` statements over the sealed hierarchy may stop being recognised as exhaustive. Compile a `when` over the sealed hierarchy with your plugin enabled and confirm no `NO_ELSE_IN_WHEN` diagnostic appears.

### `needTransformSupertypes` returns false but supertype still appears

Check that you're not also generating it from a `FirDeclarationGenerationExtension` — both paths can add supertypes independently.

## Relation to other extensions

- **Pick which classes get the supertype** → [`fir-predicate-system`](../fir-predicate-system/guide.md)
- **Generate the synthesised members the new supertype demands** → [`fir-declaration-generation-extension`](../fir-declaration-generation-extension/guide.md)
- **Resolve the implementation at runtime** → `IrGenerationExtension` for body filling

## What this skill does NOT cover

- Removing or replacing supertypes (the API only adds — to remove, the user must not declare the original)
- Modifying type parameters of existing supertypes
- Generated-class supertypes via `computeAdditionalSupertypesForGeneratedNestedClass` beyond the basic shape (it's experimental and limited)
