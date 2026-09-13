# Changes affecting this skill

API migrations relevant to rewriting calls in IR. This skill targets the **current stable Kotlin** (2.4.20).

## Kotlin 2.4.10 → 2.4.20: annotation-argument helpers removed from `IrUtils.kt`

Nothing on `IrMemberAccessExpression` changed for callers (`arguments` merely became `open val`; `dispatchReceiver` is still the `@UnsafeDuringIrConstructionAPI` convenience over `arguments[0]`). What did change is the helper family a call-rewriter typically uses to read the *arguments of an annotation* (e.g. `@Instrument("tag")`) before deciding whether to rewrite:

- `IrAnnotation.getAnnotationStringValue()` / `getAnnotationStringValue(name)` — **removed**.
- `IrAnnotation.getAnnotationValueOrNull<T>(name)` — **removed**.
- `IrConstructorCall.getValueArgument(name: Name)` — **removed**.
- `IrAnnotation.symbol` (the constructor symbol) — `@DeprecatedCompilerApi(deprecatedSince = _2_4_20)`; use the new `IrAnnotation.classSymbol: IrClassSymbol` to identify the annotation class. Note that `@DeprecatedCompilerApi` is a `@RequiresOptIn(level = WARNING)` marker (`compiler/util/src/org/jetbrains/kotlin/DeprecatedCompilerApi.kt`), **not** `kotlin.Deprecated`: the compiler prints `w: ... This compiler API is deprecated` rather than `'symbol' is deprecated. ...`, and `@Suppress("DEPRECATION")` does not silence it — either opt in with `@OptIn(DeprecatedCompilerApi::class)` or move to `classSymbol`. Compare `classSymbol` against a symbol obtained from `finderForSource(file).findClass(classId)`, or use `isAnnotation(ClassId)` / `hasAnnotation(ClassId)`; dereferencing `classSymbol.owner` triggers the `@UnsafeDuringIrConstructionAPI` opt-in warning.

Replacements (all in `org.jetbrains.kotlin.ir.util` unless noted):

- `IrAnnotation.argumentMapping: Map<Name, IrExpression?>` — the raw argument view, declared on the tree node itself (`org.jetbrains.kotlin.ir.expressions.IrAnnotation`).
- `IrAnnotation.getConstArgument<T>(name: String): T?` — const-valued lookup by parameter name (`AdditionalIrUtils.kt`).
- `IrAnnotationContainer.getAnnotationArgumentValue<T>(fqName, argumentName)` — **survives**, now implemented on top of `getConstArgument`.
- `IrAnnotation.isAnnotation(classId: ClassId)` — new overload next to the existing `FqName` one.
- `hasAnnotation(FqName)` / `hasAnnotation(ClassId)` / `hasAnnotation(IrClassSymbol)` and `getAnnotation(FqName)` — unchanged signatures.

```kotlin
// Before (2.4.10)
val tag: String? = enclosing.getAnnotation(MY_ANNOTATION_FQ)?.getAnnotationValueOrNull<String>("tag")

// After (2.4.20)
val tag: String? = enclosing.getAnnotation(MY_ANNOTATION_FQ)?.getConstArgument<String>("tag")
// or, without touching the IrAnnotation node:
val tag2: String? = enclosing.getAnnotationArgumentValue<String>(MY_ANNOTATION_FQ, "tag")
```

Upstream: [`IrUtils.kt:367-370`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt#L367-L370) (surviving `getAnnotationArgumentValue`), [`AdditionalIrUtils.kt:413-416`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/AdditionalIrUtils.kt#L413-L416) (`getConstArgument`), [`IrAnnotation.kt:22-27`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/IrAnnotation.kt#L22-L27) (`classSymbol`, `argumentMapping`, deprecated `symbol`). The removed helpers were at `IrUtils.kt:351-392` at v2.4.10 and were deleted without a deprecation cycle.

## Kotlin 2.3 → 2.4: deprecated argument accessors removed

The unified-`arguments` migration (begun in 2.2, see below) reached its endpoint in 2.4.0. On `IrMemberAccessExpression`, the accessors that were `@DeprecatedForRemovalCompilerApi(_2_1_20)` through 2.3.x are now **gone**:

- `extensionReceiver` — **removed**. Index `arguments` at the slot whose `function.parameters[i].kind == IrParameterKind.ExtensionReceiver` instead.
- `valueArgumentsCount` — **removed**. Use `arguments.size` (note: it counts receivers and context parameters too, not just value arguments).
- `getValueArgument(i)` / `putValueArgument(i, v)` — **removed**. Index `arguments` directly.

`dispatchReceiver` **survives** as an `@UnsafeDuringIrConstructionAPI` convenience getter/setter over `arguments[0]` (the file `IrMemberAccessExpression.kt` shrank from ~580 to 225 lines in the process). Code that already followed this skill's "index `arguments` by parameter kind" guidance compiles unchanged; only code still using the legacy accessors breaks.

## Kotlin 2.1 → 2.2 (KT-68003): unified `arguments` list

Previously, `IrMemberAccessExpression` (the supertype of `IrCall`) exposed:

```kotlin
var dispatchReceiver: IrExpression?
var extensionReceiver: IrExpression?
var valueArguments: List<IrExpression?>
```

In **Kotlin 2.2** (KT-68003), these were unified into a single mutable list:

```kotlin
val arguments: ValueArgumentsList     // inner ArrayList<IrExpression?> — covers dispatch, extension, context params, value args
```

Slot semantics are determined by the function's `parameters[i].kind` (`IrParameterKind.{DispatchReceiver, ExtensionReceiver, Context, Regular}`). `dispatchReceiver` survives as a `@UnsafeDuringIrConstructionAPI`-marked convenience getter/setter reading from the corresponding `arguments` slot. `extensionReceiver` is also **still present** at v2.3.21 but marked `@DeprecatedForRemovalCompilerApi(_2_1_20)` — it compiles with opt-in and is scheduled for removal in a later patch; index `arguments` at the `ExtensionReceiver` slot directly in new code.

**Migration**:

```kotlin
// Before
val rewrittenCall = builder.irCall(replacement).apply {
    dispatchReceiver = original.dispatchReceiver
    extensionReceiver = original.extensionReceiver
    putValueArgument(0, original.getValueArgument(0))
}

// After
val rewrittenCall = builder.irCall(replacement).apply {
    for (i in original.arguments.indices) {
        arguments[i] = original.arguments[i]?.deepCopyWithSymbols()
    }
}
```

Don't separately assign `dispatchReceiver`/`extensionReceiver` after the loop — they refer to the same slot and overwrite the loop's work.

### Removal timeline

The remaining `dispatchReceiver` convenience accessor is currently soft-discouraged via KDoc; a hard `@Deprecated` flip is hinted at for a future minor (per `docs/backend/IR_parameter_api_migration.md` in the Kotlin repo). Plan to be off it by then.

## Kotlin 2.2 → 2.3

### `IrElementTransformerVoidWithContext` is the recommended visitor base

For call rewriting that needs source-position information, `IrElementTransformerVoidWithContext` (in `org.jetbrains.kotlin.backend.common`) maintains a scope stack and exposes `currentScope`, `currentFile`, `currentFunction`, `currentClass`, `currentProperty` (and a few more) as `protected` properties — see [`compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/IrElementTransformerVoidWithContext.kt`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/IrElementTransformerVoidWithContext.kt) for the full list. Plain `IrElementTransformerVoid` lacks them and requires manual stack tracking.

**Migration**: switch your transformer base class. The `currentScope!!.scope.scopeOwnerSymbol` is the canonical `DeclarationIrBuilder` seed. (The plain `IrElementTransformerVoid` base still works for very simple cases that don't need scope information.)
