---
name: fir-status-transformer-extension
description: Modify modifiers (visibility, modality, isOpen, isFinal, isInline, etc.) on existing user declarations via FirStatusTransformerExtension. Use when an annotation flips modifiers — allopen makes annotated classes `open`, no-arg adds synthetic constructors, JPA-style plugins change visibility. Read fir-extensions-overview and fir-predicate-system first. NOT for adding declarations (see fir-declaration-generation-extension) or supertypes (see fir-supertype-generation-extension).
---

# FirStatusTransformerExtension

The K2 extension point that **rewrites the `FirDeclarationStatus`** (the bag of modifier flags) on existing user declarations. Canonical use: `@AllOpen` makes every member of an annotated class `open`; an `@External` annotation could change visibility; `@Inline` could force inlining.

Source: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt).

## API surface

```kotlin
abstract class FirStatusTransformerExtension(session: FirSession) : FirExtension(session) {
    abstract fun needTransformStatus(declaration: FirDeclaration): Boolean

    // Generic fallback (implemented in terms of typed overloads):
    protected open fun transformStatus(status, declaration): FirDeclarationStatus = status

    // Per-declaration-type overloads (use these in real plugins):
    open fun transformStatus(status, regularClass: FirRegularClass, containingClass, isLocal): FirDeclarationStatus
    open fun transformStatus(status, function: FirNamedFunction, containingClass, isLocal): FirDeclarationStatus
    open fun transformStatus(status, property: FirProperty, containingClass, isLocal): FirDeclarationStatus
    open fun transformStatus(status, propertyAccessor: FirPropertyAccessor, containingClass, containingProperty, isLocal): FirDeclarationStatus
    open fun transformStatus(status, constructor, containingClass, isLocal): FirDeclarationStatus
    open fun transformStatus(status, typeAlias, containingClass, isLocal): FirDeclarationStatus
    open fun transformStatus(status, field, containingClass, isLocal): FirDeclarationStatus
    open fun transformStatus(status, backingField, containingClass, isLocal): FirDeclarationStatus
    open fun transformStatus(status, enumEntry, containingClass, isLocal): FirDeclarationStatus
}
```

| Member | Purpose |
|---|---|
| `needTransformStatus(decl)` | Cheap pre-filter — return `true` only if you intend to change something. Called for every declaration. |
| `transformStatus(status, ...)` overloads | Return a new `FirDeclarationStatus` (use the `transform { ... }` helper) or the unchanged `status` if you decide not to modify. |

## Important limitations

The source KDoc explicitly forbids:

> **It's forbidden for this extension to change the visibility of a regular class** in any way, as this may influence type resolve thus violating our phase contracts.
>
> **It's forbidden for this extension to change the visibility of a type alias** in any way, as this may influence type resolve thus violating our phase contracts.

You can change visibility on functions, properties, constructors, accessors, fields, enum entries — but not on regular classes or type aliases. The pipeline assumes class visibility is stable through type resolution.

## End-to-end example: make annotated classes' members `open`

A custom `@Open` annotation (`com.example.Open`) that compiles `@Open class Foo { fun bar() }` as `open class Foo { open fun bar() }`. This mirrors what the official allopen plugin does internally — allopen takes a configurable list of annotation FQNs via plugin options rather than hard-coding one. See [`plugins/allopen/allopen.k2/src/org/jetbrains/kotlin/allopen/fir/FirAllOpenStatusTransformer.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/plugins/allopen/allopen.k2/src/org/jetbrains/kotlin/allopen/fir/FirAllOpenStatusTransformer.kt) for the production implementation.

### 1. Predicate

```kotlin
private val OPEN_FQN = FqName("com.example.Open")
val OPEN_PREDICATE = DeclarationPredicate.create { annotated(OPEN_FQN) or ancestorAnnotated(OPEN_FQN) }
```

`ancestorAnnotated` lets us match members of an annotated class without re-tagging each member.

### 2. Transformer

```kotlin
package com.example.openplugin.fir

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.copyWithNewDefaults
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.extensions.transform
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol

class MakeOpenTransformer(session: FirSession) : FirStatusTransformerExtension(session) {

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(OPEN_PREDICATE)
    }

    override fun needTransformStatus(declaration: FirDeclaration): Boolean =
        session.predicateBasedProvider.matches(OPEN_PREDICATE, declaration)

    // Class itself: make it open (modality only, NOT visibility).
    override fun transformStatus(
        status: FirDeclarationStatus,
        regularClass: FirRegularClass,
        containingClass: FirClassLikeSymbol<*>?,
        isLocal: Boolean,
    ): FirDeclarationStatus = openify(status)

    // Member functions of an annotated class
    override fun transformStatus(
        status: FirDeclarationStatus,
        function: FirNamedFunction,
        containingClass: FirClassLikeSymbol<*>?,
        isLocal: Boolean,
    ): FirDeclarationStatus = if (isLocal) status else openify(status)

    // Member properties similarly
    override fun transformStatus(
        status: FirDeclarationStatus,
        property: FirProperty,
        containingClass: FirClassLikeSymbol<*>?,
        isLocal: Boolean,
    ): FirDeclarationStatus = if (isLocal) status else openify(status)

    private fun openify(status: FirDeclarationStatus): FirDeclarationStatus = when (status.modality) {
        null -> status.copyWithNewDefaults(modality = Modality.OPEN, defaultModality = Modality.OPEN)
        Modality.FINAL -> status.copyWithNewDefaults(defaultModality = Modality.OPEN)
        else -> status
    }
}
```

`status.modality` is **`null` when the user wrote no explicit `final`/`open` keyword** — at status-transform time the default-FINAL hasn't been materialised yet. A naive `if (status.modality == Modality.FINAL)` check silently misses this case and the transform becomes a no-op. The production allopen plugin (`FirAllOpenStatusTransformer.kt` at v2.3.21) uses a **2-arm** `when (status.modality)`: the `null` branch sets both `modality = OPEN` and `defaultModality = OPEN`; the `else` branch sets `defaultModality = OPEN` without touching the explicit modality. The 3-arm form shown above is a legitimate variant that makes the `Modality.FINAL` case explicit, but the production plugin collapses `FINAL`/`OPEN`/`ABSTRACT`/`SEALED` into one `else`. The `FirDeclarationStatus.transform(visibility, modality, init)` helper exists too, but it doesn't help with the `null`-modality case — `copyWithNewDefaults` is the canonical idiom.

### 3. Wire it up

```kotlin
class MyFirRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::MakeOpenTransformer
    }
}
```

(Real-world reference: `kotlin/plugins/allopen/allopen.k2/src/.../FirAllOpenStatusTransformer.kt`.)

## Flipping non-modality flags (`isInline`, `isOperator`, `isInfix`, `isTailRec`, …)

`copyWithNewDefaults` is restricted to `visibility`, `modality`, and the `defaultModality` projection — it does **not** expose the boolean flags `isInline`, `isOperator`, `isInfix`, `isTailRec`, `isExternal`, `isExpect`, `isActual`, etc. To flip those, use the `transform { ... }` helper at `FirStatusTransformerExtension.kt:134-160`, whose lambda runs against `FirDeclarationStatusImpl` and lets you mutate any field directly:

```kotlin
// import org.jetbrains.kotlin.fir.extensions.transform — already imported in the file's import block above

// Reuse the same predicate shape as OPEN_PREDICATE above. If your marker annotation
// is `@MakeInline`, declare it at top level next to your other predicates:
//   val MAKE_INLINE_PREDICATE = DeclarationPredicate.create {
//       annotated(MAKE_INLINE_FQN) or ancestorAnnotated(MAKE_INLINE_FQN)
//   }

class MakeInlineTransformer(session: FirSession) : FirStatusTransformerExtension(session) {

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(MAKE_INLINE_PREDICATE)
    }

    override fun needTransformStatus(declaration: FirDeclaration): Boolean =
        session.predicateBasedProvider.matches(MAKE_INLINE_PREDICATE, declaration)

    override fun transformStatus(
        status: FirDeclarationStatus,
        function: FirNamedFunction,
        containingClass: FirClassLikeSymbol<*>?,
        isLocal: Boolean,
    ): FirDeclarationStatus = status.transform { isInline = true }
}
```

This propagates end-to-end: a `@MakeInline fun runIt(block: () -> Unit) { block() }` admits non-local returns from its lambda argument *without* the user having written the `inline` keyword, and JVM codegen actually inlines the call (verified via `javap` — the call site shows the lambda body inlined, not an `invokestatic runIt`).

`copyWithNewDefaults` is the right tool for modality (because it correctly handles the `null` case at the very start of status resolution); `transform { ... }` is the right tool for everything else.

## Common gotchas

### Typed vs generic overload — both styles are valid

Two override styles work:

1. **Per-type overrides** — override the public `transformStatus(status, regularClass: FirRegularClass, ...)`, `transformStatus(status, function: FirNamedFunction, ...)`, etc. for each declaration kind. The example above uses this style.
2. **Generic catch-all** — override the protected `transformStatus(status, declaration: FirDeclaration)` once and dispatch internally. `allopen` (`kotlin/plugins/allopen/allopen.k2/src/.../FirAllOpenStatusTransformer.kt`) uses this style with `copyWithNewDefaults(...)` instead of `transform { ... }`.

The typed overloads forward to the generic one by default. Either style works; the per-type overrides are easier to read when you treat declaration kinds differently, the generic style is terse when you treat them uniformly. Don't mix — pick one. Allopen's generic-only style is a perfectly valid pattern; it just exposes a different trade-off.

### Trying to flip a class's visibility

Forbidden by the API contract. The pipeline may silently ignore your change, or worse, cause inconsistent type resolution. If you need to change a class's visibility, you've picked the wrong extension — re-think the design.

### Changing `isOverride` or `isOpen` on a declaration that's already overridden somewhere

`FirDeclarationStatus` controls how the declaration is *declared*. Overriding behaviour from this declaration's perspective is one slice; the *containing class*'s open-ness is a different slice. Make both of them open — see the example above which transforms both class and member statuses.

### Stale modifier flags after manual reconstruction

If you construct `FirDeclarationStatusImpl(visibility, modality)` directly, you lose `isInline`, `isOperator`, `isInfix`, `isData`, `isExpect`, `isActual`, etc. Use `copyWithNewDefaults(...)` (preserves all flags AND fixes the null-modality case shown above) or `transform { ... }` (preserves flags but doesn't help when `status.modality` is null).

### Changes to local declarations

`isLocal: Boolean` tells you whether the declaration is inside a function. Most transforms should bail on locals — making a local function `open` is meaningless because nothing can extend the enclosing scope.

### Multiple status transformers

If two plugins register status transformers and both want to change the same declaration, ordering is unspecified — there is no priority API on `FirExtension` as of Kotlin 2.3.21. Be defensive: read the input `status`, change only what you need, return.

### Status transform vs `Final` parent class

Making a method `open` doesn't help if the enclosing class is `final`. The example above transforms both. If you're consuming third-party `final` classes, `FirSupertypeGenerationExtension` cannot make them non-final from outside — only the owner module's own status transformer can.

## Relation to other extensions

- **Pick which declarations to transform** → [`fir-predicate-system`](../fir-predicate-system/guide.md)
- **Make annotated classes implement an interface, then make their methods open** → use both [`fir-supertype-generation-extension`](../fir-supertype-generation-extension/guide.md) and this one
- **Generated declarations need explicit modifiers** → set them in the `*BuildingContext` helpers in [`fir-declaration-generation-extension`](../fir-declaration-generation-extension/guide.md); the status transformer doesn't run on plugin-generated declarations by default

## What this skill does NOT cover

- Changing `isExpect` / `isActual` (multiplatform contract changes)
- Adding/removing modifiers via the dedicated K1 path (legacy; not relevant for K2 plugins)
- The `transform` helper's full flag set — see `FirStatusTransformerExtension.kt` lines 134–160 for the exhaustive list of preserved flags
