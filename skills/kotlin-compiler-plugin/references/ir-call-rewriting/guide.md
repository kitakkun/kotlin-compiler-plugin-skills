---
name: ir-call-rewriting
description: Replace function calls in user code with calls to a different function during IR transformation — the canonical pattern for "intercept every call to X and route it through Y", used by power-assert, atomicfu, instrumentation plugins, and feature-flag rewriters. Covers IrElementTransformerVoidWithContext, visitCall, building IrCalls with DeclarationIrBuilder, the unified arguments list, deepCopyWithSymbols, and IrPluginContext lookups. Read ir-plugincontext-usage and compiler-plugin-bootstrap first. NOT for body modification of arbitrary functions (see ir-body-modification) or for adding new declarations (see ir-synthetic-class-generation). If the user is upgrading from older Kotlin and references `dispatchReceiver`/`extensionReceiver`/`valueArguments` separately, or hits unified-arguments confusion, ALSO Read CHANGES.md in this skill's directory.
---

# IR Call Rewriting

The canonical IR transformation: walk the module's IR, find every `IrCall` to a target function, and replace it with a call to your chosen replacement. Used by power-assert (rewrite `assert` calls), atomicfu (rewrite atomic API), instrumentation plugins (wrap calls in logging), feature-flag rewriters.

## The shape of an IrCall

`IrCall` is an `abstract class`; you don't instantiate it directly. The properties you read/write live on it and on its parent `IrMemberAccessExpression`:

| Property | On | What it is |
|---|---|---|
| `symbol: IrSimpleFunctionSymbol` | `IrCall` | the target function |
| `superQualifierSymbol: IrClassSymbol?` | `IrCall` | for `super.foo()` calls |
| `arguments: ValueArgumentsList` | `IrMemberAccessExpression` | **all** arguments — dispatch receiver, extension receiver, context parameters, value arguments, in fixed slots indexed by `IrParameterKind`. `ValueArgumentsList` is an inner subclass of `ArrayList<IrExpression?>` with extra overloads keyed by `IrValueParameter`. |
| `typeArguments: MutableList<IrType?>` | `IrMemberAccessExpression` | type arguments (mutable list, slot-by-slot) |
| `origin: IrStatementOrigin?` | `IrMemberAccessExpression` | why the call exists (operator, getter, etc.) |
| `dispatchReceiver: IrExpression?` | `IrMemberAccessExpression` | convenience getter/setter that reads/writes the `arguments` slot for the dispatch receiver. Marked `@UnsafeDuringIrConstructionAPI`; KDoc on the source soft-discourages it ("try to use `arguments` instead, unless usage of `dispatchReceiver` makes for a cleaner/simpler code"). |
| ~~`extensionReceiver: IrExpression?`~~ | `IrMemberAccessExpression` | **Removed in Kotlin 2.4.0** (was `@DeprecatedForRemovalCompilerApi(_2_1_20)` through 2.3.x). Index `arguments` at the `IrParameterKind.ExtensionReceiver` slot instead — see below. |

The two accessors diverged at Kotlin 2.4.0: `extensionReceiver` was **removed** (it was `@DeprecatedForRemovalCompilerApi(_2_1_20)` through 2.3.x), while `dispatchReceiver` **survives** as an `@UnsafeDuringIrConstructionAPI` convenience getter/setter over `arguments[0]` (upstream KDoc soft-discourages it: "try to use `arguments` instead, unless usage of `dispatchReceiver` makes for a cleaner/simpler code"). For the extension receiver there is no longer any shortcut — index `arguments` by `function.parameters[i].kind == IrParameterKind.ExtensionReceiver`.

**This is the critical change since Kotlin 2.2 (KT-68003)**: the dispatch and extension receivers are *not* separate slots — they live inside `arguments` at well-known kinds, queried via `function.parameters[i].kind`. Older tutorials show `dispatchReceiver = ...` as if it were a separate field; for new code prefer iterating `arguments` directly with the parameter shape.

### Slot layout by function kind

| Function shape | `arguments[0]` | `arguments[1]` | `arguments[2..]` |
|---|---|---|---|
| Top-level `fun foo(x, y)` | first regular arg `x` | `y` | — |
| Top-level extension `fun A.foo(x)` | extension receiver `A` | `x` | — |
| Member `class B { fun foo(x) }` | dispatch receiver | `x` | — |
| Member extension `class B { fun A.foo(x) }` | dispatch receiver | extension receiver `A` | `x` |
| With context params `context(C) fun foo(x)` | `C` (Context) | `x` | — |

Always check `function.parameters[i].kind` before assuming what slot `i` represents. For top-level non-extension functions like `kotlin.io.println(message: Any?)`, `arguments[0]` is the user-facing first regular argument.

To rewrite a call, you produce a new `IrCall` (typically via `irCall(symbol)` builder) and return it from a transformer method.

## Boilerplate: visitor + transformer

```kotlin
package com.example.rewrite.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class RewriteIrExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val finder = pluginContext.finderForBuiltins()
        val targetSymbol = finder.findFunctions(
            CallableId(FqName("com.example"), Name.identifier("logSomething"))
        ).single()

        val replacementSymbol = finder.findFunctions(
            CallableId(FqName("com.example.internal"), Name.identifier("instrumentedLog"))
        ).single()

        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoidWithContext() {
            override fun visitCall(expression: IrCall): IrExpression {
                val recursed = super.visitCall(expression) as IrCall
                if (recursed.symbol != targetSymbol) return recursed
                val scopeOwner = currentScope!!.scope.scopeOwnerSymbol
                return rewriteCall(recursed, replacementSymbol, pluginContext, scopeOwner)
            }
        })
    }
}
```

We use `IrElementTransformerVoidWithContext` (in `org.jetbrains.kotlin.backend.common`) because it provides `currentScope` for free — the canonical seed for `DeclarationIrBuilder`. See "Choosing the right scope owner" below for details.

`super.visitCall(expression)` first lets the visitor recurse into argument expressions (so nested calls also get rewritten), then we inspect *that* result and decide whether to replace.

## Building the replacement

The cleanest way: use `DeclarationIrBuilder` and the `irCall` helper.

```kotlin
private fun rewriteCall(
    original: IrCall,
    replacement: IrSimpleFunctionSymbol,
    pluginContext: IrPluginContext,
    scopeOwner: IrSymbol,
): IrCall {
    val builder = DeclarationIrBuilder(
        pluginContext,
        symbol = scopeOwner,
        startOffset = original.startOffset,
        endOffset = original.endOffset,
    )
    return builder.irCall(replacement).apply {
        // arguments includes dispatch receiver, extension receiver, context params,
        // and value args — all in one list. If the original and replacement have
        // matching parameter shapes, copy slot-by-slot. Use deepCopyWithSymbols
        // when the same expression would otherwise become a child of two parents.
        for (i in original.arguments.indices) {
            arguments[i] = original.arguments[i]?.deepCopyWithSymbols()
        }
        for (i in original.typeArguments.indices) {
            typeArguments[i] = original.typeArguments[i]
        }
    }
}
```

Two important details often missed:

1. **Don't separately copy `dispatchReceiver`/`extensionReceiver`** — they're already in `arguments[i]` at fixed slots determined by the original function's parameter shape. Copying them twice overwrites slot 0/1 redundantly (and `dispatchReceiver` is deprecated `@UnsafeDuringIrConstructionAPI`).
2. **Use `deepCopyWithSymbols`** when an `IrExpression` from the original call gets reused in the replacement. The same `IrElement` cannot have two parents — direct aliasing leads to `patchDeclarationParents` failures and IR validation errors. Real plugins (power-assert, atomicfu) call `deepCopyWithSymbols` before reusing argument expressions.

`irCall(symbol)` constructs a fresh `IrCall` whose symbol is the replacement. The `apply { ... }` block fills in arguments and type arguments.

## Choosing the right scope owner for the builder

`DeclarationIrBuilder` needs a `symbol` (any `IrSymbol`) for source-position bookkeeping. The recommended approach is to use **`IrElementTransformerVoidWithContext`** (not plain `IrElementTransformerVoid`) so you get `currentScope` for free:

```kotlin
class CallRewriter : IrElementTransformerVoidWithContext() {
    override fun visitCall(expression: IrCall): IrExpression {
        val recursed = super.visitCall(expression) as IrCall
        if (recursed.symbol != targetSymbol) return recursed
        val scopeOwner = currentScope!!.scope.scopeOwnerSymbol
        return rewriteCall(recursed, replacementSymbol, pluginContext, scopeOwner)
    }
}
```

`currentScope!!.scope.scopeOwnerSymbol` is the enclosing scope's symbol — the canonical seed for `DeclarationIrBuilder`. This is the pattern power-assert uses (`PowerAssertCallTransformer.kt:184`).

`IrElementTransformerVoidWithContext` lives in `org.jetbrains.kotlin.backend.common`. It also gives you `currentFile` and `currentFunction` without manual stack tracking.

If you stay on plain `IrElementTransformerVoid` (e.g. for a very simple plugin), passing the replacement's symbol works as a fallback — just less correct for diagnostics that should land at the user's call site.

## Argument mapping when signatures don't match

If the replacement takes additional arguments (e.g. you wrap with logging metadata):

```kotlin
return builder.irCall(replacement).apply {
    arguments[0] = builder.irString(callerSourceLocation)   // injected metadata
    arguments[1] = original.arguments[0]                    // original first arg
    arguments[2] = original.arguments[1]                    // original second arg
}
```

The `IrBuilder` exposes helpers like `irString`, `irInt`, `irNull`, `irBoolean`, `irGetObjectValue`, `irGet`, `irGetField` for constructing common expression types.

### Building a string template programmatically

For "prepend a literal to an existing argument expression" (e.g. rewriting `println("hello")` → `println("[REWRITTEN] hello")`), use the **`irConcat()`** builder helper combined with `addArgument` (an extension in `org.jetbrains.kotlin.ir.expressions`):

```kotlin
import org.jetbrains.kotlin.ir.expressions.addArgument
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols

val rewrittenArg = builder.irConcat().apply {
    addArgument(builder.irString("[REWRITTEN] "))
    original.arguments[0]?.deepCopyWithSymbols()?.let { addArgument(it) }
}
arguments[0] = rewrittenArg
```

`irConcat()` creates an `IrStringConcatenationImpl` with the builder's offsets and `irBuiltIns.stringType` already wired up — strictly preferred over instantiating `IrStringConcatenationImpl` directly. This emits a single `IrStringConcatenation` node which the JVM backend lowers to a `StringBuilder.append(...)` chain — zero runtime overhead vs writing the concatenation by hand. Real plugins (compose, power-assert) use this for diagnostic message construction.

### `irCall(symbol)` import — minor caveat

`org.jetbrains.kotlin.ir.builders.irCall` is the imported function name, but the resolved overload at the call site `builder.irCall(symbol)` is the `IrBuilderWithScope` extension form (also in the same package). The import is what makes it available; removing it breaks compilation. Keep the import even though IDE inspections may flag it as unused-looking.

## Preserving the `IrStatementOrigin`

If the original call has `origin = IrStatementOrigin.PLUS` (i.e., it represents `a + b` desugared into `a.plus(b)`), preserving that origin keeps debugger and IDE behaviour consistent:

```kotlin
return builder.irCall(replacement).apply {
    origin = original.origin
    // …
}
```

For ordinary function calls (no syntactic-sugar origin), you can leave `origin = null`.

## Filtering by call site context

You may want to rewrite *some* calls and not others — e.g. only inside annotated functions, or only in non-test code:

```kotlin
override fun visitCall(expression: IrCall): IrExpression {
    val recursed = super.visitCall(expression) as IrCall
    if (recursed.symbol != targetSymbol) return recursed
    // Only rewrite if the enclosing function is annotated:
    val enclosing = currentFunction?.irElement as? IrFunction ?: return recursed
    if (!enclosing.hasAnnotation(MY_ANNOTATION_FQ)) return recursed
    return rewriteCall(recursed, replacementSymbol, pluginContext)
}
```

You'll need annotation-checking helpers; `IrAnnotationContainer.hasAnnotation(fqName)` is in `org.jetbrains.kotlin.ir.util`.

## Common gotchas

### `super.visitCall` not called → nested calls untouched

If a target function appears inside the arguments of another target call (`f(g())` where both `f` and `g` need rewriting), you must call `super.visitCall(expression)` first so the recursion happens. Otherwise the outer call's arguments still reference `g` instead of the rewritten replacement.

### Argument count mismatch crashes the backend

`irCall(replacement)` allocates an `arguments` list sized to the *replacement*'s parameter count. If you assign past the end, `IndexOutOfBoundsException`. If you forget to fill an argument, IR validation fails later with "argument N is null". Always match argument indices to the replacement's parameter count exactly.

### Type arguments missed

If the replacement is generic (`fun <T> instrumented(x: T)`), and the original was `myFun<String>(x)`, you must copy the type arguments. Forgetting yields a partially-typed call that fails IR verification.

### Receiver handling — dispatch and extension are IN `arguments`

This is the most common mistake from older tutorials. **Since Kotlin 2.2 (KT-68003), the dispatch receiver, extension receiver, context parameters, and value arguments all live in the same `arguments: ValueArgumentsList`** (an inner subclass of `ArrayList<IrExpression?>`). You do not need to copy `dispatchReceiver` and `extensionReceiver` as separate fields — copying `arguments[i]` slot-by-slot already covers them.

The `dispatchReceiver` getter/setter survives at 2.4.0 as `@UnsafeDuringIrConstructionAPI` and still reads/writes the matching `arguments` slot under the hood; the `extensionReceiver` accessor was **removed in 2.4.0** (it was `@DeprecatedForRemovalCompilerApi(_2_1_20)` through 2.3.x). Using the surviving `dispatchReceiver` in addition to `arguments[i] = ...` overwrites slot `i` twice. New code should index `arguments` directly with `function.parameters[i].kind`.

If your replacement has a *different* parameter shape than the original (e.g. converting an extension call to a top-level call), you must remap by parameter kind:

```kotlin
// Original is a member call (parameters[0].kind == DispatchReceiver, [1..] == Regular)
// Replacement is top-level (all Regular). Move slots:
arguments[0] = original.arguments[1]?.deepCopyWithSymbols()  // first regular arg
// etc.
```

### Recursive / self-call infinite loop

If your rewrite routes `f()` through a wrapper `f_instrumented()`, and the wrapper itself calls `f()`, you've made an infinite recursion. Always exclude the rewrite when `expression.symbol == enclosingFunction.symbol` (and watch for `super` qualifiers — power-assert checks both at `PowerAssertCallTransformer.kt:70-72`).

### Stale `parent` after structural moves

Whenever a rewrite causes `IrFunction`s or other declarations to change parents (e.g. lifting a lambda into a different scope), call `patchDeclarationParents()` on the affected subtree. Real plugins (atomicfu) call it unconditionally at the end of every transform. Without it, later phases crash with "declaration's parent is wrong".

### Symbol matching across overrides

`expression.symbol == targetSymbol` works for direct calls. But a user can override an annotated function in a subclass — that override has a *different* symbol. For interface-method targeting, traverse `function.allOverridden()` or use `function.hasAnnotationOrOverridden(annotationFqName)` (in `org.jetbrains.kotlin.ir.util`). Power-assert uses this pattern.

### Return type mismatch propagates upward

If the replacement returns a different type than the original, IR types upstream become invalid. You'll see verification errors in IR phases after yours. Either keep return types compatible, or wrap in a coercion: `irCall(coerceFn).apply { arguments[0] = builder.irCall(replacement)... }`.

### Calls inside generated lambdas

`IrFunctionExpression` (a lambda) contains an `IrSimpleFunction` with a body. Walking via `transformChildrenVoid` enters lambda bodies automatically; no special handling needed.

### Inlined call sites

When the original function is `inline`, the call may be inlined before your transformer runs (depending on phase ordering). To intercept inline calls reliably, register your `IrGenerationExtension` carefully — most plugins find that inline expansion happens *after* `IrGenerationExtension` so you see the original call shape. But this is a phase-ordering concern; verify with `-Xphases-to-dump-after=` (see [`compiler-plugin-debugging`](../compiler-plugin-debugging/guide.md)).

### Symbol comparison via `==` works because of how IrSymbol caches

`expression.symbol == targetSymbol` works because the compiler caches `IrSimpleFunctionSymbol` instances per resolved declaration. Don't compare via `expression.symbol.owner == targetSymbol.owner` — that compares declarations, which can be different instances after lowering.

## Relation to other extensions

- **Set up the extension** → [`compiler-plugin-bootstrap`](../compiler-plugin-bootstrap/guide.md)
- **Look up the target and replacement symbols** → [`ir-plugincontext-usage`](../ir-plugincontext-usage/guide.md)
- **Inspect or modify the call's *body* (deeper than rewriting the call itself)** → [`ir-body-modification`](../ir-body-modification/guide.md)
- **Add a new function for the replacement** → [`ir-synthetic-class-generation`](../ir-synthetic-class-generation/guide.md)

## What this skill does NOT cover

- Replacing operators, property accessors, or operator calls with custom origins beyond the basic case
- Cross-module call rewriting (the user's code is in module A, the target is in module B that also gets compiled by your plugin) — the principles are the same; just be aware the symbol equality check works across modules
- Inlining your own replacement (your replacement gets inlined too if marked `inline`)
