---
name: fir-predicate-system
description: Use Kotlin K2's declarative predicate DSL to match annotated declarations efficiently — DeclarationPredicate vs LookupPredicate, the BuilderContext DSL (annotated/parentAnnotated/ancestorAnnotated/hasAnnotated/metaAnnotated/and/or), registering predicates via FirExtension.registerPredicates(), and querying via session.predicateBasedProvider. Use when a FIR extension needs to find or filter by annotation FQN. Read fir-extensions-overview first. NOT a how-to for any specific extension — see per-extension fir-* skills which use predicates.
---

# FIR Predicate System

A FIR extension that does *anything* annotation-driven (e.g. "for every `@Serializable` class, …") should use the predicate system rather than walking annotations manually. Predicates are declared up-front by each extension; the FIR pipeline indexes annotated declarations once and answers `matches`/`getSymbolsByPredicate` in O(1) per hit.

Source: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate/`](https://github.com/JetBrains/kotlin/tree/v2.3.21/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/predicate).

## Two predicate flavours

| Type | When to use | Where |
|---|---|---|
| `DeclarationPredicate` | "Does *this* declaration match?" — used inside checkers, generators, status transformers | `DeclarationPredicate.kt` |
| `LookupPredicate` | "Find all declarations matching this" — used to enumerate annotated symbols | `LookupPredicate.kt` |

Both share the same shape (annotation FQNs + Or/And) but `DeclarationPredicate` additionally supports `metaAnnotated` (annotations on annotations). They are not interchangeable — pick by the question you're asking.

## DSL primitives

Inside the `BuilderContext` (the receiver of `create { ... }` and `registerPredicates { ... }`):

| Builder | Matches a declaration when… |
|---|---|
| `annotated(fqn, ...)` | the declaration itself has any of the given annotations |
| `parentAnnotated(fqn, ...)` | its **direct** containing declaration has any of the given annotations |
| `ancestorAnnotated(fqn, ...)` | **any** containing declaration up the chain has any of the given annotations |
| `hasAnnotated(fqn, ...)` | one of its **child** declarations has any of the given annotations |
| `annotatedOrUnder(fqn, ...)` | shorthand for `annotated(...) or ancestorAnnotated(...)` |
| `metaAnnotated(fqn, ..., includeItself: Boolean)` | the declaration is annotated with an annotation that is itself annotated with one of these. **`includeItself` is required** (no default). Set `true` to also match declarations directly annotated with the listed FQNs; `false` to match only declarations annotated with annotations annotated with the listed FQNs. Only on `DeclarationPredicate` — `LookupPredicate` doesn't expose meta-annotation matching. |
| `a or b` | either side matches |
| `a and b` | both sides match |

Each helper has both varargs and `Collection<AnnotationFqn>` overloads. `AnnotationFqn` is a typealias for `FqName`.

## Building a predicate

Two equivalent forms:

```kotlin
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.name.FqName

private val SERIALIZABLE = FqName("kotlinx.serialization.Serializable")
private val MY_OPEN = FqName("com.example.Open")

// 1. Builder lambda (idiomatic)
val classMatchesEither = DeclarationPredicate.create {
    annotated(SERIALIZABLE) or annotated(MY_OPEN)
}

// 2. Direct construction (verbose, rare)
val classMatchesEither2 = DeclarationPredicate.Or(
    DeclarationPredicate.AnnotatedWith(setOf(SERIALIZABLE)),
    DeclarationPredicate.AnnotatedWith(setOf(MY_OPEN)),
)
```

Real-world predicates are almost always built through `create { ... }`.

## Registering predicates from an extension

Every `FirExtension` subclass exposes:

```kotlin
override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(MY_PREDICATE)
}
```

This is *not* the same as `configurePlugin()` on the registrar — it lives on the **extension itself**.

`registerPredicates()` is called once per session, on **every** extension in the session. The FIR pipeline (`FirRegisteredPluginAnnotations.initialize()`, source: `kotlin/compiler/fir/resolve/src/.../FirRegisteredPluginAnnotations.kt:97-120`) iterates `session.extensionService.getAllExtensions()` and aggregates every registered predicate's annotation FQNs into a **single session-wide index**.

Implications:
- Annotation FQNs registered by **any** extension become queryable by **any other** extension in the same session. So if `ExtensionA` registers FQN `@Foo` and `ExtensionB` builds a `DeclarationPredicate.create { annotated(FOO_FQN) }`, `ExtensionB` can call `session.predicateBasedProvider.matches(...)` against that predicate even without itself overriding `registerPredicates()`.
- What you **must** ensure is that the FQNs your predicates depend on are registered by *some* extension. If no extension in the session registers a given FQN, the index is missing it and matches against that FQN return empty.
- The conventional pattern: every extension that uses a predicate also registers its FQNs via its own `registerPredicates()`. This makes each extension self-contained and avoids cross-plugin "ghost dependencies" where ExtensionA silently relies on ExtensionB to register FQNs.

## Querying matches at runtime

Inside an extension method (`check`, `generateClassLikeDeclaration`, etc.) you query through the session:

```kotlin
val matched: Boolean = session.predicateBasedProvider.matches(MY_PREDICATE, declaration)
val symbols: List<FirBasedSymbol<*>> = session.predicateBasedProvider.getSymbolsByPredicate(MY_PREDICATE)
```

`matches(...)` checks a single declaration. `getSymbolsByPredicate(...)` returns every declaration in the current source session that matches. Both are O(1) per query relative to the indexed predicate's hit set.

`predicateBasedProvider` is a `FirSession` extension property. Don't try to construct it yourself.

## Canonical pattern: pre-defined predicate fields

Real plugins (allopen, noarg, kotlinx-serialization) define their predicates as `private val` fields on the extension class so they are stable across all uses:

```kotlin
class MyChecker(session: FirSession) : FirAdditionalCheckersExtension(session) {

    companion object {
        private val MUST_BE_FINAL_FQN = FqName("com.example.MustBeFinal")
        private val MUST_BE_FINAL_PREDICATE = DeclarationPredicate.create {
            annotated(MUST_BE_FINAL_FQN)
        }
    }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(MUST_BE_FINAL_PREDICATE)
    }

    override val declarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers = setOf(
            object : FirRegularClassChecker(MppCheckerKind.Common) {
                context(context: CheckerContext, reporter: DiagnosticReporter)
                override fun check(declaration: FirRegularClass) {
                    if (!context.session.predicateBasedProvider.matches(MUST_BE_FINAL_PREDICATE, declaration)) return
                    // … report diagnostic
                }
            }
        )
    }
}
```

Same predicate object is registered once and queried many times; instance equality is what the index uses.

## Helper: AbstractSimpleClassPredicateMatchingService

For the very common "is this class (or any supertype) annotated with my marker?" question, the compiler ships a helper base class:

```kotlin
// kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/utils/AbstractSimpleClassPredicateMatchingService.kt
abstract class AbstractSimpleClassPredicateMatchingService(session: FirSession) : FirExtensionSessionComponent(session) {
    protected abstract val predicate: DeclarationPredicate
    fun isAnnotated(symbol: FirRegularClassSymbol): Boolean   // checks self + all resolved supertypes
    // (the base also overrides registerPredicates() so subclasses just supply `predicate`)
}
```

Note the symbol type is `FirRegularClassSymbol` (not `FirClassLikeSymbol<*>`). The single `isAnnotated` method walks self plus the supertype chain. There is no separate `matches` method — that name belongs to `FirPredicateBasedProvider.matches(...)`.

Allopen and noarg both extend this for their `@Open` / `@NoArg` matching. If your plugin already needs a `FirExtensionSessionComponent` for shared state, extending this base saves boilerplate.

## Example: lookup all annotated declarations

```kotlin
override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(LOOKUP_FOR_GENERATOR)
}

override fun generateClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
    val annotated = session.predicateBasedProvider.getSymbolsByPredicate(LOOKUP_FOR_GENERATOR)
    annotated.forEach { /* generate something for each */ }
    return null
}
```

Note: `getSymbolsByPredicate` requires `LookupPredicate` (not `DeclarationPredicate`). Build it via:

```kotlin
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate

val LOOKUP_FOR_GENERATOR: LookupPredicate = LookupPredicate.create {
    annotated(MY_GENERATOR_TRIGGER_FQN)
}
```

Use `DeclarationPredicate` for `matches`, `LookupPredicate` for `getSymbolsByPredicate`. Both `BuilderContext`s are nearly identical, so converting between them is mechanical.

**The register-side has to stay in `DeclarationPredicate` terms.** `FirDeclarationPredicateRegistrar.register(...)` only takes a `DeclarationPredicate`; there is no `LookupPredicate` overload. The session-wide annotation index is populated from those `DeclarationPredicate` FQNs, and `getSymbolsByPredicate(LookupPredicate)` then queries that same index. So the typical "enumerate annotated declarations" plugin keeps **two predicates over the same FQN** — a `DeclarationPredicate` to register (so the FQN ends up in the session index) and a `LookupPredicate` to pass to `getSymbolsByPredicate`. Skipping the `DeclarationPredicate` registration makes `getSymbolsByPredicate` return an empty set silently, because the FQN was never indexed:

```kotlin
private val MARKER_FQN = FqName("com.example.Marker")
private val MARKER_DECL  = DeclarationPredicate.create { annotated(MARKER_FQN) }
private val MARKER_LOOKUP = LookupPredicate.create { annotated(MARKER_FQN) }

override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(MARKER_DECL)              // populates the session-wide index
}

// later: session.predicateBasedProvider.getSymbolsByPredicate(MARKER_LOOKUP)
```

## Common gotchas

### No extension registered the FQN — matches return empty silently

The session-wide annotation index aggregates FQNs from every extension's `registerPredicates()`. If *no* extension in the session registers a given FQN, the index is missing it and `matches`/`getSymbolsByPredicate` against that FQN return empty without warning. Override `registerPredicates()` on the extension class itself (not on the registrar's `configurePlugin()`) — the receiver there is `FirDeclarationPredicateRegistrar`, which is where `register(predicate)` lives.

### Empty annotation set throws at construction

```kotlin
DeclarationPredicate.AnnotatedWith(emptySet())  // IllegalArgumentException: "Annotations should be not empty"
```

Build predicates from a non-empty annotation list, even if your "list" is computed dynamically.

### `matches` returns false for synthetic declarations

`FirPredicateBasedProviderImpl.matches` checks the declaration's *own* annotation list (see [`compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirPredicateBasedProviderImpl.kt`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirPredicateBasedProviderImpl.kt) — `visitAnnotatedWith` calls `matchWith(data, predicate.annotations)`). The `*BuildingContext` helpers in `FirDeclarationGenerationExtension` don't copy annotations from the originating user declaration, so plugin-synthesised declarations carry an empty `annotations` list by default and won't match an `annotated(FQN)` predicate. Either attach the annotation explicitly when generating, or short-circuit by checking `declaration.origin is FirDeclarationOrigin.Plugin` before querying the predicate.

### `parentAnnotated` vs `ancestorAnnotated`

- `parentAnnotated(X)` matches if the **immediate** container is annotated with X
- `ancestorAnnotated(X)` matches if **any** container up the chain is annotated with X

These are easy to confuse. Prefer `ancestorAnnotated` when you want "anywhere above"; `parentAnnotated` for the strict-parent case.

### `hasAnnotated` ≠ `annotated`

- `annotated(X)` — *this declaration* is annotated with X
- `hasAnnotated(X)` — *some child* of this declaration is annotated with X

Class with `hasAnnotated(@Serializer)` matches when a method inside it is `@Serializer`. The naming is unfortunate; check intent twice.

### `metaAnnotated` is `DeclarationPredicate`-only

`LookupPredicate` does not have `metaAnnotated`. If you need meta-annotation matching at lookup time, you have to enumerate the meta-annotated annotations first and build a `LookupPredicate.annotated(...)` over the result.

### One predicate per session, not per declaration

`PredicateBasedProvider` indexes per session. The same predicate object queried across many `check(...)` calls within one compilation reuses the same index. Don't construct new predicates inside checker bodies — define them as `companion object` constants.

### `AnnotationFqn` ≠ `ClassId`

`AnnotationFqn` is `FqName` (the dotted package + class name string). The `ClassId` API used elsewhere is *not* what predicates take. Convert with `classId.asSingleFqName()` if needed.

## Relation to other extensions

- Use predicates in **`FirAdditionalCheckersExtension`** to filter the declarations you check.
- Use predicates in **`FirDeclarationGenerationExtension`** to find which user types should get synthesised members.
- Use predicates in **`FirSupertypeGenerationExtension`** to decide which classes need extra supertypes.
- Use predicates in **`FirStatusTransformerExtension`** to decide which declarations get modifier rewrites.

## What this skill does NOT cover

- The full `PredicateVisitor` API (used internally by the FIR pipeline, not by plugin authors)
- IDE-side equivalents in the Analysis API
- How `FirRegisteredPluginAnnotations` consumes the registered predicates internally
