# Evidence for guide.md

## `FirExtensionSessionComponent(session: FirSession)` constructor

### Claim: `class MyMetadataService(session: FirSession) : FirExtensionSessionComponent(session)`
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirExtensionSessionComponent.kt:12`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirExtensionSessionComponent.kt#L12)
- **Snippet**:
  ```kotlin
  abstract class FirExtensionSessionComponent(session: FirSession) : FirExtension(session), FirSessionComponent {
  ```

## Nested `Factory` interface

### Claim: `Factory(::MyMetadataService)` and `Factory { session -> ... }` are both supported because `Factory` is a `fun interface`
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirExtensionSessionComponent.kt:26`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirExtensionSessionComponent.kt#L26)
- **Snippet**:
  ```kotlin
  fun interface Factory : FirExtension.Factory<FirExtensionSessionComponent>
  ```

## `FirSession.sessionComponentAccessor()` companion

### Claim: "by FirSession.sessionComponentAccessor() extension property at file top-level is the canonical accessor"
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/FirSession.kt:20`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/FirSession.kt#L20)
- **Snippet**:
  ```kotlin
  inline fun <reified T : FirSessionComponent> sessionComponentAccessor(): ArrayMapAccessor<FirSessionComponent, FirSessionComponent, T> {
      return generateAccessor(T::class)
  }
  ```

## `firCachesFactory.createCache { key, ctx -> ... }` (two-arg)

### Claim: "`firCachesFactory.createCache { key, ctx -> compute }` keyed cache (two-arg lambda, generic context)"
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/caches/FirCachesFactory.kt:27`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/caches/FirCachesFactory.kt#L27)
- **Snippet**:
  ```kotlin
  abstract fun <K : Any, V, CONTEXT> createCache(createValue: (K, CONTEXT) -> V): FirCache<K, V, CONTEXT>
  ```

## `firCachesFactory.createCache { key -> ... }` (single-arg)

### Claim: "`firCachesFactory.createCache { key -> compute }` (single-arg form) — keyed cache without context"
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/caches/FirCachesFactory.kt:139-143`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/caches/FirCachesFactory.kt#L139-L143)
- **Snippet**:
  ```kotlin
  inline fun <K : Any, V> FirCachesFactory.createCache(
      crossinline createValue: (K) -> V,
  ): FirCache<K, V, Nothing?> = createCache(
      createValue = { key, _ -> createValue(key) },
  )
  ```

## `firCachesFactory.createCacheWithPostCompute`

### Claim: "two-phase: `createValue` produces `Pair<V, DATA>`, then `postCompute` runs with the stored `V` and `DATA`"
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/caches/FirCachesFactory.kt:62-65`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/caches/FirCachesFactory.kt#L62-L65)
- **Snippet**:
  ```kotlin
  abstract fun <K : Any, V, CONTEXT, DATA> createCacheWithPostCompute(
      createValue: (K, CONTEXT) -> Pair<V, DATA>,
      postCompute: (K, V, DATA) -> Unit
  ): FirCache<K, V, CONTEXT>
  ```

## `firCachesFactory.createCacheWithSuggestedLimits`

### Claim: "bounded cache with eviction policy and reference-strength controls"
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/caches/FirCachesFactory.kt:115-121`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/caches/FirCachesFactory.kt#L115-L121)
- **Snippet**:
  ```kotlin
  abstract fun <K : Any, V, CONTEXT> createCacheWithSuggestedLimits(
      expirationAfterAccess: Duration? = null,
      maximumSize: Long? = null,
      keyStrength: KeyReferenceStrength = KeyReferenceStrength.STRONG,
      valueStrength: ValueReferenceStrength = ValueReferenceStrength.STRONG,
      createValue: (K, CONTEXT) -> V,
  ): FirCache<K, V, CONTEXT>
  ```

## `firCachesFactory.createLazyValue`

### Claim: "`firCachesFactory.createLazyValue { compute }` — one-shot lazy"
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/caches/FirCachesFactory.kt:123`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/caches/FirCachesFactory.kt#L123)
- **Snippet**:
  ```kotlin
  abstract fun <V> createLazyValue(createValue: () -> V): FirLazyValue<V>
  ```

## `firCachesFactory.createPossiblySoftLazyValue`

### Claim: "`firCachesFactory.createPossiblySoftLazyValue { compute }` — one-shot lazy with soft-reference reclamation"
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/caches/FirCachesFactory.kt:134`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/caches/FirCachesFactory.kt#L134)
- **Snippet**:
  ```kotlin
  abstract fun <V> createPossiblySoftLazyValue(createValue: () -> V): FirLazyValue<V>
  ```

## "no `createPossiblyValueClassCache`" verification

### Claim: "There is no special 'value-class cache' helper"
- **Verification**: `grep -rn "createPossiblyValueClassCache\|createValueClassCache" kotlin/compiler/fir/` returns no matches.
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/caches/FirCachesFactory.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/caches/FirCachesFactory.kt) (entire `FirCachesFactory` class body lines 12-135 — only `createCache`, `createCacheWithPostCompute`, `createCacheWithSuggestedLimits`, `createLazyValue`, `createPossiblySoftLazyValue` are declared).

## `AbstractSimpleClassPredicateMatchingService` location

### Claim: "`AbstractSimpleClassPredicateMatchingService` in `compiler/fir/providers/src/.../utils/`. Allopen and noarg both extend it."
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/utils/AbstractSimpleClassPredicateMatchingService.kt:23`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/utils/AbstractSimpleClassPredicateMatchingService.kt#L23)
- **Snippet**:
  ```kotlin
  abstract class AbstractSimpleClassPredicateMatchingService(session: FirSession) : FirExtensionSessionComponent(session) {
  ```
- **Noarg subclass**: [`kotlin/plugins/noarg/noarg.k2/src/org/jetbrains/kotlin/noarg/fir/NoArgAnnotationNameProvider.kt:16`](https://github.com/JetBrains/kotlin/blob/v2.4.0/plugins/noarg/noarg.k2/src/org/jetbrains/kotlin/noarg/fir/NoArgAnnotationNameProvider.kt#L16) — `class FirNoArgPredicateMatcher(session, ...) : AbstractSimpleClassPredicateMatchingService(session)`

## `Factory.getFactory(...)` parameterised pattern

### Claim: "`Factory { session -> MyMatcher(session, annotationFqns) }` — pattern from `NoArgAnnotationNameProvider.kt`"
- **File**: [`kotlin/plugins/noarg/noarg.k2/src/org/jetbrains/kotlin/noarg/fir/NoArgAnnotationNameProvider.kt:17-21`](https://github.com/JetBrains/kotlin/blob/v2.4.0/plugins/noarg/noarg.k2/src/org/jetbrains/kotlin/noarg/fir/NoArgAnnotationNameProvider.kt#L17-L21)
- **Snippet**:
  ```kotlin
  companion object {
      fun getFactory(noArgAnnotationFqNames: List<String>): Factory {
          return Factory { session -> FirNoArgPredicateMatcher(session, noArgAnnotationFqNames) }
      }
  }
  ```

### Claim: same pattern in `LombokService`
- **File**: [`kotlin/plugins/lombok/lombok.k2/src/org/jetbrains/kotlin/lombok/k2/config/LombokService.kt:34-38`](https://github.com/JetBrains/kotlin/blob/v2.4.0/plugins/lombok/lombok.k2/src/org/jetbrains/kotlin/lombok/k2/config/LombokService.kt#L34-L38)
- **Snippet**:
  ```kotlin
  companion object {
      fun getFactory(configFile: File?): Factory {
          return Factory { LombokService(it, configFile) }
      }
  }
  ```
