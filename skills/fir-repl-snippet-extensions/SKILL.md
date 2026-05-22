---
name: fir-repl-snippet-extensions
description: Customise how Kotlin REPL snippets (individual cells of an interactive REPL, IDE evaluator, or Jupyter kernel) are compiled by registering one or more of three FIR/Fir2Ir extensions — `FirReplSnippetConfiguratorExtension` (decide which sources are snippets and shape the `FirReplSnippet`/eval-body before resolution), `FirReplSnippetResolveExtension` (inject default imports and the cross-snippet visibility scope so a later snippet sees earlier ones), and `Fir2IrReplSnippetConfiguratorExtension` (post-process the generated `IrReplSnippet` for the runtime evaluator). Also covers the `FirReplHistoryProvider` session component that tracks ordering. Read fir-extensions-overview first. NOT for ordinary `.kt` source — see `fir-declaration-generation-extension`. NOT for `.kts` scripts — see `fir-scripting-extensions`.
---

# FIR REPL Snippet Extensions

A **REPL snippet** is a single, self-contained input fragment evaluated against a growing history of previous snippets: a Jupyter notebook cell, an IDE "evaluate expression" entry, the response to a `>>>` prompt, etc. The compiler treats each snippet as a `FirReplSnippet` (not a `FirFile`, and not a `FirScript`). The three extensions in this skill control how snippets are built, resolved, and lowered to IR.

These are distinct from `.kts` scripts (see `fir-scripting-extensions`). Scripts compile a whole file independently; snippets need to *see each other* — `snippet2` may reference a `val` introduced by `snippet1`. The extension surface reflects that: configurators participate in the per-snippet shape; the resolve extension provides the scope linking snippets via the `FirReplHistoryProvider`.

## The three extensions plus the history provider

| Extension | Where it runs | What it does |
|---|---|---|
| `FirReplSnippetConfiguratorExtension` | Raw-FIR builder | Decide if a source is a REPL snippet (`isReplSnippetsSource`), shape the `FirReplSnippet` and its containing `FirFile`, and rewrite the snippet's eval-function body (`MutableList<FirStatement>.configure`) |
| `FirReplSnippetResolveExtension` | Resolve phase | Inject default imports per snippet and return the **cross-snippet scope** that exposes earlier snippets' declarations to the current one |
| `Fir2IrReplSnippetConfiguratorExtension` | FIR → IR conversion | Mutate the generated `IrReplSnippet` (typically to wire it into the runtime evaluator's frame / dispatch class) |

There is also a session component:

| Component | Role |
|---|---|
| `FirReplHistoryProvider` (abstract `FirSessionComponent`) | Maintains the ordered list of `FirReplSnippetSymbol`s in this session and answers `isFirstSnippet(symbol)` / `getSnippetCount()`. Plugins implementing the resolve extension need this to enumerate the predecessors |

## 1. `FirReplSnippetConfiguratorExtension`

```kotlin
package com.example.repl.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.KtSourceFile
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.builder.Context
import org.jetbrains.kotlin.fir.builder.FirReplSnippetConfiguratorExtension
import org.jetbrains.kotlin.fir.declarations.builder.FirFileBuilder
import org.jetbrains.kotlin.fir.declarations.builder.FirReplSnippetBuilder
import org.jetbrains.kotlin.fir.expressions.FirStatement

class MyReplConfigurator(session: FirSession) : FirReplSnippetConfiguratorExtension(session) {

    override fun isReplSnippetsSource(sourceFile: KtSourceFile?, scriptSource: KtSourceElement): Boolean =
        sourceFile?.name?.endsWith(".my.repl.kts") == true

    override fun FirReplSnippetBuilder.configureContainingFile(fileBuilder: FirFileBuilder) {
        // Most plugins leave this empty; the snippet's containing FirFile rarely needs edits.
    }

    override fun FirReplSnippetBuilder.configure(sourceFile: KtSourceFile?, context: Context<*>) {
        // Mutate the FirReplSnippet itself. The builder's mutable fields at v2.3.21
        // are receivers, attributes, annotations, snippetClass, evalFunctionName, etc.
        // (No `parameters` list on the snippet builder — that's a script-only thing.)
    }

    override fun MutableList<FirStatement>.configure(
        sourceFile: KtSourceFile?,
        scriptSource: KtSourceElement,
        context: Context<*>,
    ) {
        // Receiver is the statement list of the snippet's eval-function body.
        // Canonical use: turn the final expression into a property so the result of
        // the snippet is persisted and visible to the next snippet.
        //
        // For example, `1 + 2` at the end becomes `val res$N = 1 + 2` so a later
        // snippet can reference `res$N`. The reference impl in scripting-compiler
        // shows the full pattern.
    }
}
```

The fourth method — `MutableList<FirStatement>.configure(...)` — is the distinguishing feature versus `FirScriptConfiguratorExtension`. The eval-function body is a list of statements you can rewrite in place. The reference scripting implementation uses this to materialise the last expression as a named `val` so subsequent snippets can reference it.

## 2. `FirReplSnippetResolveExtension`

The resolve extension owns the snippet history. The reference scripting implementation stores a `FirReplHistoryProvider` as a private field of the extension class and writes to it directly from `updateResolved(...)` — there is no built-in session-level accessor.

```kotlin
package com.example.repl.fir

import org.jetbrains.kotlin.KtSourceFile
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirImport
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirReplSnippet
import org.jetbrains.kotlin.fir.declarations.builder.buildImport
import org.jetbrains.kotlin.fir.extensions.FirReplHistoryProvider
import org.jetbrains.kotlin.fir.extensions.FirReplSnippetResolveExtension
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.scopes.DelicateScopeAPI
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassifierSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirReplSnippetSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class MyReplResolver(session: FirSession) : FirReplSnippetResolveExtension(session) {

    // Own the history directly. (The scripting plugin instead reads it from the host
    // configuration so multiple compiler invocations can share state across sessions;
    // see "Sharing history across sessions" below.)
    private val history = MyReplHistoryProvider()

    override fun getSnippetDefaultImports(
        sourceFile: KtSourceFile,
        snippet: FirReplSnippet,
    ): List<FirImport>? = listOf(
        buildImport {
            importedFqName = FqName("kotlin.io")
            isAllUnder = true
        },
    )

    override fun getSnippetScope(currentSnippet: FirReplSnippet, useSiteSession: FirSession): FirScope? {
        // Return a scope that exposes earlier snippets' declarations to currentSnippet.
        // Iterate the history and collect each predecessor's top-level symbols into a
        // composite scope. Excluding the current snippet is important — the resolver
        // adds the current snippet's own declarations to scope separately.
        val predecessors = history.getSnippets().filter { it != currentSnippet.symbol }
        if (predecessors.none()) return null
        return buildHistoryScope(predecessors, useSiteSession)
    }

    override fun updateResolved(snippet: FirReplSnippet) {
        // Called after the snippet is fully resolved. Push the snippet's symbol into
        // our history so the next snippet's `getSnippetScope` sees it.
        history.putSnippet(snippet.symbol)
    }

    private fun buildHistoryScope(predecessors: Iterable<FirReplSnippetSymbol>, session: FirSession): FirScope {
        return HistoryScope(predecessors, session)
    }
}

@OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)
private class HistoryScope(
    private val predecessors: Iterable<FirReplSnippetSymbol>,
    private val session: FirSession,
) : FirScope() {
    // At v2.3.21, only `withReplacedSessionOrNull` is abstract on `FirScope`; the
    // three `processXxxByName` callbacks are `open` with no-op defaults. Override the
    // ones whose declaration kinds your history exposes. `processClassifiersByName`
    // (without substitution) is no longer an override point — use
    // `processClassifiersByNameWithSubstitution` and pass `ConeSubstitutor.Empty`.

    override fun processFunctionsByName(name: Name, processor: (FirNamedFunctionSymbol) -> Unit) {
        for (snippet in predecessors) {
            for (decl in snippet.fir.snippetClass.declarations) {
                if (decl is FirNamedFunction && decl.name == name) {
                    processor(decl.symbol)
                }
            }
        }
    }

    override fun processPropertiesByName(name: Name, processor: (FirVariableSymbol<*>) -> Unit) {
        for (snippet in predecessors) {
            for (decl in snippet.fir.snippetClass.declarations) {
                if (decl is FirProperty && decl.name == name) {
                    processor(decl.symbol)
                }
            }
        }
    }

    override fun processClassifiersByNameWithSubstitution(
        name: Name,
        processor: (FirClassifierSymbol<*>, ConeSubstitutor) -> Unit,
    ) {
        for (snippet in predecessors) {
            for (decl in snippet.fir.snippetClass.declarations) {
                if (decl is FirRegularClass && decl.name == name) {
                    processor(decl.symbol, ConeSubstitutor.Empty)
                }
            }
        }
    }

    @DelicateScopeAPI
    override fun withReplacedSessionOrNull(newSession: FirSession, newScopeSession: ScopeSession): FirScope? = null
}
```

**Three @OptIns are required** to make this scope compile at v2.3.21:
- `SymbolInternals` — for `symbol.fir` access (`snippet.fir`).
- `DirectDeclarationsAccess` — for `snippetClass.declarations` access (the platform asks plugins to prefer scope-based queries but a history scope is intrinsically declaration-walking).
- `DelicateScopeAPI` — for the `withReplacedSessionOrNull` override.

**Why these abstract overrides specifically**: at v2.3.21 `FirScope`'s abstract surface is `processFunctionsByName`, `processPropertiesByName`, `processClassifiersByNameWithSubstitution`, and `withReplacedSessionOrNull`. `getCallableNames` / `getClassifierNames` live on the `FirContainingNamesAwareScope` subclass; you only need them if your scope must answer "what names exist". `processClassifiersByName` (without substitution) does not exist as an override point in this version.

**Why walk `snippet.fir.snippetClass.declarations`**: `FirReplSnippet` does NOT have a `body` / `statements` property at v2.3.21. Its public surface is `receivers`, `snippetClass: FirRegularClass`, and `evalFunctionName: Name`. The declarations introduced by the snippet (top-level `val`s, `fun`s, classes) live as members of `snippetClass`. The `MutableList<FirStatement>.configure` shape from the configurator extension is the raw-FIR-builder phase view; from the resolve extension's perspective the snippet has already been baked into a class.

`getSnippetScope(...)` is the heart of REPL semantics. It composes a `FirScope` from the symbols introduced by snippets earlier in the history. `updateResolved(...)` is the write-side counterpart: after the resolver finishes a snippet, push the snippet's symbol into the history so the next snippet's scope query sees it.

### Sharing history across sessions

If your host evaluates each snippet in a fresh `FirSession` (the way the scripting plugin does for cleaner isolation), keeping `history` as a private field of a per-session `FirReplSnippetResolveExtension` instance loses state between snippets. The reference scripting plugin solves this by stashing the `FirReplHistoryProvider` in `ScriptingHostConfiguration` — a host-level config object that survives across sessions — and having the per-session extension read it via the constructor:

```kotlin
class FirReplSnippetResolveExtensionImpl(
    session: FirSession,
    hostConfiguration: ScriptingHostConfiguration,
) : FirReplSnippetResolveExtension(session) {
    private val replHistoryProvider: FirReplHistoryProvider =
        hostConfiguration[ScriptingHostConfiguration.repl.firReplHistoryProvider]
            ?: FirReplHistoryProviderImpl()
    // ... uses replHistoryProvider as above ...
}
```

This is the actual production pattern in `kotlin/plugins/scripting/scripting-compiler/src/.../FirReplSnippetResolveExtensionImpl.kt`. It requires plumbing your host-config object into the extension's constructor, which `FirExtensionRegistrar`'s simple `+::Class` DSL doesn't directly support — the scripting plugin uses a custom factory. For a one-process REPL where every snippet shares the same session, the in-extension `private val history` field above is sufficient.

## 3. `Fir2IrReplSnippetConfiguratorExtension`

```kotlin
package com.example.repl.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.backend.Fir2IrComponents
import org.jetbrains.kotlin.fir.backend.Fir2IrReplSnippetConfiguratorExtension
import org.jetbrains.kotlin.fir.backend.Fir2IrVisitor
import org.jetbrains.kotlin.fir.declarations.FirReplSnippet
import org.jetbrains.kotlin.ir.declarations.IrReplSnippet

class MyReplIrConfigurator(session: FirSession) : Fir2IrReplSnippetConfiguratorExtension(session) {
    override fun Fir2IrComponents.prepareSnippet(
        fir2IrVisitor: Fir2IrVisitor,
        firReplSnippet: FirReplSnippet,
        irSnippet: IrReplSnippet,
    ) {
        // Receiver is Fir2IrComponents (the in-flight Fir2Ir state); irSnippet is the
        // freshly-produced IR. `IrReplSnippet` is NOT an `IrStatementContainer` — it does
        // not have a `.statements` field. Its mutable fields at v2.3.21 are
        //   receiverParameters, variablesFromOtherSnippets, declarationsFromOtherSnippets,
        //   stateObject, targetClass.
        // Use these to wire the snippet into the host evaluator's frame layout.
    }
}
```

This runs once per snippet during FIR → IR conversion. The `Fir2IrComponents` receiver gives you symbol-table access; mutate the fields listed above (`receiverParameters`, `variablesFromOtherSnippets`, `declarationsFromOtherSnippets`, `stateObject`, `targetClass`) or wire helper IR via the symbol table — `IrReplSnippet` has no `.statements` to mutate directly.

## `FirReplHistoryProvider` abstract class

`FirReplHistoryProvider` is an `abstract class : FirSessionComponent` with no constructor arguments, so subclassing it is trivial:

```kotlin
package com.example.repl.fir

import org.jetbrains.kotlin.fir.extensions.FirReplHistoryProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirReplSnippetSymbol

class MyReplHistoryProvider : FirReplHistoryProvider() {
    private val snippets = LinkedHashSet<FirReplSnippetSymbol>()

    override fun getSnippets(): Iterable<FirReplSnippetSymbol> = snippets
    override fun putSnippet(symbol: FirReplSnippetSymbol) { snippets += symbol }
    override fun isFirstSnippet(symbol: FirReplSnippetSymbol): Boolean =
        snippets.firstOrNull() == symbol
    override fun getSnippetCount(): Int = snippets.size
}
```

The reference impl `FirReplHistoryProviderImpl` (in `kotlin/plugins/scripting/scripting-compiler/src/.../FirReplSnippetResolveExtensionImpl.kt`, ~9 lines) is structurally identical.

### Where to instantiate it

`FirReplHistoryProvider` is declared as a `FirSessionComponent`, but the scripting plugin does **not** install it on the session via `+::Component`. Instead it stores the instance in its host-configuration object (`ScriptingHostConfiguration.repl.firReplHistoryProvider`) and reads it from the resolve extension's constructor (shown in section 2 above). The session-component interface is currently just a marker — there is no `session.replHistoryProvider` accessor in v2.3.21.

If you want session-level access (so other FIR extensions can also enumerate snippets), implement your own `FirSession` accessor via `fir-session-components`' standard pattern: have a `FirExtensionSessionComponent` whose property exposes a `MyReplHistoryProvider`, and access it via `FirSession.sessionComponentAccessor<MyReplHistoryProviderComponent>()`. This is the same shape `fir-session-components` documents for any plugin state.

## Wiring up

```kotlin
package com.example.repl

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import com.example.repl.fir.MyReplConfigurator
import com.example.repl.fir.MyReplResolver
import com.example.repl.fir.MyReplIrConfigurator

class MyReplFirRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::MyReplConfigurator
        +::MyReplResolver       // owns the history (private field inside the extension)
        +::MyReplIrConfigurator
    }
}
```

Registration order inside `configurePlugin()` does not affect runtime visibility — every extension can see every other in the session.

## What this skill enables you to build

- A custom REPL dialect with its own implicit receivers and per-snippet imports (e.g. a SQL-style REPL where each snippet has an implicit `Database` receiver).
- A Jupyter-style kernel where snippet `N` may reference variables introduced by snippet `N-1` — driven by `getSnippetScope(...)` enumerating the history provider.
- Persisting the result of the last expression of each snippet as a named `val` (`MutableList<FirStatement>.configure`).
- A runtime evaluator integration where the IR backend's snippet class name matches an external lookup table (`Fir2IrReplSnippetConfiguratorExtension`).

## Common gotchas

### Forgetting to update the history

`updateResolved(...)` is the only place that writes to the history. If you forget to call `history.putSnippet(snippet.symbol)` there, every snippet appears stand-alone — `val x = 1` in snippet 1 is invisible to snippet 2 because no predecessor was ever recorded. The fix is one line; the symptom is silent.

### `isReplSnippetsSource(...)` overlapping with `FirScriptConfiguratorExtension.accepts(...)`

A single source cannot be both a script and a REPL snippet. If both extensions claim the same source, behaviour is unspecified — typically the FIR builder picks one and the other's customisations are silently dropped. Make `isReplSnippetsSource(...)` and `accepts(...)` disjoint at the source-file-name level.

### `getSnippetScope(...)` returning a scope that includes the current snippet

The current snippet's own declarations are added to the scope by the resolver after `getSnippetScope(...)` returns. Don't include `currentSnippet` in your composite scope — you'll get duplicated symbols and confusing "candidate is ambiguous" diagnostics. Iterate `replHistoryProvider.getSnippets()` and exclude `currentSnippet.symbol`.

### `updateResolved(...)` racing with the next snippet's resolution

If the host calls back into compilation for snippet N+1 *before* `updateResolved(...)` for snippet N has executed (or before the snippet you persisted has been pushed into the history provider), the next snippet sees a stale scope. The compiler's resolve pipeline runs `updateResolved` synchronously per snippet, so a single-threaded host is safe. Multi-threaded hosts (rare) need their own ordering.

### `MutableList<FirStatement>.configure(...)` reordering past the eval result

If you push or replace statements at the end of the list, you may bury the snippet's "value" (the last expression). The host evaluator typically looks at the **final** statement to compute the cell result. If you rewrite the last statement (e.g. into a `val resN = ...`), ensure your host knows to look at `resN` instead.

### `Fir2IrReplSnippetConfiguratorExtension.prepareSnippet(...)` reading FIR after the visitor finishes

The `fir2IrVisitor` is mid-walk when `prepareSnippet(...)` is called. Reading `firReplSnippet` properties is safe; do **not** trigger lazy initialisation of unrelated FIR nodes that the visitor hasn't reached yet — you can deadlock or get half-resolved trees.

## Reference implementations

The JetBrains scripting plugin uses these extensions for its REPL infrastructure:

- `FirReplSnippetConfiguratorExtensionImpl.kt` (`plugins/scripting/scripting-compiler/src/...services/`) — implements `FirReplSnippetConfiguratorExtension`. The `MutableList<FirStatement>.configure(...)` override is the canonical example of turning the last expression into a named property.
- `FirReplSnippetResolveExtensionImpl.kt` — implements `FirReplSnippetResolveExtension`; reads the host's `replHistoryProvider` to compute the cross-snippet scope.
- `Fir2IrReplSnippetConfiguratorExtensionImpl.kt` — implements `Fir2IrReplSnippetConfiguratorExtension`; wires the IR snippet into the host's runtime model.

Two Kotlin/Native REPL hosts (Jupyter kernel, IDE evaluator) also implement custom `FirReplHistoryProvider`s; consult their respective repositories outside the main Kotlin tree for production-tier examples. All three reference files are Apache-2.0 licensed — see `NOTICE.md`.

## Relation to other skills

- **Overview first** → `fir-extensions-overview`
- **`.kts` script files, not REPL snippets** → `fir-scripting-extensions`
- **Session components in general** → `fir-session-components`
- **Adding ordinary declarations** → `fir-declaration-generation-extension`
- **Custom diagnostics on snippet bodies** → `fir-additional-checkers-extension`

## What this skill does NOT cover

- The host-side concerns of running a REPL: opening a socket, dispatching to Jupyter kernels, persisting evaluator frames. Those live outside the compiler-plugin layer (see `kotlin-jupyter-api`, `kotlin-scripting-jvm`, `kotlin-main-kts`).
- Auto-completion / inspection in IDE REPL UIs — that's an IntelliJ-IDE-side concern; the FIR extensions only feed the compiler's resolver.
- The lifecycle of an IDE "evaluate expression" debug-step expression — that's `IRFragmentCompiler` territory, not these extensions.
