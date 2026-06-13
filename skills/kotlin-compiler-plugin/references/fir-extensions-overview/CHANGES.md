# Changes affecting this skill

API migrations relevant to the FIR extension architecture overview. This skill targets the **current stable Kotlin** (2.4.0).

## Kotlin 2.3 → 2.4

### `AVAILABLE_EXTENSIONS` dropped from 18 to 17 entries

`FirReplSnippetResolveExtension::class` was entry 15 in `FirExtensionRegistrar.AVAILABLE_EXTENSIONS` through 2.3.x; in 2.4.0 it is **no longer registered** there and no longer appears anywhere in `FirExtensionRegistrar.kt`. The list is now 17 entries (verified against `FirExtensionRegistrar.kt:23-43` at v2.4.0). If you relied on registering a `FirReplSnippetResolveExtension` through the registrar DSL, re-check `fir-repl-snippet-extensions` for the 2.4.0 REPL-resolution path.

### Line-number drift in `FirExtensionRegistrar.kt`

The whole file shifted up by ~17 lines (the list and the `unaryPlus` DSL block both moved). EVIDENCE.md citations were re-pinned to v2.4.0 line numbers (`AVAILABLE_EXTENSIONS` 23-43, `ALLOWED_EXTENSIONS_FOR_LIBRARY_SESSION` 45-48, `registerDiagnosticContainers` 234-236, `registeredPluginAnnotations.initialize()` 323).

## Kotlin 2.2 → 2.3

### Catalog growth and stability

`FirExtensionRegistrar.AVAILABLE_EXTENSIONS` has **18 entries** at v2.3.21 (verified against source `FirExtensionRegistrar.kt:29-50`). `FirReplSnippetResolveExtension::class` (entry 15) was added in the 2.1 cycle — confirmed absent at v2.0.21 and present at v2.1.20. The list is expected to grow over time as new extension points are added — when bumping to a newer Kotlin, re-check this list and add any new entries to the catalog table in the guide.md.

### `FirAdditionalCheckersExtension.check` context parameters

(Detailed in `fir-additional-checkers-extension/CHANGES.md`.) The signature of every checker base in `FirAdditionalCheckersExtension`'s buckets moved to context parameters in 2.3. This affects how all checker overrides are written. The overview's example snippets reflect the 2.3.x form; if you find code from older guides showing `check(declaration, context, reporter)`, that won't override in 2.3.x.

## Predicate-system clarifications (cosmetic across recent versions)

### DSL helper names are `parentAnnotated`, not `parentHasAnnotated`

Some early third-party documentation (and some early drafts of these skills) used `parentHasAnnotated`. The actual DSL function is `parentAnnotated` (and `ancestorAnnotated`, `hasAnnotated` — three distinct helpers). `parentHasAnnotated` does not exist.

### Predicates registered by *any* extension share a session-wide index

Extensions use `FirExtension.registerPredicates()` to contribute FQNs to a session-wide annotation index (`FirRegisteredPluginAnnotations`, see `FirRegisteredPluginAnnotations.initialize()` source). Once any extension registers an FQN, predicates over that FQN are queryable from any other extension in the session via `session.predicateBasedProvider`. Earlier framings that suggested per-extension isolation of registered predicates were inaccurate.
