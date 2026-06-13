# Evidence for guide.md

Primary-source citations against `kotlin/` (i.e. the Kotlin compiler tree at `/Users/kitakkun/Documents/GitHub/kotlin-lang/`). Paths are written in `kotlin/<path>:line` form.

## DSL helper signatures (DeclarationPredicate)

### Claim: `annotated(fqn, ...)` varargs + `Collection<AnnotationFqn>` overload
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt:118`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt#L118)
- **Snippet**:
  ```kotlin
  override fun annotated(vararg annotations: AnnotationFqn): DeclarationPredicate = annotated(annotations.toList())
  ```
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt:130`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt#L130)
- **Snippet**:
  ```kotlin
  override fun annotated(annotations: Collection<AnnotationFqn>): DeclarationPredicate = AnnotatedWith(annotations.toSet())
  ```

### Claim: `parentAnnotated(fqn, ...)`
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt:120`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt#L120)
- **Snippet**:
  ```kotlin
  override fun parentAnnotated(vararg annotations: AnnotationFqn): DeclarationPredicate = parentAnnotated(annotations.toList())
  ```
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt:134`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt#L134)

### Claim: `ancestorAnnotated(fqn, ...)`
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt:119`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt#L119)
- **Snippet**:
  ```kotlin
  override fun ancestorAnnotated(vararg annotations: AnnotationFqn): DeclarationPredicate = ancestorAnnotated(annotations.toList())
  ```
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt:131`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt#L131)

### Claim: `hasAnnotated(fqn, ...)`
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt:121`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt#L121)
- **Snippet**:
  ```kotlin
  override fun hasAnnotated(vararg annotations: AnnotationFqn): DeclarationPredicate = hasAnnotated(annotations.toList())
  ```
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt:137`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt#L137)

### Claim: `annotatedOrUnder(fqn, ...)` — shorthand for `annotated(...) or ancestorAnnotated(...)`
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt:123-124`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt#L123-L124)
- **Snippet**:
  ```kotlin
  override fun annotatedOrUnder(vararg annotations: AnnotationFqn): DeclarationPredicate =
      annotated(*annotations) or ancestorAnnotated(*annotations)
  ```

### Claim: `metaAnnotated(fqn, ..., includeItself: Boolean)` — required `includeItself`, no default
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt:126-127`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt#L126-L127)
- **Snippet**:
  ```kotlin
  fun metaAnnotated(vararg metaAnnotations: AnnotationFqn, includeItself: Boolean): DeclarationPredicate =
      MetaAnnotatedWith(metaAnnotations.toSet(), includeItself)
  ```
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt:142-143`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt#L142-L143)
- **Snippet**:
  ```kotlin
  fun metaAnnotated(metaAnnotations: Collection<AnnotationFqn>, includeItself: Boolean): DeclarationPredicate =
      MetaAnnotatedWith(metaAnnotations.toSet(), includeItself)
  ```
  (Both overloads declare `includeItself: Boolean` with no default value.)

## `or` / `and` infix combinators

### Claim: `a or b`, `a and b`
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt:114-115`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt#L114-L115)
- **Snippet**:
  ```kotlin
  override infix fun DeclarationPredicate.or(other: DeclarationPredicate): DeclarationPredicate = Or(this, other)
  override infix fun DeclarationPredicate.and(other: DeclarationPredicate): DeclarationPredicate = And(this, other)
  ```
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/LookupPredicate.kt:83-84`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/LookupPredicate.kt#L83-L84)
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/AbstractPredicate.kt:227-228`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/AbstractPredicate.kt#L227-L228) (abstract declaration on `BuilderContext<P>`)

## `DeclarationPredicate.create { ... }` and `LookupPredicate.create { ... }`

### Claim: `DeclarationPredicate.create { ... }`
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt:146-148`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt#L146-L148)
- **Snippet**:
  ```kotlin
  companion object {
      inline fun create(init: BuilderContext.() -> DeclarationPredicate): DeclarationPredicate = BuilderContext.init()
  }
  ```

### Claim: `LookupPredicate.create { ... }`
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/LookupPredicate.kt:104-106`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/LookupPredicate.kt#L104-L106)
- **Snippet**:
  ```kotlin
  companion object {
      inline fun create(init: BuilderContext.() -> LookupPredicate): LookupPredicate = BuilderContext.init()
  }
  ```

## `LookupPredicate` lacks `metaAnnotated`

### Claim: "`LookupPredicate` doesn't expose meta-annotation matching."
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/LookupPredicate.kt:14-17`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/LookupPredicate.kt#L14-L17)
- **Snippet**:
  ```kotlin
  sealed class LookupPredicate : AbstractPredicate<LookupPredicate> {
      abstract override val annotations: Set<AnnotationFqn>
      final override val metaAnnotations: Set<AnnotationFqn>
          get() = emptySet()
  ```
  `metaAnnotations` is hard-coded to `emptySet()`, and `LookupPredicate.BuilderContext` (lines 82-102) defines no `metaAnnotated` helper. Confirmed by KDoc on `MetaAnnotatedWith`:
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/AbstractPredicate.kt:213-214`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/AbstractPredicate.kt#L213-L214)
- **Snippet**:
  ```
  Note that [MetaAnnotatedWith] predicate has no implementation in [LookupPredicate] hierarchy
    and cannot be used for global lookup
  ```

## Empty annotation set throws

### Claim: "`DeclarationPredicate.AnnotatedWith(emptySet())` throws `IllegalArgumentException: Annotations should be not empty`"
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt:48-54`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt#L48-L54)
- **Snippet**:
  ```kotlin
  sealed class Annotated(final override val annotations: Set<AnnotationFqn>) : DeclarationPredicate(),
      AbstractPredicate.Annotated<DeclarationPredicate> {
      init {
          require(annotations.isNotEmpty()) {
              "Annotations should be not empty"
          }
      }
  ```
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt:97-101`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/DeclarationPredicate.kt#L97-L101) (same `require` for `MetaAnnotatedWith`)
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/LookupPredicate.kt:44-48`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/LookupPredicate.kt#L44-L48) (same `require` in `LookupPredicate.Annotated`)

## `FirExtension.registerPredicates()` location

### Claim: "Every `FirExtension` subclass exposes `override fun FirDeclarationPredicateRegistrar.registerPredicates() { ... }`"
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirExtension.kt:25-35`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirExtension.kt#L25-L35)
- **Snippet**:
  ```kotlin
  abstract class FirExtension(val session: FirSession) {
      ...
      open fun FirDeclarationPredicateRegistrar.registerPredicates() {}
  }
  ```
  The hook is declared on `FirExtension` itself (not on a registrar/configurePlugin).

## `FirDeclarationPredicateRegistrar.register(predicate)` API

### Claim: `register(vararg predicates)` and `register(predicates: Collection<...>)`
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirExtension.kt:42-45`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirExtension.kt#L42-L45)
- **Snippet**:
  ```kotlin
  abstract class FirDeclarationPredicateRegistrar {
      abstract fun register(vararg predicates: AbstractPredicate<*>)
      abstract fun register(predicates: Collection<AbstractPredicate<*>>)
  }
  ```

## Session-wide aggregation in `FirRegisteredPluginAnnotations.initialize()`

### Claim: "iterates `session.extensionService.getAllExtensions()` and aggregates every registered predicate's annotation FQNs into a single session-wide index"
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirRegisteredPluginAnnotations.kt:97-120`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirRegisteredPluginAnnotations.kt#L97-L120)
- **Snippet**:
  ```kotlin
  @PluginServicesInitialization
  final override fun initialize() {
      val registrar = object : FirDeclarationPredicateRegistrar() {
          val predicates = mutableListOf<AbstractPredicate<*>>()
          override fun register(vararg predicates: AbstractPredicate<*>) {
              this.predicates += predicates
          }

          override fun register(predicates: Collection<AbstractPredicate<*>>) {
              this.predicates += predicates
          }
      }

      for (extension in session.extensionService.getAllExtensions()) {
          with(extension) {
              registrar.registerPredicates()
          }
      }

      for (predicate in registrar.predicates) {
          saveAnnotationsFromPlugin(predicate.annotations)
          metaAnnotations += predicate.metaAnnotations
      }
  }
  ```

## `FirPredicateBasedProvider.matches` / `getSymbolsByPredicate` signatures

### Claim: `getSymbolsByPredicate(predicate: LookupPredicate): List<FirBasedSymbol<*>>`
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirPredicateBasedProvider.kt:25`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirPredicateBasedProvider.kt#L25)
- **Snippet**:
  ```kotlin
  abstract fun getSymbolsByPredicate(predicate: LookupPredicate): List<FirBasedSymbol<*>>
  ```

### Claim: `matches(predicate: AbstractPredicate<*>, declaration: FirDeclaration): Boolean`
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirPredicateBasedProvider.kt:41`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirPredicateBasedProvider.kt#L41)
- **Snippet**:
  ```kotlin
  abstract fun matches(predicate: AbstractPredicate<*>, declaration: FirDeclaration): Boolean
  ```
  And the `FirBasedSymbol` overload:
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirPredicateBasedProvider.kt:46-48`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirPredicateBasedProvider.kt#L46-L48)
- **Snippet**:
  ```kotlin
  fun matches(predicate: AbstractPredicate<*>, declaration: FirBasedSymbol<*>): Boolean {
      return matches(predicate, declaration.fir)
  }
  ```

### Claim: `predicateBasedProvider` is a `FirSession` extension property
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirPredicateBasedProvider.kt:82`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirPredicateBasedProvider.kt#L82)
- **Snippet**:
  ```kotlin
  val FirSession.predicateBasedProvider: FirPredicateBasedProvider by FirSession.sessionComponentAccessor()
  ```

## `AbstractSimpleClassPredicateMatchingService` — single `isAnnotated` (not a separate `matches`)

### Claim: "single `isAnnotated` method walks self plus the supertype chain. There is no separate `matches` method."
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/utils/AbstractSimpleClassPredicateMatchingService.kt:23-45`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/utils/AbstractSimpleClassPredicateMatchingService.kt#L23-L45)
- **Snippet**:
  ```kotlin
  abstract class AbstractSimpleClassPredicateMatchingService(session: FirSession) : FirExtensionSessionComponent(session) {
      protected abstract val predicate: DeclarationPredicate

      final override fun FirDeclarationPredicateRegistrar.registerPredicates() {
          register(predicate)
      }

      fun isAnnotated(symbol: FirRegularClassSymbol): Boolean {
          return cache.getValue(symbol)
      }

      private val cache: FirCache<FirRegularClassSymbol, Boolean, Nothing?> = session.firCachesFactory.createCache { symbol, _ ->
          symbol.annotated()
      }

      private fun FirRegularClassSymbol.annotated(): Boolean {
          if (session.predicateBasedProvider.matches(predicate, this)) return true
          return resolvedSuperTypes.any {
              val superSymbol = it.toRegularClassSymbol(session) ?: return@any false
              cache.getValue(superSymbol)
          }
      }
  }
  ```
  The class exposes only `isAnnotated(FirRegularClassSymbol): Boolean` publicly. The internal `matches` invocation is on `session.predicateBasedProvider` (a different type: `FirPredicateBasedProvider`).

## `getSymbolsByPredicate` return type `List<FirBasedSymbol<*>>`

### Claim: return type is `List<FirBasedSymbol<*>>`
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirPredicateBasedProvider.kt:25`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirPredicateBasedProvider.kt#L25)
- **Snippet**:
  ```kotlin
  abstract fun getSymbolsByPredicate(predicate: LookupPredicate): List<FirBasedSymbol<*>>
  ```
  (Empty implementation at line 73 also returns `List<FirBasedSymbol<*>> = emptyList()`.)
