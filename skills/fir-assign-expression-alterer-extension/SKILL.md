---
name: fir-assign-expression-alterer-extension
description: Replace a Kotlin variable assignment (`x = value`) with an arbitrary statement during FIR resolution — typically rewriting `someProperty = value` to `someProperty.assign(value)` for value-container types (e.g. Gradle's `Property<T>` build-script API). Covers FirAssignExpressionAltererExtension, the `transformVariableAssignment` hook, scope limitations (property assignments only — local `val`/`var` and parameters are unaffected), and the multi-extension ambiguity error. Read fir-extensions-overview first. NOT for transforming arbitrary expressions (no extension exists for that) or for redirecting function calls (see fir-function-call-refinement-extension).
---

# FirAssignExpressionAltererExtension

This is the K2 extension point that hijacks `=` **on property assignments** — for any `lhs = rhs` where `lhs` resolves to a property (member or top-level), the extension can return a replacement statement (typically `lhs.assign(rhs)` or any other rewriting). The canonical production user is the **Kotlin Assignment Compiler Plugin** ([`plugins/assign-plugin/`](https://github.com/JetBrains/kotlin/tree/v2.3.21/plugins/assign-plugin) in JetBrains/kotlin) — applied in Gradle build scripts to enable the lazy-property idiom `task.input = "OK"` (rewritten to `task.input.assign("OK")`) for Gradle's `Property<T>` API. The plugin is JetBrains-maintained; Gradle is a downstream consumer that opts in via the `kotlin-assignment` Kotlin Gradle plugin.

Source: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirAssignExpressionAltererExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirAssignExpressionAltererExtension.kt).

## What you get

```kotlin
abstract class FirAssignExpressionAltererExtension(session: FirSession) : FirExtension(session) {
    /**
     * At this point [variableAssignment] contains resolved and completed lhs and calleeReference (lvalue)
     *   and unresolved rValue expression
     *
     * It's allowed to transform [variableAssignment] into any kind of statement. This state should be unresolved
     *   (modulo usages of already resolved parts, like lValue). Later this statement will be resolved by compiler
     *   itself using regular resolution algorithms.
     */
    abstract fun transformVariableAssignment(variableAssignment: FirVariableAssignment): FirStatement?

    fun interface Factory : FirExtension.Factory<FirAssignExpressionAltererExtension>
}
```

One method to override; one decision per call: `null` means "leave the assignment alone", any `FirStatement` replaces the assignment wholesale (the compiler then re-resolves the returned statement with the regular resolver, so you can return unresolved `FirFunctionCall` builders without pre-resolving them yourself).

## When the extension fires (and when it doesn't)

The resolver consults the extension only for `FirVariableAssignment` nodes whose `calleeReference` is a `FirResolvedNamedReference` — i.e. **property assignments** where the LHS resolved to a property symbol. It does **not** fire for:

- Local `val` reassignment (already a hard error: `VAL_REASSIGNMENT`)
- Local `var` reassignment (regular variable, no rewrite path)
- Method parameter reassignment (also a hard error)
- Compound assignments handled as `+=` operator dispatches (those go through SAM/operator resolution, not `FirVariableAssignment`)

So your `transformVariableAssignment` will only ever receive property writes — that's a structural guarantee, not something you need to filter.

Inside the body, you typically narrow further by the LHS symbol: only rewrite when the property's type is annotated with your marker (the assign-plugin convention) or the property itself is annotated. Returning `null` for everything else keeps the regular `=` semantics.

## End-to-end example: `@Lazy` property auto-assign

We want `task.input = "OK"` to compile to `task.input.assign("OK")` whenever the property's type is annotated `@Lazy`.

### 1. The annotation (user code) and the API contract

```kotlin
// User code:
package com.example.lazy

annotation class Lazy

@Lazy
interface Property<T> {
    fun assign(value: T)
    fun get(): T
}
```

### 2. The alterer

```kotlin
package com.example.lazy.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirFunctionCallOrigin
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.builder.buildPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.buildUnaryArgumentList
import org.jetbrains.kotlin.fir.expressions.calleeReference
import org.jetbrains.kotlin.fir.expressions.contextArguments
import org.jetbrains.kotlin.fir.expressions.dispatchReceiver
import org.jetbrains.kotlin.fir.expressions.explicitReceiver
import org.jetbrains.kotlin.fir.expressions.extensionReceiver
import org.jetbrains.kotlin.fir.extensions.FirAssignExpressionAltererExtension
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildSimpleNamedReference
import org.jetbrains.kotlin.fir.references.toResolvedVariableSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularPropertySymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class LazyAssignAlterer(session: FirSession) : FirAssignExpressionAltererExtension(session) {

    override fun transformVariableAssignment(variableAssignment: FirVariableAssignment): FirStatement? {
        // Only rewrite when LHS is a property whose type carries @Lazy.
        val propertySymbol = variableAssignment.calleeReference?.toResolvedVariableSymbol()
            as? FirRegularPropertySymbol ?: return null
        val targetType = propertySymbol.resolvedReturnType
            .toRegularClassSymbol(session) ?: return null
        if (!targetType.hasAnnotation(LAZY_CLASS_ID, session)) return null

        // Build `lhs.assign(rhs)` as an unresolved FunctionCall — the compiler will re-resolve it.
        val lhsRef = variableAssignment.calleeReference as FirNamedReference
        val rhs = variableAssignment.rValue

        return buildFunctionCall {
            source = variableAssignment.source
            explicitReceiver = buildPropertyAccessExpression {
                source = lhsRef.source
                coneTypeOrNull = propertySymbol.resolvedReturnTypeRef.coneType
                calleeReference = lhsRef
                explicitReceiver = variableAssignment.explicitReceiver
                dispatchReceiver = variableAssignment.dispatchReceiver
                extensionReceiver = variableAssignment.extensionReceiver
                contextArguments += variableAssignment.contextArguments
            }
            argumentList = buildUnaryArgumentList(rhs)
            calleeReference = buildSimpleNamedReference {
                source = variableAssignment.source
                name = ASSIGN_NAME
            }
            origin = FirFunctionCallOrigin.Regular
        }
    }

    companion object {
        private val LAZY_CLASS_ID = ClassId(FqName("com.example.lazy"), Name.identifier("Lazy"))
        private val ASSIGN_NAME = Name.identifier("assign")
    }
}
```

The replacement is intentionally **unresolved** — only the lValue parts the resolver already gave you (`calleeReference`, `dispatchReceiver`, `explicitReceiver`, `contextArguments`) get reused. The new function call's callee (`assign`) and its receiver type are resolved by the regular FIR resolver after `transformVariableAssignment` returns.

### 3. Wire it up

```kotlin
class LazyFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::LazyAssignAlterer
    }
}
```

`+::LazyAssignAlterer` is the standard `FirExtension` registration; no separate predicate registration is needed unless you also have a checker.

## Receiver and context-argument handover

`FirVariableAssignment` carries everything the LHS resolved against:

- `dispatchReceiver` — the implicit `this` for member properties
- `explicitReceiver` — the receiver written in source (e.g. `obj.prop`)
- `extensionReceiver` — the extension receiver if `prop` is an extension property
- `contextArguments` — context parameters that were resolved into scope

When you build the replacement call's `explicitReceiver` (the new property access of `lhs`), copy all of these forward. Dropping any of them produces a "receiver missing" or "wrong context" error during the re-resolution pass, with a confusing source location because it points at your generated FIR node.

## Multi-extension ambiguity

The resolver iterates every registered `FirAssignExpressionAltererExtension` and collects the non-null results. If **two or more extensions return non-null for the same assignment**, the compiler emits `ConeAmbiguousAlteredAssign` listing the extension class names, and replaces the LHS with an error expression (the source line gets a red squiggle). This is by design — there is no priority mechanism. Plugins must be conservative in what they match, ideally by a unique annotation, so two plugins don't both claim ownership of the same assignment.

If you ship two plugins that both might apply (e.g. an internal lazy-prop plugin and a third-party assign-plugin in the same build), the user sees the ambiguity error and must remove one. There's no current way to express "fall back to the other extension if I return null" beyond simply returning null.

## Common gotchas

### Returning a resolved expression breaks the resolver

The KDoc is explicit: the returned statement should be **unresolved** (modulo the already-resolved LHS parts you reuse). If you pre-resolve the new function call (e.g. by manually setting its `calleeReferenceSymbol`), the post-return resolver pass either rejects or double-resolves it, often with a non-obvious crash. Build it with `buildFunctionCall { ... }` and a `buildSimpleNamedReference { name = ASSIGN_NAME }` — let the resolver bind the symbol.

### `contextArguments` is easy to forget

Context parameters silently get dropped if you don't propagate `contextArguments += variableAssignment.contextArguments` into the new property access. The original assign-plugin's commit history shows multiple fixes around this — it's the most common omission. Always copy it forward.

### Resolver only calls the extension when the LHS is resolved

The resolver guards the alterer dispatch inline inside `transformVariableAssignment` behind `if (assignAltererExtensions != null && resolvedReference is FirResolvedNamedReference)` (`FirExpressionsResolveTransformer.kt:1323-1357` at v2.3.21). When the LHS is broken (typo on the property name etc.), the extension is **not** invoked, so you don't need to defend against `FirErrorNamedReference`. The `?.toResolvedVariableSymbol() as? FirRegularPropertySymbol ?: return null` chain is still a useful belt-and-braces guard against an unexpected symbol shape, just not because the reference could be erroneous.

### No way to alter `+=` / `-=`

Compound assignments dispatch to `plus` / `minus` operator functions through normal call resolution, not through `FirVariableAssignment`. They don't visit this extension. If you need to intercept `task.input += "OK"`, that's a different problem (custom operator function resolution + `FirFunctionCallRefinementExtension`).

### Local property assignment doesn't fire

Top-level `val x = 1; x = 2` is a `VAL_REASSIGNMENT` error before the extension is consulted. Local `var y = 1; y = 2` doesn't go through `FirVariableAssignment` with a resolved property symbol either — it's a direct value-write. The extension only sees property assignments on receivers.

## Test data shape

Tests live as JUnit-driven testData fixtures. Diagnostic-level testing uses `<!DIAGNOSTIC!>...<!>` markers around expected errors; codegen tests use `fun box(): String` returning `"OK"`. See `compiler-plugin-testing` for the testData runner. A representative box test:

```
// FILE: lib.kt
@Lazy
interface Property<T> { fun assign(v: T); fun get(): T }

class StringProp(var v: String) : Property<String> {
    override fun assign(v: String) { this.v = v }
    override fun get() = v
}

class Task(val input: StringProp)

// FILE: main.kt
fun box(): String {
    val task = Task(StringProp("Fail"))
    task.input = "OK"               // rewritten to task.input.assign("OK") by the alterer
    return task.input.get()
}
```

If the alterer is correctly wired, the test compiles and `box()` returns `"OK"`.

## Relation to other extensions

- **Foundation** → `fir-extensions-overview`, `compiler-plugin-bootstrap`
- **For diagnostics on the same annotated property** → `fir-additional-checkers-extension`
- **For modifying the call expression after normal resolution** (different problem) → `fir-function-call-refinement-extension`
- **If you ALSO need to rewrite the IR** of the `assign(...)` call → `ir-call-rewriting`

## What this skill does NOT cover

- Transforming arbitrary expressions (no public extension exists for that)
- Compound assignments (`+=`, `-=`) — they go through operator resolution
- Local variable shadowing or re-typing
- Wiring of `FirAdditionalCheckersExtension` to forbid invalid annotated targets — see `fir-additional-checkers-extension`
