# 05-session-component — RESULT

## Outcome

**PASS.** `../gradlew :sample:run` produces:

```
w: .../Main.kt:4:7 [TRACKED_USAGE] Class is registered with the @Tracked session component
> Task :sample:run
Base
```

* The plugin compiles cleanly.
* The sample compiles cleanly. The only diagnostic is the expected
  `TRACKED_USAGE` warning emitted by `TrackedChecker` against `Base`.
* `Sub : Base()` would produce `FINAL_SUPERTYPE` without the
  `TrackedOpener` status transformer, but the sample succeeds — proof
  that the transformer flipped the modality.
* `main()` prints `Base`.

## Shared session component

`TrackedRegistry : FirExtensionSessionComponent` is the single source of
truth for which classes are `@Tracked`. It owns:

1. The `DeclarationPredicate.create { annotated(TRACKED_FQN) }` predicate,
   registered exactly once via `registerPredicates()`.
2. A `FirCache<FirClassLikeSymbol<*>, Boolean, Nothing?>` produced by
   `session.firCachesFactory.createCache { ... }` so the predicate match
   is computed once per symbol per session.
3. A `MutableSet<ClassId>` (the `Set<ClassId>` required by the spec) which
   accumulates the IDs of every class classified as tracked. Both
   consumer extensions populate this set transitively whenever they call
   `isTracked(symbol)`.

The accessor is exposed exactly as the skill prescribes:

```kotlin
val FirSession.trackedRegistry: TrackedRegistry by FirSession.sessionComponentAccessor()
```

`TrackedRegistry`, `TrackedChecker`, and `TrackedOpener` are all
registered in `TrackedExtensionRegistrar`:

```kotlin
override fun ExtensionRegistrarContext.configurePlugin() {
    +::TrackedRegistry
    +::TrackedChecker
    +::TrackedOpener
    registerDiagnosticContainers(TrackedDiagnostics)
}
```

## How we know both extensions consume the SAME component instance

`session.trackedRegistry` is resolved by `sessionComponentAccessor()`,
which reflects on the session's component map and returns *the* instance
of `TrackedRegistry::class` registered in this session. There is exactly
one registration in `TrackedExtensionRegistrar`, so by construction both
extensions read the same object — there is no second instance for the
accessor to return.

The behavioural evidence:

* `TrackedChecker` only fires `TRACKED_USAGE` when
  `session.trackedRegistry.isTracked(symbol)` returns `true`. The warning
  fires for `Base` ⇒ the predicate matched ⇒ the component returned
  `true` ⇒ the component's `MutableSet<ClassId>` now contains
  `Base`'s class ID.
* `TrackedOpener.needTransformStatus` calls
  `session.trackedRegistry.isTracked(declaration.symbol)`. It returned
  `true` for `Base` (the same class), so `transformStatus` flipped its
  modality from default-`null`-with-`FINAL`-default to OPEN via
  `copyWithNewDefaults(modality = OPEN, defaultModality = OPEN)`. Without
  this flip, `class Sub : Base()` would have raised `FINAL_SUPERTYPE` —
  it does not.
* Both effects are gated on **the same predicate result** — computed
  once and cached by the same `FirCache` inside the same component
  object. If the checker and the transformer were each holding their
  own copy of the data, only one of those two effects would happen on
  the first compile pass.

## Files

* `plugin/src/main/kotlin/com/example/session/TrackedRegistry.kt`
  – `FirExtensionSessionComponent`, predicate, cache, `Set<ClassId>`, accessor.
* `plugin/src/main/kotlin/com/example/session/TrackedChecker.kt`
  – `FirAdditionalCheckersExtension` consuming `session.trackedRegistry`.
* `plugin/src/main/kotlin/com/example/session/TrackedOpener.kt`
  – `FirStatusTransformerExtension` consuming `session.trackedRegistry`.
* `plugin/src/main/kotlin/com/example/session/TrackedDiagnostics.kt`
  – `KtDiagnosticsContainer` declaring `TRACKED_USAGE`.
* `plugin/src/main/kotlin/com/example/session/TrackedExtensionRegistrar.kt`
  – Registers the three extensions plus the diagnostic container.
* `plugin/src/main/kotlin/com/example/session/TrackedComponentRegistrar.kt`
  – `CompilerPluginRegistrar` plumbing.
* `sample/src/main/kotlin/Tracked.kt` – the `@Tracked` annotation.
* `sample/src/main/kotlin/Main.kt` – `@Tracked Base`, `Sub : Base()`,
  `main()` printing `"Base"`.

## Re-run on Kotlin 2.4.20

**Status: PASS** (unchanged from the 2.3.21 result; no source changes were needed, only the version pins in `build.gradle.kts`).

```
$ ../gradlew --no-daemon -q clean :sample:run
Base
(exit code 0)
```

Validated with Kotlin 2.4.20, Gradle 9.5.0, JDK 21 on 2026-09-10.
