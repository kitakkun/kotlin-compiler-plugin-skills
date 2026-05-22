---
name: fir-function-call-refinement-extension
description: Refine the return type of a resolved function call at the call site by generating local declarations (typically a local class encoding inferred information from arguments) — the data-frame schema-inference pattern. Covers FirFunctionCallRefinementExtension, the intercept/transform two-phase API, the run/let scope-wrapping codegen pattern, the @FirExtensionApiInternals stability gate, the IR-codegen incompleteness, and why you almost certainly do not want to use this. Read fir-extensions-overview, fir-predicate-system, and fir-additional-checkers-extension first. NOT a general "rewrite this call" hook (it can only refine the return type, not the callee or arguments).
---

# FirFunctionCallRefinementExtension

> **Stability warning** — the source itself opens with `@FirExtensionApiInternals` and the KDoc reads literally: `!!!! This extension is highly unstable and not recommended to use !!!!`. The compiler enforces opting into `@FirExtensionApiInternals` to even reference the class. Use it only if you've ruled out every other extension; treat each Kotlin minor as a potential breakage point.

The motivating use case is **`kotlinx.dataframe`**: when the user writes `df.add("score") { 1 }`, the plugin wants the expression's type to be `DataFrame<NewSchema>` where `NewSchema` is a generated local class encoding the union of the original schema and the new "score: Int" column. Ordinary call resolution can't do this — it returns the function's declared return type. This extension hooks into the resolver between "candidate selected" and "outer call resolved" to substitute a more-specific return type and emit the local declarations needed to make that type meaningful.

Source: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirFunctionCallRefinementExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirFunctionCallRefinementExtension.kt). Reference impls:
- [`kotlin/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/DataFrameLikeCallsRefinementExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/DataFrameLikeCallsRefinementExtension.kt) (sandbox prototype)
- [`kotlin/plugins/kotlin-dataframe/kotlin-dataframe.k2/src/org/jetbrains/kotlinx/dataframe/plugin/extensions/FunctionCallTransformer.kt`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/kotlin-dataframe/kotlin-dataframe.k2/src/org/jetbrains/kotlinx/dataframe/plugin/extensions/FunctionCallTransformer.kt) (production)

## What you get

```kotlin
@FirExtensionApiInternals
abstract class FirFunctionCallRefinementExtension(session: FirSession) : FirExtension(session) {
    /** Called after candidate selection, before outer-call resolution. */
    abstract fun intercept(callInfo: CallInfo, symbol: FirNamedFunctionSymbol): CallReturnType?

    /** Called during call completion, after intercept returned non-null and the candidate symbol was substituted. */
    abstract fun transform(call: FirFunctionCall, originalSymbol: FirNamedFunctionSymbol): FirFunctionCall

    abstract fun ownsSymbol(symbol: FirRegularClassSymbol): Boolean
    abstract fun anchorElement(symbol: FirRegularClassSymbol): KtSourceElement
    abstract fun restoreSymbol(call: FirFunctionCall, name: Name): FirRegularClassSymbol?

    class CallReturnType(
        val typeRef: FirResolvedTypeRef,
        val callback: ((FirNamedFunctionSymbol) -> Unit)? = null,
    )
}
```

Five abstract methods, two of them ephemeral (`anchorElement`, `restoreSymbol`) for IDE / completion machinery, three doing real work.

## The two-phase intercept/transform contract

### Phase 1: `intercept` (after candidate resolution)

```kotlin
override fun intercept(callInfo: CallInfo, symbol: FirNamedFunctionSymbol): CallReturnType? {
    if (!symbol.hasAnnotation(REFINE_FQ, session)) return null
    // ... compute a new return type, possibly involving newly built local FirRegularClass symbols ...
    return CallReturnType(typeRef = newReturnTypeRef, callback = { newSymbol ->
        // Optional: stash data keyed by the new symbol for transform() to retrieve later.
        session.callDataStorage.put(newSymbol, /* whatever you need */)
    })
}
```

Three things the resolver does with the return:

1. **`null` → no refinement**, the call resolves normally.
2. **`CallReturnType` → the resolver clones the function symbol** (via `buildNamedFunctionCopy`), substitutes `returnTypeRef = result.typeRef`, and uses the clone as the call's resolved callee.
3. **The clone's body is `null`** — your refinement only changes the *signature* the resolver sees, not the runtime behaviour. The runtime still calls the original function via the original symbol.

`callback` (if supplied) fires once with the new function symbol, letting you associate per-call data with it for `transform`.

**Ambiguity detection**: if multiple registered extensions return non-null for the same call, the resolver records `AmbiguousInterceptedSymbol` and falls back to the un-refined symbol. There is no priority — both extensions silently lose. Be conservative in your match predicate (e.g. require a unique marker annotation).

### Phase 2: `transform` (during call completion)

```kotlin
override fun transform(call: FirFunctionCall, originalSymbol: FirNamedFunctionSymbol): FirFunctionCall {
    if (call.calleeReference is FirResolvedErrorReference) return call
    // Wrap the original call in `run { /* generated declarations */; original() as RefinedReturnType }`,
    // returning the new wrapper FirFunctionCall.
}
```

By the time `transform` runs, the resolver knows the new return type but the IR backend needs **somewhere to put the generated local class** so the type name resolves at runtime. The convention from both reference impls is to wrap the call in a `kotlin.let { ... }` (not `run`) block whose lambda holds the generated local class plus the (cast) original call:

```kotlin
foo(args)
// becomes:
foo(args).let {
    class NewSchema { /* synthesised columns */ }
    it as Container<NewSchema>
}
```

Both `DataFrameLikeCallsRefinementExtension` (sandbox) and `FunctionCallTransformer` (kotlin-dataframe) build this as `buildFunctionCall { calleeReference = (resolved `kotlin.let`); arguments = (the original call + a lambda containing the local class + cast) }`. The resolver then completes resolution of the wrapper exactly as if the user had written it.

### `ownsSymbol`, `anchorElement`, `restoreSymbol`

These three exist for the IDE / completion path. After deserializing a partial source state (e.g. user is mid-typing and the IDE re-runs analysis), the resolver needs to know:

- **`ownsSymbol`** — given a generated `FirRegularClassSymbol`, is it from this plugin? (`return symbol.anchor != null` in the sandbox impl, where `anchor` is a custom property the plugin sets on its generated classes.)
- **`anchorElement`** — given an owned class symbol, what's the source location of the *original* call expression that produced it? Used to navigate "go to definition".
- **`restoreSymbol`** — given the original call and a name, find the generated class with that name. Used to re-resolve types in the wrapper after caching/edit cycles.

If you implement them inconsistently, the IDE's caching layer fails non-deterministically — usually appearing as "type information disappears after editing".

## End-to-end shape (pseudocode sketch)

The fragment below is **pseudocode**: storage hooks like `session.callDataStorage`, anchor properties like `symbol.anchor`, and `generatedClasses` are illustrative only — they don't exist as public APIs and you'll need to invent equivalent storage for your plugin. Use it for orientation, then read the real implementations in `DataFrameLikeCallsRefinementExtension.kt` (sandbox) and `FunctionCallTransformer.kt` (kotlin-dataframe) for production-quality patterns.

```kotlin
@OptIn(FirExtensionApiInternals::class)
class MyRefinement(session: FirSession) : FirFunctionCallRefinementExtension(session) {

    override fun intercept(callInfo: CallInfo, symbol: FirNamedFunctionSymbol): CallReturnType? {
        if (!symbol.hasAnnotation(REFINE_FQ, session)) return null

        // 1. Decide what local class to introduce based on the call arguments.
        //    `symbol.callableId.packageName` is the real packageFqName accessor on the symbol;
        //    derive a unique class name from `callInfo.callSite` source position or your own counter.
        val refinedClassId = ClassId(symbol.callableId.packageName, Name.identifier("Refined_${freshId()}"))
        val refinedSymbol = FirRegularClassSymbol(refinedClassId)
        val refinedClass = buildRegularClass { /* populated from callInfo.arguments */ }

        // 2. Build a return type referencing the refined class.
        val newReturnType = ConeClassLikeTypeImpl(
            CONTAINER_LOOKUP_TAG,
            arrayOf(ConeClassLikeTypeImpl(ConeClassLikeLookupTagWithFixedSymbol(refinedClassId, refinedSymbol), emptyArray(), isMarkedNullable = false)),
            isMarkedNullable = false,
        )
        return CallReturnType(buildResolvedTypeRef { coneType = newReturnType }) { newSymbol ->
            // Stash the generated class so `transform()` can emit it. Use your own storage
            // (e.g. a FirSessionComponent) — `callDataStorage` below is illustrative only.
            myCallDataStorage[newSymbol] = GeneratedCallData(refinedClass)
        }
    }

    @OptIn(SymbolInternals::class)
    override fun transform(call: FirFunctionCall, originalSymbol: FirNamedFunctionSymbol): FirFunctionCall {
        // Wrap the call in `original().let { class Refined_...; it as Container<Refined_...> }`
        // (omitted; see DataFrameLikeCallsRefinementExtension.kt for the full builder code)
    }

    override fun ownsSymbol(symbol: FirRegularClassSymbol): Boolean = /* check your storage */ ...
    override fun anchorElement(symbol: FirRegularClassSymbol): KtSourceElement = /* from your storage */ ...
    override fun restoreSymbol(call: FirFunctionCall, name: Name): FirRegularClassSymbol? = /* look up in your storage */ ...
}
```

The full implementations in `DataFrameLikeCallsRefinementExtension.kt` (sandbox) and `FunctionCallTransformer.kt` (kotlin-dataframe) span ~200-400 lines each — type construction, symbol cloning, and source-element propagation are most of the volume.

## Wiring

Same `+::Constructor` registration as other FIR extensions, but the `unaryPlus` operator on the constructor reference is itself annotated `@FirExtensionApiInternals`. Opt in at the registrar:

```kotlin
class MyFirExtensionRegistrar : FirExtensionRegistrar() {
    @OptIn(FirExtensionApiInternals::class)
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::MyRefinement
    }
}
```

The opt-in is per-registrar — you don't need it on the extension class itself.

## Common gotchas

### The body of the refined call is unchanged

A common misconception: refinement does **not** rewrite what the function does at runtime. The original function is still called, with the original arguments, returning the original (less-specific) value. Refinement only narrows the **static type** the resolver gives the expression. If you need to change runtime behaviour, you need an `IrGenerationExtension` that recognises the refined-call pattern and rewrites the IR (the dataframe plugin does both: this extension for types, an IR transformer for the actual schema-aware codegen).

### Generated declarations must be **local**

The KDoc explicitly states: "Generated declarations should be local because this `FirExtension` works at body resolve stage and thus cannot create new top level declarations." Wrapping in `run { ... }` is mandatory for that reason — you have a body scope to put your local class in. Returning a top-level class from `transform` produces a corruption error during serialization (the metadata writer sees a class with no enclosing source file).

### Return-type substitution doesn't reach the `IrPluginContext`

The IR side sees the **refined** type on the call expression's `IrType`, but it sees the **original** function symbol as the callee (because the cloned function was synthetic and discarded after FIR). Match the call by checking `IrType.classOrNull` against your refined-type marker, not by callee symbol equality.

### `transform` runs even on partially-resolved calls

The reference impls all start with `if (call.calleeReference is FirResolvedErrorReference) return call`. If the user's source has resolution errors elsewhere in the call (a wrong arg type, missing import), the resolver may still invoke `transform` with a `FirResolvedErrorReference` — without that guard you crash trying to dereference `call.calleeReference.resolvedSymbol`.

### Caching: the resolver memoises per-call

`replaceFromPluginsIfNeeded` records the chosen extension via `originalCallDataForPluginRefinedCall`. If you change your `intercept` logic between IDE-incremental resolutions but the same call is re-encountered from cache, the old refinement may stick. Test against fresh CLI builds, not just IDE iteration.

### `restoreSymbol` returning `null` when the IDE expects a hit

Symptom: hover/go-to-definition silently fails for refinement-generated types. Cause: `restoreSymbol` not returning the right symbol because the cached call's `resolvedType` got serialized differently than what was generated. Always test `restoreSymbol` against fresh resolution and against incremental edits separately.

### One refinement per call

The resolver currently doesn't compose multiple refinements on the same call — when two extensions both claim a call, both lose (`AmbiguousInterceptedSymbol`). If your plugin needs layered refinement, do all the work in a single extension and dispatch internally.

## When to use this vs alternatives

| Goal | Right tool |
|---|---|
| Generate a method on annotated classes | `fir-declaration-generation-extension` |
| Add a supertype to annotated classes | `fir-supertype-generation-extension` |
| Reject a call that doesn't satisfy a rule | `fir-additional-checkers-extension` (`FirFunctionCallChecker`) |
| Rewrite `lhs = rhs` to `lhs.assign(rhs)` | `fir-assign-expression-alterer-extension` |
| Refine the return type of a call to narrow the static type at the call site | this skill |
| Rewrite the IR (post-FIR) at a call to change runtime behaviour | `ir-call-rewriting` |

If you can't precisely articulate what you'd return from `CallReturnType.typeRef`, you don't need this extension.

## Test data shape

A representative diagnostic test from sandbox (`plugin-sandbox/testData/diagnostics/receivers/callShapeBasedInjector.kt`):

```
// RUN_PIPELINE_TILL: FRONTEND
interface DataFrame<out T>
annotation class Refine

@Refine
fun <T, R> DataFrame<T>.add(columnName: String, expression: () -> R): DataFrame<Any?> = TODO()

fun test(df: DataFrame<*>) {
    val df1 = df.add("column") { 1 }
    val col = df1.column      // resolves only because refinement injected a local schema with `column`
}
```

Without the extension, `df1.column` is `Unresolved reference` (the declared return type is `DataFrame<Any?>`, not a schema with `column`). With the extension, the type at `df1` is `DataFrame<RefinedSchemaN>` where `RefinedSchemaN` was synthesised at the call site.

## Relation to other extensions

- **Foundation** → `fir-extensions-overview`, `fir-predicate-system`
- **For matching annotation/symbol of the call's callee** → `fir-predicate-system` patterns; or directly `symbol.hasAnnotation(...)`.
- **For complementary IR rewriting that uses the refined types** → `ir-call-rewriting`.
- **Diagnostic on call-site misuse** → `fir-additional-checkers-extension` (paired with this for user-facing error messages).

## What this skill does NOT cover

- Changing the **arguments** of a call (no extension does this directly; you build a new wrapper call in `transform` instead).
- Changing the **callee** of a call (similar story; the refinement substitutes a clone of the same symbol, not a different function).
- Cross-call inference (refining one call based on the result of another) — works in principle but the caching makes it fragile; both reference impls operate per-call independently.
- IDE-side completion integration beyond `ownsSymbol`/`anchorElement`/`restoreSymbol` — IDE-specific work goes in a separate IDE plugin, not in this extension.
