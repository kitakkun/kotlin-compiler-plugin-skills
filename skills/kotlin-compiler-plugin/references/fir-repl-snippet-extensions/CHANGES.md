# Changes affecting this skill

API migrations relevant to the REPL-snippet FIR/Fir2Ir extensions. This skill targets the **current stable Kotlin** (2.4.20).

## Kotlin 2.3 → 2.4

### `FirReplSnippetResolveExtension` became a `FirExtensionSessionComponent`

Through 2.3.x, `FirReplSnippetResolveExtension` was a standalone `FirExtension`: it had its own `companion object { NAME }`, `name`/`extensionType` overrides, a `Factory` interface, and a dedicated `AVAILABLE_EXTENSIONS` entry. It was accessed via `FirExtensionService.replSnippetResolveExtensions: List<…>`.

In 2.4.0 (`FirReplSnippetResolveExtension.kt:17-30` at v2.4.0):

- The base class is now `FirExtensionSessionComponent(session)` instead of `FirExtension(session)`.
- The `NAME`/`name`/`extensionType`/`Factory` members are gone; a single `override val componentClass` replaces them.
- It is **no longer listed in `FirExtensionRegistrar.AVAILABLE_EXTENSIONS`** (which dropped from 18 to 17 entries — see `fir-extensions-overview/CHANGES.md`).
- Access changed from the `replSnippetResolveExtensions: List<…>` extension on `FirExtensionService` to a single nullable accessor `val FirSession.replSnippetResolveExtension: FirReplSnippetResolveExtension? by FirSession.nullableSessionComponentAccessor()`.

What did **not** change: the three abstract methods (`getSnippetDefaultImports`, `getSnippetScope`, `updateResolved`), so a concrete subclass and its overrides compile unchanged. Registration via the `+::MyReplResolver` DSL still works — it now resolves through the `FirExtensionSessionComponent` `unaryPlus` overload rather than a dedicated one.

The other two REPL extensions (`FirReplSnippetConfiguratorExtension`, `Fir2IrReplSnippetConfiguratorExtension`) are unchanged and remain in `AVAILABLE_EXTENSIONS`.
