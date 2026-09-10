---
name: fir-expression-resolution-extension
description: Inject implicit extension receivers into specific function-call resolution contexts via FirExpressionResolutionExtension — let `myDsl { foo() }` resolve `foo()` against an extension receiver the plugin provides synthetically. Niche but powerful for DSL plugins, dependency-injection-style plugins, and code-fragment evaluation in the debugger. Read fir-extensions-overview and fir-predicate-system first. NOT for replacing function calls (see ir-call-rewriting) or adding declarations (see fir-declaration-generation-extension).
---

# FirExpressionResolutionExtension

The K2 extension point that **adds implicit extension receivers** during call resolution. When a user writes `foo()` inside some context (e.g. inside a specific DSL block or annotated function), this extension lets the plugin claim "there's an additional implicit receiver of type `T` in scope here" — so `foo()` resolves to `T.foo()` even though the user didn't write the receiver.

Source: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirExpressionResolutionExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirExpressionResolutionExtension.kt).

## API surface

```kotlin
abstract class FirExpressionResolutionExtension(session: FirSession) : FirExtension(session) {
    abstract fun addNewImplicitReceivers(
        functionCall: FirFunctionCall,
        sessionHolder: SessionAndScopeSessionHolder,
        containingCallableSymbol: FirBasedSymbol<*>,
    ): List<ImplicitExtensionReceiverValue>

    fun interface Factory : FirExtension.Factory<FirExpressionResolutionExtension>
}
```

Single abstract method. For each function call site, return a list of additional implicit extension receivers that should be considered in scope at that call site. Returning an empty list means "no additional receivers from this plugin for this call".

| Parameter | What |
|---|---|
| `functionCall: FirFunctionCall` | The call expression being resolved. Inspect its `calleeReference`, source position, and lexical context. |
| `sessionHolder: SessionAndScopeSessionHolder` | Access to the FIR session and the scope session — use these to build types and look up symbols. |
| `containingCallableSymbol: FirBasedSymbol<*>` | The function/property that lexically contains this call. Useful for filtering by enclosing function annotation. |

## When to use this extension

The extension is niche. Real use cases:

- **Return-type-driven receiver injection** (the most-shipped use case) — see `kotlin-dataframe`'s `ReturnTypeBasedReceiverInjector` (`kotlin/plugins/kotlin-dataframe/kotlin-dataframe.k2/src/.../extensions/ReturnTypeBasedReceiverInjector.kt`). Every call whose return type matches some pattern gets implicit receivers contributing to that type's API surface.
- **Algebra-style implicit DSL receivers** — the test plugin `plugin-sandbox` has examples (`AlgebraReceiverInjector.kt`, `DataFrameLikeReturnTypeInjector.kt`).
- **Debugger code-fragment evaluation** — making locally-evaluated expressions resolve symbols available at the breakpoint frame (this is why the API exists — see `KaFirCompilerFacility`).
- **Dependency-injection-style plugins** — every call inside an `@Injected` function gets implicit receivers for registered services.

If your goal is "make `foo()` mean something different *after* it's been resolved", that's call rewriting ([`ir-call-rewriting`](../ir-call-rewriting/guide.md)), not expression resolution. Expression resolution influences *which* `foo` resolves; IR call rewriting changes *what `foo` does*.

## Skeleton

```kotlin
package com.example.dsl.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionAndScopeSessionHolder
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.resolve.calls.ImplicitExtensionReceiverValue
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol

class MyImplicitReceiverExtension(session: FirSession) : FirExpressionResolutionExtension(session) {
    override fun addNewImplicitReceivers(
        functionCall: FirFunctionCall,
        sessionHolder: SessionAndScopeSessionHolder,
        containingCallableSymbol: FirBasedSymbol<*>,
    ): List<ImplicitExtensionReceiverValue> {
        // Filter: only add receivers when the containing function is annotated.
        if (containingCallableSymbol !is FirCallableSymbol<*>) return emptyList()
        if (!matchesMarker(containingCallableSymbol)) return emptyList()

        // Build the implicit receiver value(s) — typically a singleton list.
        return listOf(buildMyImplicitReceiver(sessionHolder))
    }
}
```

Wire it in your `FirExtensionRegistrar`:

```kotlin
override fun ExtensionRegistrarContext.configurePlugin() {
    +::MyImplicitReceiverExtension
}
```

## Common gotchas

### Called for *every* call site

`addNewImplicitReceivers` is invoked during resolution for every function call in the module. Bail early on the common case to keep compile times reasonable:

```kotlin
if (functionCall.source?.kind is KtFakeSourceElementKind) return emptyList()
if (!annotatedContainerCheck()) return emptyList()
```

Use the predicate system ([`fir-predicate-system`](../fir-predicate-system/guide.md)) to filter by annotation rather than walking annotation lists per call.

### Returning a receiver doesn't *force* it to be used

The compiler's overload resolution still chooses the best candidate among all receivers in scope (existing + plugin-added). Your synthesised receiver only "wins" when no other receiver provides a better match. Conflicts produce ambiguity errors at the user's source.

### Receiver type must be resolvable in `sessionHolder`

The `ImplicitExtensionReceiverValue` constructor takes a `ConeKotlinType` (or symbol) — that type must be looked up via `sessionHolder.session.symbolProvider`. Don't try to construct types from string FQNs by hand.

### Receivers attach *between* calls, not retroactively

`addNewImplicitReceivers` participates in `addReceiversFromExtensions` which runs **after each call completes resolution** (call site at `compiler/fir/resolve/src/.../FirExpressionsResolveTransformer.kt:617` at v2.3.21; function declared at `:2156`). Practical consequence: receivers your extension contributes during call N are visible only to calls N+1, N+2, ... within the same body. The very first call in a function body sees no extension-added receivers.

This breaks the naive "first line" pattern:

```kotlin
@DslContext
fun runDsl(d: Dsl) {
    foo()        // ← Unresolved reference: no extension-added receiver in scope yet
}
```

Real plugins work around this by requiring a "trigger" call before user code expects the receiver — the reference implementation is JetBrains' own `plugin-sandbox` test plugin (`plugins/plugin-sandbox/src/.../fir/AlgebraReceiverInjector.kt`), which uses a no-op `injectAlgebra<A>()` call as the first statement, after which the algebra receiver becomes available. (kotlinx.dataframe takes a different approach via `ReturnTypeBasedReceiverInjector`, so don't crib from there for the trigger-call pattern.) Document this requirement in your plugin's README; users who write the receiver-using call as the first statement will see confusing "unresolved reference" errors.

### Synthetic receivers have no runtime value — pair with an IR lowering

`ImplicitExtensionReceiverValue` is purely a **resolution-time** receiver. It tells FIR which symbols to consider, but no IR backing exists for it: at fir2ir, calls that resolved against the synthetic receiver compile to `IrErrorCallExpression` ("Unresolved reference: this@...") and JVM codegen asserts shortly after.

You must pair this extension with an `IrGenerationExtension` that rewrites those error calls. The typical strategy: when entering a function whose containing-callable was the trigger context, walk its body and replace any `IrErrorCallExpression` whose `type` matches your synthetic receiver type with an `IrGetValue` of the source-visible parameter that supplies the actual value (e.g. the function's first parameter of that type). The kotlin-dataframe plugin pairs `ReturnTypeBasedReceiverInjector` with extensive IR-side rewriting for exactly this reason.

### `captureValueInAnalyze` for debugger fragments

The same source file exposes a flag on `FirReceiverParameter`:

```kotlin
var FirReceiverParameter.captureValueInAnalyze: Boolean?
```

When `false`, the IDE's debugger code-fragment evaluator won't capture the receiver's runtime value. This is opt-in and only relevant if your plugin generates implicit receivers that the debugger would otherwise try to capture (and fail because the value isn't on the stack frame). Set it `false` and provide an IR lowering that doesn't depend on a runtime value (e.g. inline the access). Most plugins don't need to touch this.

### Multiple extensions providing receivers

If two plugins register `FirExpressionResolutionExtension`s, both contribute receivers — the lists concatenate. Be defensive against ordering and prefer non-overlapping receiver types.

### Source kind filtering

`FirFunctionCall.source` may be a `KtFakeSourceElementKind` (desugared expressions) — calls inside synthetic property accessors, for-loop iterators, delegated property accessors, etc. Decide explicitly whether plugin-added receivers should apply to those, then filter accordingly.

## Relation to other extensions

- **Filter by annotation efficiently** → [`fir-predicate-system`](../fir-predicate-system/guide.md)
- **Add the *function* the resolver should pick** → [`fir-declaration-generation-extension`](../fir-declaration-generation-extension/guide.md) (declare it on the receiver type your extension makes implicit)
- **Rewrite the IrErrorCallExpression into a real IrGetValue** → [`ir-body-modification`](../ir-body-modification/guide.md) (mandatory companion — without this, runtime fails)
- **Replace the resolved call's bytecode** → [`ir-call-rewriting`](../ir-call-rewriting/guide.md)
- **Cache receiver-type lookups across calls** → [`fir-session-components`](../fir-session-components/guide.md)

## What this skill does NOT cover

- The interaction with `KaFirCompilerFacility` and the debugger's code-fragment evaluator (advanced — `KaFirCompilerFacility` lives at `kotlin/analysis/analysis-api-fir/src/org/jetbrains/kotlin/analysis/api/fir/components/KaFirCompilerFacility.kt`)
- DSL marker (`@DslMarker`) interactions — orthogonal feature
- Custom overload resolution priorities (not exposed via this extension)
