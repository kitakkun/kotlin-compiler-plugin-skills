---
name: fir-session-components
description: Share computed state across FIR extensions in the same session via FirExtensionSessionComponent — the canonical state-sharing idiom used by every non-trivial Kotlin compiler plugin (kotlinx-serialization, lombok, parcelize, atomicfu). Covers extending FirExtensionSessionComponent, registering it via the FirExtensionRegistrar DSL, exposing it as a FirSession extension property via sessionComponentAccessor(), and integrating with firCachesFactory for memoisation. Read fir-extensions-overview first. NOT a how-to for any specific extension's internals.
---

# FIR Session Components

If two of your FIR extensions need to read the same computed data — annotation argument lists, class metadata, plugin configuration — share it through a `FirExtensionSessionComponent`. This is the dominant state-sharing idiom used by official Kotlin compiler plugins (kotlinx-serialization, allopen, noarg, etc.).

Source: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirExtensionSessionComponent.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirExtensionSessionComponent.kt).

## Why a session component, not a singleton

A `FirSession` is the per-module compilation context. State held on a singleton outlives the session — in IDE mode, where sessions are recreated per file change, that means stale references and wrong results across modules. A session component:

- Is constructed once per session by your factory
- Lives exactly as long as that session
- Can be looked up from any other extension in the same session via `session.yourComponent`
- Can be paired with the per-session `firCachesFactory` for memoised results

## Minimal pattern

```kotlin
package com.example.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol

class MyMetadataService(session: FirSession) : FirExtensionSessionComponent(session) {
    private val cache: FirCache<FirClassSymbol<*>, MyMetadata, Nothing?> =
        session.firCachesFactory.createCache { symbol, _ ->
            // expensive computation — e.g. walking supertypes, parsing annotation
            // arguments, resolving plugin-configured FQNs
            MyMetadata.from(symbol, session)
        }

    fun metadataOf(symbol: FirClassSymbol<*>): MyMetadata = cache.getValue(symbol, null)
}

val FirSession.myMetadataService: MyMetadataService by FirSession.sessionComponentAccessor()
```

The `by FirSession.sessionComponentAccessor()` extension property at file top-level is the canonical accessor. It uses reified types under the hood — one accessor per component class. Once registered, any FIR extension that has a `FirSession` in scope can read `session.myMetadataService`.

## Registration

In your `FirExtensionRegistrar`:

```kotlin
class MyFirRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::MyMetadataService          // registers the component
        +::MyCheckerExtension         // checker can read session.myMetadataService
        +::MyDeclarationGenerator     // generator can read it too
    }
}
```

`+::MyMetadataService` works because `MyMetadataService(session)` matches the `(FirSession) -> FirExtensionSessionComponent` signature the registrar's `unaryPlus` accepts. The session component is treated identically to other extensions in the registrar DSL.

## Caching with `firCachesFactory`

`session.firCachesFactory` produces session-scoped caches that integrate with the FIR pipeline's invalidation:

```kotlin
private val cache: FirCache<KeyType, ValueType, ContextType> =
    session.firCachesFactory.createCache { key, context ->
        compute(key, context)
    }

// Look up:
val value = cache.getValue(key, contextOrNull)
```

Cache types in `compiler/fir/tree/src/org/jetbrains/kotlin/fir/caches/`:

| Helper | Use |
|---|---|
| `firCachesFactory.createCache { key, ctx -> compute }` | keyed cache (two-arg lambda, generic context) |
| `firCachesFactory.createCache { key -> compute }` (single-arg form) | keyed cache without context |
| `firCachesFactory.createCacheWithPostCompute(...)` | two-phase: `createValue` produces `Pair<V, DATA>`, then `postCompute` runs with the stored `V` and `DATA` |
| `firCachesFactory.createCacheWithSuggestedLimits(...)` | bounded cache with eviction policy and reference-strength controls |
| `firCachesFactory.createLazyValue { compute }` | one-shot lazy |
| `firCachesFactory.createPossiblySoftLazyValue { compute }` | one-shot lazy with soft-reference reclamation |

Don't construct `ConcurrentHashMap` or `mutableMapOf` yourself — the FIR pipeline relies on these caches participating in IDE invalidation. Manual maps leak across session boundaries.

## Integrating predicates

Many session components own a predicate and expose match queries:

```kotlin
class MyMatcher(session: FirSession) : FirExtensionSessionComponent(session) {
    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(MY_PREDICATE)
    }

    fun isMatched(symbol: FirBasedSymbol<*>): Boolean =
        session.predicateBasedProvider.matches(MY_PREDICATE, symbol)
}

val FirSession.myMatcher: MyMatcher by FirSession.sessionComponentAccessor()
```

Then checkers and generators read `session.myMatcher.isMatched(symbol)` instead of querying the predicate provider directly. This centralises the predicate-FQN list, makes refactoring easy, and benefits from any caching the matcher might add.

The compiler ships an even narrower base for the "annotation marker on class" case: `AbstractSimpleClassPredicateMatchingService` in `compiler/fir/providers/src/.../utils/`. Allopen and noarg both extend it.

## Parameterised factories

When a session component needs configuration (annotation FQN list from CLI args, feature flags), use a custom factory function rather than `::Constructor`:

```kotlin
class MyMatcher(
    session: FirSession,
    private val annotationFqns: Set<FqName>,
) : FirExtensionSessionComponent(session) {
    // … uses annotationFqns
    companion object {
        fun getFactory(annotationFqns: Set<FqName>): Factory =
            Factory { session -> MyMatcher(session, annotationFqns) }
    }
}
```

Register with the factory call:

```kotlin
override fun ExtensionRegistrarContext.configurePlugin() {
    +MyMatcher.getFactory(myAnnotationFqns)
}
```

The registrar's `+` operator overloads accept a `Factory` instance directly (in addition to constructor references and `(FirSession) -> Component` lambdas).

(Pattern from [`kotlin/plugins/noarg/noarg.k2/src/org/jetbrains/kotlin/noarg/fir/NoArgAnnotationNameProvider.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/plugins/noarg/noarg.k2/src/org/jetbrains/kotlin/noarg/fir/NoArgAnnotationNameProvider.kt).)

## Common gotchas

### `session.myComponent` returns `null` or throws "component not registered"

You forgot to register the component in the registrar (`+::MyComponent`). The session lookup expects exactly one component of that class registered. The accessor goes through `ArrayMapAccessor.getValue` (`core/compiler.common/src/org/jetbrains/kotlin/util/ArrayMapOwner.kt`), which throws `IllegalStateException` with the message `No '<KClass-name>' in array owner: <session-name>` — grep for `"in array owner"` in your build log to spot the symptom.

### Two components of the same class

`sessionComponentAccessor` looks up by reified class. If you accidentally register the same component class twice (e.g. via two registrars), only one wins and the other's predicates and state are silently discarded. Each component class should be registered exactly once across all registrars in the plugin.

### Component state survives across sessions

If you store state in a `companion object` rather than instance fields, it persists across session recreation. Always put per-session state in instance fields. Use `firCachesFactory` for memoisation that must invalidate with the session.

### Predicate registered on the component but not queried via the component

If your component overrides `registerPredicates()` but you query `session.predicateBasedProvider.matches(...)` directly elsewhere, that *works* because predicates are session-global once registered. But it's brittle: deleting the component breaks lookups silently. Centralise queries through the component's API.

### Component reads other components from constructor

Don't:

```kotlin
class Bad(session: FirSession) : FirExtensionSessionComponent(session) {
    private val other = session.otherComponent  // may be null — not yet registered
}
```

Component construction order is unspecified. Defer cross-component reads until they're actually needed inside methods, not in the constructor.

### `firCachesFactory` caches use `==` on keys

If your key is a `FirBasedSymbol` or other value-equal type, that's fine. If you've wrapped keys in custom types, ensure they implement `equals`/`hashCode` correctly. There is no special "value-class cache" helper — value classes inherit the standard `equals`/`hashCode` of their wrapped value.

### IDE shows stale results after edit

If you used `mutableMapOf` instead of `firCachesFactory.createCache`, the IDE doesn't know to invalidate your map when files change. Switch to `firCachesFactory`.

## Relation to other extensions

- **Predicates that drive matching** → [`fir-predicate-system`](../fir-predicate-system/guide.md)
- **Components that own predicates and expose match queries** → this skill
- **Consumers** (checkers, generators, status transformers) read components via `session.myComponent`

## What this skill does NOT cover

- The full `firCachesFactory` API surface — see `kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/caches/` for all cache variants
- Cross-session sharing (intentionally impossible — sessions are isolation boundaries)
- IDE-only session components (the Analysis API has its own component registration mechanism)
