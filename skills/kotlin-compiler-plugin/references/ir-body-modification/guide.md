---
name: ir-body-modification
description: Modify the body of existing functions during IR transformation — wrap existing statements with try/finally, prepend/append logging, replace the entire body with a generated implementation, fill in stubs left by FirDeclarationGenerationExtension. Covers IrBlockBody manipulation, DeclarationIrBuilder.irBlockBody / irBlock, statement insertion, and the patterns for keeping IR valid. Read ir-plugincontext-usage and compiler-plugin-bootstrap first. NOT for replacing function calls (see ir-call-rewriting) or for declaring new functions (see ir-synthetic-class-generation). If the user is migrating from `IrFunction.valueParameters`, plain `IrElementTransformerVoid` `visitFunctionNew`, or sees deprecated `dispatchReceiver` setters, ALSO Read CHANGES.md in this skill's directory.
---

# IR Body Modification

Walk every function in the module, optionally filter by annotation or signature, and modify its body — prepend statements, wrap in `try/finally`, replace entirely with a generated implementation, or fill in a stub from `FirDeclarationGenerationExtension`.

## The `IrBody` shape

```kotlin
sealed class IrBody : IrElement
class IrBlockBody(val statements: MutableList<IrStatement>) : IrBody()  // most common
class IrExpressionBody(val expression: IrExpression) : IrBody()         // for expression-bodied funs
class IrSyntheticBody(val kind: IrSyntheticBodyKind) : IrBody()         // for synthetic data class members, etc.
```

A typical user function has an `IrBlockBody` containing zero or more statements (the last statement is the return value if the function isn't `Unit`-returning).

## Example: prepend a `println` to every annotated function

Use `IrElementTransformerVoidWithContext` (in `org.jetbrains.kotlin.backend.common`) — it provides `visitFunctionNew` (plain `IrElementTransformerVoid` only has `visitFunction`). It also gives you `currentScope` for free.

Required imports (note `DeclarationIrBuilder` lives in `backend.common.lower`, not `ir.builders`):

```kotlin
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString

class LogIrExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val builtins = pluginContext.finderForBuiltins()
        val printlnSymbol = builtins.findFunctions(
            CallableId(FqName("kotlin.io"), Name.identifier("println"))
        ).single { fn -> fn.owner.parameters.singleOrNull()?.type?.isNullableAny() == true }

        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoidWithContext() {
            override fun visitFunctionNew(declaration: IrFunction): IrStatement {
                val processed = super.visitFunctionNew(declaration) as IrFunction
                if (!processed.hasAnnotation(LOG_ANNOTATION_FQ)) return processed

                val body = processed.body as? IrBlockBody ?: return processed
                val builder = DeclarationIrBuilder(pluginContext, processed.symbol)
                val logCall = builder.irCall(printlnSymbol).apply {
                    arguments[0] = builder.irString("entering ${processed.name}")
                }
                body.statements.add(0, logCall)
                return processed
            }
        })
    }
}
```

`body.statements` is a `MutableList<IrStatement>` — direct mutation is supported and idiomatic. Adding to index 0 prepends; `body.statements.add(logCall)` appends *before* the implicit return.

## Replacing the entire body

For functions whose body should be entirely plugin-generated (typically declared by `FirDeclarationGenerationExtension` with an empty body, then filled in IR):

```kotlin
override fun visitFunctionNew(declaration: IrFunction): IrStatement {
    val processed = super.visitFunctionNew(declaration) as IrFunction
    if (processed.origin !is IrDeclarationOrigin.GeneratedByPlugin) return processed

    val builder = DeclarationIrBuilder(pluginContext, processed.symbol)
    processed.body = builder.irBlockBody {
        +irReturn(irString("hello from generated body"))
    }
    return processed
}
```

`irBlockBody { ... }` is a builder DSL — inside the lambda, `+statement` adds a statement, and helpers like `irReturn`, `irGet`, `irCall` produce IR expressions. The result is assigned back to `declaration.body`.

For a single-expression body:

```kotlin
processed.body = builder.irExprBody(builder.irString("hello"))
```

## Wrapping the existing body in try/finally

The pattern: capture the current body's statements, then **assign a freshly-built `irBlockBody`** that wraps them in `try { ... } finally { ... }`. **Always go through the builder DSL — do not `body.statements.clear()` + `body.statements.add(...)` manually.** The builder produces well-typed IR with correct parent links and offsets; in-place mutation is brittle and easy to get wrong (wrong scope on synthesised statements, missing `resultType`, statements added in the wrong order relative to traversal).

```kotlin
override fun visitFunctionNew(declaration: IrFunction): IrStatement {
    val processed = super.visitFunctionNew(declaration) as IrFunction
    if (!processed.hasAnnotation(TIMED_FQ)) return processed

    val body = processed.body as? IrBlockBody ?: return processed
    val original = body.statements.toList()
    val builder = DeclarationIrBuilder(pluginContext, processed.symbol)

    processed.body = builder.irBlockBody {
        +irTry(
            type = processed.returnType,
            tryResult = irBlock(resultType = processed.returnType) {
                original.forEach { +it }
            },
            catches = emptyList(),
            finallyExpression = irCall(/* logTimerSymbol */).apply {
                // …
            }
        )
    }
    return processed
}
```

`irBlockBody { ... }`, `irBlock { ... }`, and `irTry(...)` are part of the IR builder DSL. Type inference for the block matters — the `resultType` on the inner `irBlock` must match the function's return type so that the `try` expression's value flows back as the function result, and IR validation passes.

**Why not `body.statements.clear() + add(...)`?** Direct mutation of `body.statements` is supported (the field is a `MutableList<IrStatement>` and is mutated in-place by some compiler internals — see e.g. `compiler/ir/backend.js/src/.../PurifyObjectInstanceGettersLowering.kt`). The convention in the canonical body-fill plugins (kotlinx-serialization, parcelize) is to **replace the body wholesale** via `processed.body = builder.irBlockBody { ... }`: it makes the new body a self-contained build step, keeps `try`/`finally` wrapping readable, and avoids accidental ordering bugs when statements are inserted partway through traversal. Mutate `body.statements` only for genuine append-once cases like the prepend-`println` example above.

## Filling stubs from `FirDeclarationGenerationExtension`

Functions declared by FIR generation arrive at IR with `body = null` (or an empty `IrBlockBody`). The IR plugin recognises them by their `origin`:

```kotlin
override fun visitFunctionNew(declaration: IrFunction): IrStatement {
    val processed = super.visitFunctionNew(declaration) as IrFunction
    val origin = processed.origin
    if (origin !is IrDeclarationOrigin.GeneratedByPlugin) return processed
    if (origin.pluginKey != MyGeneratedDeclarationKey) return processed

    val builder = DeclarationIrBuilder(pluginContext, processed.symbol)
    processed.body = builder.irBlockBody {
        // … synthesise implementation based on processed.parameters, processed.parent, etc.
    }
    return processed
}
```

`MyGeneratedDeclarationKey` is the `GeneratedDeclarationKey` you used in the FIR-side generator (see [`fir-declaration-generation-extension`](../fir-declaration-generation-extension/guide.md)). The IR origin carries the same key, letting both halves coordinate.

## DeclarationIrBuilder helpers cheat sheet

Inside `irBlockBody { … }` or `irBlock { … }`:

| Helper | Produces |
|---|---|
| `irReturn(expr)` | a `return expr` statement |
| `irGet(parameter)` | a reference to a parameter (`x`) |
| `irGetField(receiver, field)` | a field load |
| `irGet(receiverType, receiver, getter)` | a property getter call |
| `irSetField(receiver, field, value)` | a field store |
| `irCall(symbol)` | an `IrCall`; chain `.apply { arguments[i] = ... }` |
| `irString(value)`, `irInt(value)`, `irBoolean(value)`, `irNull()` | literals |
| `irGetObjectValue(type, classSymbol)` | reference to an `object` declaration (no zero-arg `irGetObject` exists) |
| `irIfThenElse(type, cond, thenPart, elsePart)` | branching expression |
| `irWhen(type, branches)` | n-ary `when` expression — combine with `irBranch(condition, result)` and `irElseBranch(result)` |
| `irThrow(expr)` | a throw statement |
| `irEquals(left, right)`, `irNotEquals(...)` | comparisons via `equals`/`!=` |
| `irTemporary(value, nameHint)` | a synthetic local variable, returns an `IrVariable` |
| `irConcat()` | start an `IrStringConcatenation`; chain `.apply { addArgument(irString("...")); addArgument(irGet(param)); ... }` for runtime string-template construction. Note: `addArgument` is an extension function in `org.jetbrains.kotlin.ir.expressions` (NOT `ir.builders`); add `import org.jetbrains.kotlin.ir.expressions.addArgument` explicitly. |

These helpers live in `org.jetbrains.kotlin.ir.builders.*`. Import what you need.

The `DeclarationIrBuilder` **class** itself is in a different package — `org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder` — easy to confuse with the helpers above when the IDE auto-completes imports.

## Navigating the enclosing class

When the body you're filling needs to read the surrounding class — its constructor parameters, a property's getter, the implicit `this` receiver — these are the primitives. Throughout, `function: IrFunction` is the function whose body you are building (the `processed` variable in the examples above):

| Accessor | Returns | Import | Used for |
|---|---|---|---|
| `function.parentAsClass` | `IrClass` of the enclosing type | `org.jetbrains.kotlin.ir.util.parentAsClass` | available when `function` is a class member |
| `irClass.primaryConstructor` | `IrConstructor?` | `org.jetbrains.kotlin.ir.util.primaryConstructor` | the primary constructor; `null` for interfaces / objects |
| `constructor.parameters.filter { it.kind == IrParameterKind.Regular }` | `List<IrValueParameter>` | `IrParameterKind` from `org.jetbrains.kotlin.ir.declarations` | the user-visible value params, excluding receivers / context params |
| `irClass.declarations.filterIsInstance<IrProperty>()` | `List<IrProperty>` | — | the class's own properties; match a constructor `val`/`var` parameter to a property by `name: Name` |
| `irProperty.getter` / `irProperty.setter` | `IrSimpleFunction?` | — | member accessor symbols; call via `irCall(getter.symbol)` |
| `function.dispatchReceiverParameter` | `IrValueParameter?` | — (member on `IrFunction`; at v2.3.21 this is a non-deprecated read-only getter implemented as `_parameters.firstOrNull { it.kind == IrParameterKind.DispatchReceiver }` — fine to call directly) | the implicit `this` parameter; pass `irGet(it)` into `arguments[0]` of a member call |

A getter call inside a body filler:

```kotlin
val getterCall = irCall(property.getter!!.symbol).apply {
    arguments[0] = irGet(function.dispatchReceiverParameter!!)
}
```

Two sharp edges:

- For the `this` receiver, use `arguments[0]` not the deprecated `dispatchReceiver = ...` setter (KT-68003 unified arguments since Kotlin 2.2; see `CHANGES.md`).
- A constructor `val name: String` parameter and the property named `name` on the same class are *different* IR objects. The constructor parameter is for reading from inside the constructor; outside, you go through the property's `getter`.

## Walking arguments and parameters

Inside a generated body, you typically read parameters and operate on them:

```kotlin
processed.body = builder.irBlockBody {
    val firstParamRef = irGet(processed.parameters[0])
    // Since Kotlin 2.2 (KT-68003), arguments[i] is the unified slot for
    // dispatch receiver, extension receiver, context parameters, and value args.
    // Set arguments[0] for the dispatch-receiver slot rather than the deprecated
    // dispatchReceiver = ... convenience setter.
    +irReturn(irCall(toStringSymbol).apply { arguments[0] = firstParamRef })
}
```

`processed.parameters` is the unified accessor (`IrFunction.parameters: List<IrValueParameter>`); see the gotcha "`processed.parameters` is the only accessor in 2.3.x" below for the full story on what was removed.

## Common gotchas

### Body returns wrong type → IR validation crash later

If your inserted statements change what the function returns (e.g. you replaced the body with one that returns `String` but the function is declared `Int`), IR validation fails at a later phase. Always type-check the result. Use `processed.returnType` to know what's expected.

### Modifying body during traversal corrupts ordering

`transformChildrenVoid` may visit children of the function before/after you modify the body. Modify the body *after* `super.visitFunctionNew(declaration)` returns. Done as in the examples — first super, then mutate.

### `body = null` for abstract / external functions

`abstract fun foo(): String` has `body = null`. So does `external fun bar()`. Check `body != null` (or `body as? IrBlockBody`) before mutating.

### Expression bodies (`fun f() = expr`) are NOT `IrBlockBody`

A common trap: `body as? IrBlockBody ?: return` silently skips every single-expression function (`fun greet(name: String) = "Hi, $name"`). At IR time these arrive as `IrExpressionBody` (with a single `expression: IrExpression` field), not `IrBlockBody`. To wrap or extend an expression body, normalise it first:

```kotlin
val body = processed.body ?: return processed
val originalStatements: List<IrStatement> = when (body) {
    is IrBlockBody -> body.statements.toList()
    is IrExpressionBody -> {
        // wrap the single expression in `irReturn(...)` so it slots into a block body
        val builder = DeclarationIrBuilder(pluginContext, processed.symbol)
        listOf(builder.irReturn(body.expression))
    }
    else -> return processed   // IrSyntheticBody / unknown — skip
}
processed.body = builder.irBlockBody { originalStatements.forEach { +it } /* + your additions */ }
```

Without this, plugins that wrap function bodies in `try/finally` or prepend logging silently miss every expression-bodied function.

### Synthetic data class members

Data class `equals`, `hashCode`, `toString`, `copy` arrive with `body: IrSyntheticBody`. Modifying them is the wrong tool — let the data class lowering produce them, or generate replacements via `FirDeclarationGenerationExtension`.

### `visitFunctionNew` also visits constructors and fake overrides

When you walk every function with a class-level annotation and want to wrap "user-visible methods only", filter explicitly:

```kotlin
override fun visitFunctionNew(declaration: IrFunction): IrStatement {
    val processed = super.visitFunctionNew(declaration) as IrFunction
    if (processed is IrConstructor) return processed                       // skip <init> — no body wrap
    if (processed is IrSimpleFunction && processed.isFakeOverride) return processed
                                                                           // skip Any.equals/hashCode/toString
                                                                           // and other inherited stubs
    // ... your transform ...
}
```

`isFakeOverride` filters out the inherited `equals`/`hashCode`/`toString` stubs that the compiler injects into every class (these have a `body` referencing the supertype's implementation but are not "user-written" bodies). This is a different mechanism from `IrSyntheticBody`: synthetic bodies are how the compiler marks data-class `equals`/`hashCode`/`toString`/`copy` (whose real implementation is filled in later by data-class lowering); fake overrides are how inheritance is represented at the IR level. A plugin that wraps every method body — for instance, to add timing logs to user code — should skip both, otherwise it ends up double-wrapping inherited `Any` methods or interfering with data-class lowering.

### Filtering by visibility

`IrFunction.visibility` is a `DescriptorVisibility`. Compare against constants from `DescriptorVisibilities` (the Java `class` in `org.jetbrains.kotlin.descriptors`), which holds matching `DescriptorVisibility` instances — `PUBLIC`, `PRIVATE`, `INTERNAL`, `PROTECTED`, `LOCAL`, etc.:

```kotlin
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities

if (processed.visibility != DescriptorVisibilities.PUBLIC) return processed
```

This is the comparison the compiler itself uses (e.g. `compiler/ir/backend.common/src/.../LocalDeclarationsLowering.kt` checks `declaration.visibility == DescriptorVisibilities.LOCAL`). Don't compare against `org.jetbrains.kotlin.descriptors.Visibilities.Public` — that's a `Visibility`, a different (lowercase) type, and the equality always returns false.

### Adding statements after the implicit return

For functions with an expression body that's been "block-ified" by an earlier phase, the last statement is often a `return`. Adding a statement after that return makes it dead code (and IR validation will warn). Insert *before* the last statement, or use `irBlock` to wrap.

### `processed.parameters` is the canonical accessor in 2.4.0

`IrFunction.parameters: List<IrValueParameter>` is the unified mutable list and the preferred accessor in Kotlin 2.4.0. At v2.4.0:

- `valueParameters` and `extensionReceiverParameter` have been **removed** from `IrFunction` — they no longer exist as members. (Both were error-level `@DeprecatedForRemovalCompilerApi` through 2.3.x.) Old code referencing them won't compile against this Kotlin version.
- `dispatchReceiverParameter` survives but is now **read-only** (`val`, not `var` as in 2.3.x) — a non-deprecated convenience getter on `IrFunction` (`IrFunction.kt:62`, just `parameters.firstOrNull { it.kind == IrParameterKind.DispatchReceiver }`). Reading it is safe; code that *assigned* `dispatchReceiverParameter = …` must now mutate `parameters` instead.
- Several **extension** helpers in `org.jetbrains.kotlin.ir.util.IrUtils.kt` (`explicitParameters`, `explicitParametersCount`, `addExplicitParametersTo`, `allParametersCount`, `createParameterDeclarations`) carry `@DeprecatedForRemovalCompilerApi(_2_1_20)` — replace with `parameters` directly.

New code should filter `parameters` by `kind` (`IrParameterKind.DispatchReceiver`, `Context`, `ExtensionReceiver`, `Regular`) when you need a specific subset; for `this` specifically, `dispatchReceiverParameter` remains the convenient form.

### Builder offsets land at synthetic positions

Stack traces / debugger steps land on synthetic offsets if you don't pass `startOffset`/`endOffset` to the builder. For user-visible behaviour (e.g. logging that should report the user's call site), pass the original element's offsets:

```kotlin
val builder = DeclarationIrBuilder(pluginContext, processed.symbol, processed.startOffset, processed.endOffset)
```

### `visitFunctionNew` requires `IrElementTransformerVoidWithContext`

`visitFunctionNew` is **not** on plain `IrElementTransformerVoid` — only on `IrElementTransformerVoidWithContext` (in `org.jetbrains.kotlin.backend.common`). Stay-on-plain-`IrElementTransformerVoid` plugins must override `visitFunction` (which dispatches to `visitSimpleFunction`/`visitConstructor`). For most non-trivial plugins, the `WithContext` variant is correct because it also gives you `currentScope`, `currentFile`, and `currentFunction` for free.

### Helper extensions on `IrBlockBodyBuilder` don't work inside an inner `irBlock { ... }`

A subtle scope-shadowing trap when factoring out body-fill code. If you write a helper like:

```kotlin
fun IrBlockBodyBuilder.appendField(name: String, value: IrExpression) {
    val tmp = irTemporary(value, "fld_$name")
    +irCall(/* something using tmp */)
}
```

…it works fine when called directly inside `irBlockBody { appendField(...) }`. But the **moment you call it inside a nested `irBlock { ... }`** — typically when building a `try { irBlock { ... } } finally { ... }` body — the helper resolves against the *outer* `IrBlockBodyBuilder`, not the inner `IrBlockBuilder`. The temporary is then declared in the outer scope, but `+irCall(...)` adds a statement that references it in the **inner** scope. JVM codegen later crashes with `No mapping for symbol: VAR IR_TEMPORARY_VARIABLE …`.

**Fix**: declare helpers on the common supertype `IrStatementsBuilder<*>` (which both `IrBlockBodyBuilder` and `IrBlockBuilder` extend):

```kotlin
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder

fun IrStatementsBuilder<*>.appendField(name: String, value: IrExpression) { /* same body */ }
```

Or pass the builder explicitly:

```kotlin
fun appendField(b: IrStatementsBuilder<*>, name: String, value: IrExpression) {
    with(b) { /* body */ }
}
```

The crash is far from the cause (codegen, not transformation), so when you see "`No mapping for symbol: VAR IR_TEMPORARY_VARIABLE`" the first thing to suspect is a helper extension targeting the wrong builder type.

## Relation to other extensions

- **Lookup helpers and contexts** → [`ir-plugincontext-usage`](../ir-plugincontext-usage/guide.md)
- **Replace whole calls inside the body** → [`ir-call-rewriting`](../ir-call-rewriting/guide.md)
- **Generate the function declaration whose body you fill** → [`fir-declaration-generation-extension`](../fir-declaration-generation-extension/guide.md) + this skill
- **Generate a brand-new class with brand-new functions** → [`ir-synthetic-class-generation`](../ir-synthetic-class-generation/guide.md)

## What this skill does NOT cover

- Constructing complex IR expressions from scratch (loops, when expressions, lateinit checks) — see the IR builder helpers in `kotlin/compiler/ir/ir.psi2ir/src/.../IrUtils.kt`
- Lowering — modifying IR during compiler lowering passes (vs your IrGenerationExtension that runs once before lowering)
- IR validation rules — see `kotlin/compiler/ir/ir.tree/src/.../IrValidator.kt` if validation fails on your output
