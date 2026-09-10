---
name: fir-function-call-refinement-extension
description: Refine the return type of a resolved function call at the call site by generating local declarations (typically a local class encoding inferred information from arguments) — the data-frame schema-inference pattern. Covers FirFunctionCallRefinementExtension, the intercept/transform two-phase API, the run/let scope-wrapping codegen pattern, the companion FirDeclarationGenerationExtension that must supply the generated local class's constructor (hand-built `declarations` on a plugin-origin class are invisible to fir2ir and crash with an NPE in `ClassMemberGenerator.convertClassContent`), the @FirExtensionApiInternals stability gate, and why you almost certainly do not want to use this. Read fir-extensions-overview, fir-predicate-system, and fir-additional-checkers-extension first. If the user references `KtFakeSourceElementKind.PluginGenerated` as a value (it became a sealed class in Kotlin 2.4.20) or hits `FirDistinctSourceElementsHandler` failures in diagnostic tests, ALSO Read CHANGES.md in this skill's directory. NOT a general "rewrite this call" hook (it can only refine the return type, not the callee or arguments).
---

# FirFunctionCallRefinementExtension

> **Stability warning** — the source itself opens with `@FirExtensionApiInternals` and the KDoc reads literally: **"This extension is highly unstable and not recommended to use!"**. The compiler enforces opting into `@FirExtensionApiInternals` to even reference the class. Use it only if you've ruled out every other extension; treat each Kotlin minor as a potential breakage point.

The motivating use case is **`kotlinx.dataframe`**: when the user writes `df.add("score") { 1 }`, the plugin wants the expression's type to be `DataFrame<NewSchema>` where `NewSchema` is a generated local class encoding the union of the original schema and the new "score: Int" column. Ordinary call resolution can't do this — it returns the function's declared return type. This extension hooks into the resolver between "candidate selected" and "outer call resolved" to substitute a more-specific return type and emit the local declarations needed to make that type meaningful.

Source: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirFunctionCallRefinementExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirFunctionCallRefinementExtension.kt). Reference impls:
- [`kotlin/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/DataFrameLikeCallsRefinementExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.20/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/DataFrameLikeCallsRefinementExtension.kt) (sandbox prototype)
- [`kotlin/plugins/kotlin-dataframe/kotlin-dataframe.k2/src/org/jetbrains/kotlinx/dataframe/plugin/extensions/FunctionCallTransformer.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.20/plugins/kotlin-dataframe/kotlin-dataframe.k2/src/org/jetbrains/kotlinx/dataframe/plugin/extensions/FunctionCallTransformer.kt) (production)

Neither reference works alone. Each is paired with a `FirDeclarationGenerationExtension` that supplies the constructor (and properties) of the local classes the refinement extension creates — `DataFrameLikeTypeMembersGenerator.kt` next to the sandbox file, `TokenContentGenerator.kt` next to the production one. If you copy only the refinement extension, the build dies in fir2ir; see [Members of the generated local class must come from a `FirDeclarationGenerationExtension`](#members-of-the-generated-local-class-must-come-from-a-firdeclarationgenerationextension).

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
        val refinedClass = buildRegularClass {
            // 2.4.20+: every local declaration you inject must have a *distinct* source element.
            // Derive it from the call site and tag it with a marker unique to this class.
            source = callInfo.callSite.source?.fakeElement(
                KtFakeSourceElementKind.PluginGenerated.Custom(RefinedSourceKind.Schema(refinedClassId.shortClassName.asString())),
            )
            origin = FirDeclarationOrigin.Plugin(MyRefinementKey)
            // Do NOT add a constructor (or any member) to `declarations` here: for a Plugin-origin
            // class fir2ir only sees members that a FirDeclarationGenerationExtension reports.
            // `MyRefinedClassMemberGenerator` below supplies the primary constructor.
            /* remaining fields populated from callInfo.arguments */
        }

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

/** Marker for `PluginGenerated.Custom` — must have stable equals/hashCode/toString, so a data class is the natural choice. */
private sealed class RefinedSourceKind {
    data class Schema(val name: String) : RefinedSourceKind()
}

data object MyRefinementKey : GeneratedDeclarationKey()

// Plus the companion `MyRefinedClassMemberGenerator : FirDeclarationGenerationExtension` that supplies
// the primary constructor of every `Refined_*` class — full listing under
// "Members of the generated local class must come from a FirDeclarationGenerationExtension" below.
```

The full implementations in `DataFrameLikeCallsRefinementExtension.kt` (sandbox) and `FunctionCallTransformer.kt` (kotlin-dataframe) span ~200-400 lines each — type construction, symbol cloning, and source-element propagation are most of the volume.

## Wiring

Same `+::Constructor` registration as other FIR extensions, but the `unaryPlus` operator on the constructor reference is itself annotated `@FirExtensionApiInternals`. Opt in at the registrar:

```kotlin
class MyFirExtensionRegistrar : FirExtensionRegistrar() {
    @OptIn(FirExtensionApiInternals::class)
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::MyRefinement
        +::MyRefinedClassMemberGenerator // supplies the generated local class's constructor; without it fir2ir NPEs
    }
}
```

The opt-in is per-registrar — you don't need it on the extension class itself. The second line is not optional: see the next section.

## Members of the generated local class must come from a `FirDeclarationGenerationExtension`

This is the rule the reference implementations follow silently and the one that turns "frontend works, backend crashes" into a working build.

**Mechanism.** The class you build in `intercept` has `origin = FirDeclarationOrigin.Plugin(key)`, and `Plugin` is declared with `generated = true`. For any class whose origin is `generated`, `FirDeclaredMemberScopeProvider.createDeclaredMemberScope` builds the declared-member scope **exclusively** from registered `FirDeclarationGenerationExtension`s (`FirGeneratedClassDeclaredMemberScope.create(..., regularDeclaredScope = null, scopeForGeneratedClass = true) ?: FirTypeScope.Empty`) and never reads `klass.declarations`. Everything downstream goes through that scope: `FirClass.constructors(session)` / `primaryConstructorIfAny(session)` call `session.declaredMemberScope(this).processDeclaredConstructors`, and fir2ir's `Fir2IrConverter.processClassMembers` creates the IR primary constructor via `klass.primaryConstructorIfAny(session)`. A constructor you appended to `declarations` by hand therefore never gets an `IrConstructor` created or cached.

**Crash signature.** `ClassMemberGenerator.convertClassContent` *does* read `klass.declarations` when it looks for the primary constructor, finds your hand-built one, and then dereferences the cache that was never filled:

```
Exception was thrown during transformation of class org.jetbrains.kotlin.fir.expressions.impl.FirFunctionCallImpl
Caused by: java.lang.NullPointerException
  at org.jetbrains.kotlin.fir.backend.generators.ClassMemberGenerator.convertClassContent(ClassMemberGenerator.kt:76)
  at org.jetbrains.kotlin.fir.backend.Fir2IrVisitor.visitRegularClass(Fir2IrVisitor.kt:205)
  ...
  at org.jetbrains.kotlin.fir.backend.Fir2IrVisitor.visitAnonymousFunction(...)   <- the wrapper lambda
```

(line 76 at 2.4.20 is `primaryConstructor?.let { declarationStorage.getCachedIrConstructorSymbol(it)!!.owner }`). The `FirConstructor` branch of `processMemberDeclaration` skips primary constructors on purpose ("already created in `processClassMembers`"), so there is no second chance. Leaving the class with **no** constructor at all gets you past fir2ir but fails in `LocalDeclarationsLowering` with `AssertionError: Expected at least one constructor calling super`.

**Fix.** Register a companion `FirDeclarationGenerationExtension` that recognizes your local classes by origin key, advertises `SpecialNames.INIT`, and returns a primary constructor built with the plugin-utils helper. `generateDelegatedNoArgConstructorCall = true` fills in `delegatedConstructor` (a call to `Any()`), and fir2ir synthesizes the constructor body from `delegatedConstructor` plus an `IrInstanceInitializerCall` even though `body == null` — no IR-side body filler is needed. Copy-pasteable (this is the generator from a probe plugin that was built and run against 2.4.20, renamed):

```kotlin
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

data object MyRefinementKey : GeneratedDeclarationKey()

/**
 * Supplies the primary constructor of every local class emitted by the refinement extension.
 * Classes with a `FirDeclarationOrigin.Plugin` origin get their declared member scope exclusively
 * from `FirDeclarationGenerationExtension`s, so this is the only channel through which fir2ir can
 * discover (and cache) the constructor before `convertClassContent` asks for it.
 */
class MyRefinedClassMemberGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {
    private fun FirClassSymbol<*>.isRefinedClass(): Boolean =
        isLocal && (origin as? FirDeclarationOrigin.Plugin)?.key == MyRefinementKey

    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> =
        if (classSymbol.isRefinedClass()) setOf(SpecialNames.INIT) else emptySet()

    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        if (!context.owner.isRefinedClass()) return emptyList()
        return listOf(
            createConstructor(context.owner, MyRefinementKey, isPrimary = true, generateDelegatedNoArgConstructorCall = true).symbol,
        )
    }
}
```

and in the registrar, next to the refinement extension:

```kotlin
+::MyRefinedClassMemberGenerator
```

The same applies to every other member you want the class to have (the "columns" of a schema class): report their names from `getCallableNamesForClass` and build them in `generateProperties` / `generateFunctions`. `ClassMemberGenerator.convertClassContent` and `Fir2IrConverter.processClassMembers` pick generated members up through `klass.generatedMembers(session)`, which is again scope-based. This is exactly how the reference implementations are wired: kotlin-dataframe's `TokenContentGenerator.getCallableNamesForClass` adds `SpecialNames.INIT` for every class carrying `callShapeData` and `generateConstructors` returns `createConstructor(context.owner, DataFrameTokenContentKey, isPrimary = true)`; the sandbox's `DataFrameLikeTypeMembersGenerator` does the same and is registered in `FirPluginPrototypeExtensionRegistrar` alongside `DataFrameLikeCallsRefinementExtension`.

Two details of the probe worth keeping:

- Use `firClassLikeSymbol.isLocal` (`org.jetbrains.kotlin.fir.declarations.utils.isLocal`) for the locality check, not `classSymbol.classId.isLocal` — `ClassId.isLocal` is gated by `@ClassIdBasedLocality`, a `RequiresOptIn` at `WARNING` level whose message tells you to use the symbol accessor instead.
- The generated class must still be emitted as a **statement** of the wrapper lambda (`statements += schemaClass`). If it is only referenced from the refined type and never placed in the IR tree, `JvmInventNamesForLocalClasses` never visits it and JVM codegen fails with `Local class-like declaration should have its name computed in InventNamesForLocalClasses: <stub>.RefinedSchema`.

With the generator registered and the class emitted as a statement, the pipeline runs to bytecode: the local class shows up as `Outer$main$b$1$RefinedSchema.class` with a public no-arg constructor that calls `Object.<init>`. Codegen is not "incomplete" for this extension — it just has no fallback for members that only live in `declarations`.

## Common gotchas

### The body of the refined call is unchanged

A common misconception: refinement does **not** rewrite what the function does at runtime. The original function is still called, with the original arguments, returning the original (less-specific) value. Refinement only narrows the **static type** the resolver gives the expression. If you need to change runtime behaviour, you need an `IrGenerationExtension` that recognises the refined-call pattern and rewrites the IR (the dataframe plugin does all three: this extension for types, `TokenContentGenerator` — a `FirDeclarationGenerationExtension` — for the members of the generated classes, and an IR transformer for the actual schema-aware codegen).

### Generated declarations must be **local**

The KDoc explicitly states: "Generated declarations should be local because this `FirExtension` works at body resolve stage and thus cannot create new top level declarations." Wrapping in `run { ... }` is mandatory for that reason — you have a body scope to put your local class in. Returning a top-level class from `transform` produces a corruption error during serialization (the metadata writer sees a class with no enclosing source file).

Local does not mean "hand-assembled": the class's members still have to arrive through a `FirDeclarationGenerationExtension` (see [Members of the generated local class must come from a `FirDeclarationGenerationExtension`](#members-of-the-generated-local-class-must-come-from-a-firdeclarationgenerationextension)), because a `Plugin`-origin class's declared member scope ignores `declarations`.

### Generated local declarations need **distinct** source elements (2.4.20+)

Because the local classes you inject end up inside an *existing* source `FirFile`, they fall under the FIR-wide constraint that every declaration in a source file has a distinct `(realSource, kind)` source element — the constraint plugin-generated top-level declarations (`FirDeclarationGenerationExtension`) are exempt from. The KDoc added in 2.4.20 spells this out: build each generated local declaration's `source` from the call site with a `KtFakeSourceElementKind.PluginGenerated` kind, and use `PluginGenerated.Custom(marker)` with a marker that is unique per generated declaration so that two classes anchored on the same call (schema + scope, or the classes of two nested calls) never collide:

```kotlin
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fakeElement

private sealed class MySourceKind {
    data class Schema(val name: String) : MySourceKind()
    data class Scope(val name: String) : MySourceKind()
}

val schemaClass = buildRegularClass {
    source = callSite.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated.Custom(MySourceKind.Schema(schemaId.shortClassName.asString())))
    // ...
}
val scopeClass = buildRegularClass {
    source = callSite.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated.Custom(MySourceKind.Scope(scopeId.shortClassName.asString())))
    // ...
}
```

The marker is an `Any` that must have stable `equals`/`hashCode`/`toString` — a `data class` keyed by the generated class name is the pattern the kotlin-dataframe plugin uses (`DataFrameSourceElementKind.SchemaClass/TypeClass/PropertiesScopeClass`). `PluginGenerated.Default` (the old plain `PluginGenerated` object) is still fine for declarations generated through `FirDeclarationGenerationExtension`, but reusing it for several local classes anchored on the same call gives them *equal* source elements. The official test infrastructure now enforces the constraint: `FirDistinctSourceElementsHandler` is part of the default diagnostic-test handler set, so a plugin test that injects two local classes with the same source fails with "Duplicate source elements in test file ...". Leaving `source` unset (`null`) is not flagged by that check, but then IDE navigation and `anchorElement` have nothing to work with — set it.

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
| Generate a method on annotated classes | [`fir-declaration-generation-extension`](../fir-declaration-generation-extension/guide.md) |
| Add a supertype to annotated classes | [`fir-supertype-generation-extension`](../fir-supertype-generation-extension/guide.md) |
| Reject a call that doesn't satisfy a rule | [`fir-additional-checkers-extension`](../fir-additional-checkers-extension/guide.md) (`FirFunctionCallChecker`) |
| Rewrite `lhs = rhs` to `lhs.assign(rhs)` | [`fir-assign-expression-alterer-extension`](../fir-assign-expression-alterer-extension/guide.md) |
| Refine the return type of a call to narrow the static type at the call site | this skill |
| Rewrite the IR (post-FIR) at a call to change runtime behaviour | [`ir-call-rewriting`](../ir-call-rewriting/guide.md) |

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

Note the `// RUN_PIPELINE_TILL: FRONTEND` directive: the sandbox test stops after FIR and never proves that the generated class survives fir2ir and JVM lowering. Add a box test (or a real Gradle sample) to your own suite — the missing-generator mistake described above is invisible to a frontend-only diagnostic test.

## Relation to other extensions

- **Foundation** → [`fir-extensions-overview`](../fir-extensions-overview/guide.md), [`fir-predicate-system`](../fir-predicate-system/guide.md)
- **For matching annotation/symbol of the call's callee** → [`fir-predicate-system`](../fir-predicate-system/guide.md) patterns; or directly `symbol.hasAnnotation(...)`.
- **For complementary IR rewriting that uses the refined types** → [`ir-call-rewriting`](../ir-call-rewriting/guide.md).
- **Diagnostic on call-site misuse** → [`fir-additional-checkers-extension`](../fir-additional-checkers-extension/guide.md) (paired with this for user-facing error messages).

## What this skill does NOT cover

- Changing the **arguments** of a call (no extension does this directly; you build a new wrapper call in `transform` instead).
- Changing the **callee** of a call (similar story; the refinement substitutes a clone of the same symbol, not a different function).
- Cross-call inference (refining one call based on the result of another) — works in principle but the caching makes it fragile; both reference impls operate per-call independently.
- IDE-side completion integration beyond `ownsSymbol`/`anchorElement`/`restoreSymbol` — IDE-specific work goes in a separate IDE plugin, not in this extension.
