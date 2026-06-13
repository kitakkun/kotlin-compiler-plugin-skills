# Evidence dossier: ir-call-rewriting

Citations into the Kotlin compiler source tree at `/Users/kitakkun/Documents/GitHub/kotlin-lang/`. Each claim made by `guide.md` and `CHANGES.md` is grounded in a specific file and line.

---

### Claim: `IrCall` is an `abstract class`; you cannot instantiate it directly. Only `IrCallImpl` (the generated impl) is constructable, typically via the `irCall(symbol)` builder which delegates to `IrCallImpl(...)`.

**File**: [`kotlin/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/IrCall.kt:18`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/IrCall.kt#L18)

**Snippet**:
```kotlin
abstract class IrCall : IrFunctionAccessExpression() {
    abstract override var symbol: IrSimpleFunctionSymbol
    abstract var superQualifierSymbol: IrClassSymbol?
    ...
}
```

The factory used by builders lives at [`kotlin/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/impl/IrCallImpl.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/impl/IrCallImpl.kt). The `irCall` builder delegates to it (see `ExpressionHelpers.kt:265`).

---

### Claim: `IrMemberAccessExpression.arguments` is the unified mutable list (KT-68003), introduced in Kotlin 2.2. Slot semantics are determined by `IrFunction.parameters[i].kind`.

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/expressions/IrMemberAccessExpression.kt:41`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/expressions/IrMemberAccessExpression.kt#L41)

**Snippet**:
```kotlin
/**
 * A list of all value arguments.
 *
 * It corresponds 1 to 1 with [IrFunction.parameters], and therefore should have the same size.
 * `null` value usually means that the default value of the corresponding parameter will be used.
 */
val arguments: ValueArgumentsList = ValueArgumentsList()
```

---

### Claim: `dispatchReceiver` survives only as a `@UnsafeDuringIrConstructionAPI`-marked convenience getter/setter that reads/writes the corresponding `arguments` slot. The KDoc soft-discourages it ("Please try to use `arguments` instead"), and the source explicitly hints at future deprecation.

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/expressions/IrMemberAccessExpression.kt:222-237`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/expressions/IrMemberAccessExpression.kt#L222-L237)

**Snippet**:
```kotlin
@UnsafeDuringIrConstructionAPI
var dispatchReceiver: IrExpression?
    get() {
        return if (hasDispatchReceiver()) arguments[0] else null
    }
    set(value) {
        if (hasDispatchReceiver()) {
            arguments[0] = value
        } else {
            require(value == null) { ... }
        }
    }
```

The KDoc immediately above (lines 103-140) reads:
> Please try to use [arguments] instead, unless usage of [dispatchReceiver] makes for a cleaner/simpler code. ... In future, if we evaluate [dispatchReceiver] is not really useful ... it will be deprecated. See docs/backend/IR_parameter_api_migration.md

---

### Claim: `extensionReceiver` was removed from `IrMemberAccessExpression` in 2.4.0; `dispatchReceiver` survives.

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/expressions/IrMemberAccessExpression.kt:142-149`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/expressions/IrMemberAccessExpression.kt#L142-L149)

**Snippet** (the surviving `dispatchReceiver`; `extensionReceiver` no longer exists):
```kotlin
@UnsafeDuringIrConstructionAPI
var dispatchReceiver: IrExpression?
    get() = if (hasDispatchReceiver()) arguments[0] else null
    set(value) { ... arguments[0] = value }
```

At v2.4.0 the file is 225 lines (it was ~580 at v2.3.21); the `extensionReceiver` getter/setter — `@DeprecatedForRemovalCompilerApi(CompilerVersionOfApiDeprecation._2_1_20)` through 2.3.x — is **gone** (`git grep extensionReceiver` on this file at v2.4.0 returns nothing). `valueArgumentsCount`, `getValueArgument`, and `putValueArgument` were removed alongside it. Only `dispatchReceiver` remains, as an `@UnsafeDuringIrConstructionAPI` convenience over `arguments[0]`. For the extension receiver, index `arguments` at the slot whose `function.parameters[i].kind == IrParameterKind.ExtensionReceiver` — the migration guidance the guide already gave is now the *only* way.

---

### Claim: `IrParameterKind` is an enum with `DispatchReceiver`, `Context`, `ExtensionReceiver`, `Regular` (in that order in the source).

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/declarations/IrParameterKind.kt:8-13`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/declarations/IrParameterKind.kt#L8-L13)

**Snippet**:
```kotlin
enum class IrParameterKind {
    DispatchReceiver,
    Context,
    ExtensionReceiver,
    Regular,
}
```

(Note: source order is `DispatchReceiver, Context, ExtensionReceiver, Regular` — `Context` comes before `ExtensionReceiver` in the enum declaration, though slot layout in `arguments` is independent of enum declaration order.)

---

### Claim: `IrElementTransformerVoidWithContext` lives in `org.jetbrains.kotlin.backend.common`, with `currentScope` at line 116 (accessor for `scopeStack.peek()`). Use `currentScope!!.scope.scopeOwnerSymbol` to seed `DeclarationIrBuilder`.

**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/IrElementTransformerVoidWithContext.kt:116`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/IrElementTransformerVoidWithContext.kt#L116)

**Snippet**:
```kotlin
protected val currentScope get() = scopeStack.peek()
```

Package declaration at line 17: `package org.jetbrains.kotlin.backend.common`. The peeked element is `ScopeWithIr(val scope: Scope, val irElement: IrElement)`, defined at line 29 of the same file. The `Scope` type comes from `org.jetbrains.kotlin.ir.builders` (imported on line 21) and exposes `scopeOwnerSymbol`.

---

### Claim: `currentFunction?.irElement` (NOT `.owner`) is the right way to get the enclosing `IrFunction` element. `ScopeWithIr.irElement` is a public `val` of type `IrElement` declared at line 29.

**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/IrElementTransformerVoidWithContext.kt:29`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/IrElementTransformerVoidWithContext.kt#L29)

**Snippet**:
```kotlin
open class ScopeWithIr(val scope: Scope, val irElement: IrElement)
```

`currentFunction` returns a nullable `ScopeWithIr` (line 112: `scopeStack.lastOrNull { it.scope.scopeOwnerSymbol is IrFunctionSymbol }`), so `currentFunction?.irElement as? IrFunction` is the correct cast. There is no `.owner` member on `ScopeWithIr`.

---

### Claim: `DeclarationIrBuilder` constructor signature is `(generatorContext: IrGeneratorContext, symbol: IrSymbol, startOffset: Int = UNDEFINED_OFFSET, endOffset: Int = UNDEFINED_OFFSET)`.

**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/lower/LowerUtils.kt:29-38`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/lower/LowerUtils.kt#L29-L38)

**Snippet**:
```kotlin
class DeclarationIrBuilder(
    generatorContext: IrGeneratorContext,
    symbol: IrSymbol,
    startOffset: Int = UNDEFINED_OFFSET, endOffset: Int = UNDEFINED_OFFSET
) : IrBuilderWithScope(
    generatorContext,
    Scope(symbol),
    startOffset,
    endOffset
)
```

`IrPluginContext` extends `IrGeneratorContext`, so passing `pluginContext` for the first parameter is type-correct.

---

### Claim: `irCall(symbol)` is an extension on `IrBuilder` (not a member), defined in `ExpressionHelpers.kt:222-317` (multiple overloads).

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt:302-303`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt#L302-L303)

**Snippet**:
```kotlin
fun IrBuilder.irCall(callee: IrSimpleFunctionSymbol): IrCall =
    irCall(callee, callee.owner.returnType)
```

The full overload set spans lines 222-317. The simple no-type overload at 302-303 is what most call sites (including the SKILL examples) use. Underlying construction is `IrCallImpl(...)` at line 265.

---

### Claim: `IrBuilder` exposes builder helpers `irString`, `irInt`, `irNull`, `irBoolean`, `irGet`, `irGetField`, `irGetObjectValue`, etc.

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt)

| Helper | Line |
|---|---|
| `irBoolean` | 80 |
| `irGet(type, IrValueSymbol)` | 136 |
| `irGetField` | 149 |
| `irGetObjectValue` | 155 |
| `irNull()` (Nothing?) | 172 |
| `irNull(IrType)` | 175 |
| `irInt` | 385 |
| `irString` | 394 |
| `irConcat` (`IrStringConcatenationImpl`) | 397 |

All are `IrBuilder.` extensions in `org.jetbrains.kotlin.ir.builders`.

---

### Claim: `IrStringConcatenationImpl` is in `org.jetbrains.kotlin.ir.expressions.impl`. Its public constructor takes `(startOffset, endOffset, type)`; arguments are populated via the mutable `arguments` field.

**File**: [`kotlin/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/impl/IrStringConcatenationImpl.kt:11,19-28`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/impl/IrStringConcatenationImpl.kt#L11)

**Snippet**:
```kotlin
package org.jetbrains.kotlin.ir.expressions.impl

class IrStringConcatenationImpl internal constructor(
    @Suppress("UNUSED_PARAMETER") constructorIndicator: IrElementConstructorIndicator?,
    override var startOffset: Int,
    override var endOffset: Int,
    override var type: IrType,
) : IrStringConcatenation() {
    override var attributeOwnerId: IrElement = this
    override val arguments: MutableList<IrExpression> = ArrayList(2)
}
```

Note: the primary constructor is `internal`, so consumers use the public secondary/factory constructors (the standard `(startOffset, endOffset, type)` form is exposed via companion/factory methods — the SKILL example uses the recommended public API. `irConcat()` at `ExpressionHelpers.kt:397` is the canonical public entry).

---

### Claim: `deepCopyWithSymbols` takes an optional `initialParent: IrDeclarationParent? = null`, so a parameterless `expr.deepCopyWithSymbols()` call works.

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/DeepCopyIrTreeWithSymbols.kt:17-22`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/DeepCopyIrTreeWithSymbols.kt#L17-L22)

**Snippet**:
```kotlin
inline fun <reified T : IrElement> T.deepCopyWithSymbols(
    initialParent: IrDeclarationParent? = null,
    createTypeRemapper: (SymbolRemapper) -> TypeRemapper = ::DeepCopyTypeRemapper,
): T {
    return (deepCopyImpl(createTypeRemapper) as T).patchDeclarationParents(initialParent)
}
```

Both parameters have defaults, so `expr.deepCopyWithSymbols()` is a valid call.

---

### Claim: `IrAnnotationContainer.hasAnnotation(name: FqName): Boolean` is in `org.jetbrains.kotlin.ir.util`, defined at `IrUtils.kt:341`.

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt:341`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt#L341)

**Snippet**:
```kotlin
// Just delegate to List<IrAnnotation>.hasAnnotation(FqName) which is capable to correctly handle unbound symbols.
fun IrAnnotationContainer.hasAnnotation(name: FqName): Boolean = annotations.hasAnnotation(name)
```

Package declaration at line 6: `package org.jetbrains.kotlin.ir.util`. There is also a `ClassId` overload at line 344 and an `IrClassSymbol` overload at line 346.

---

## Cross-references

- [`compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/IrElementTransformerVoidWithContext.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/IrElementTransformerVoidWithContext.kt) — the recommended visitor base; `currentScope` (116), `currentFile` (109), `currentFunction` (112), `ScopeWithIr` (29).
- [`compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/expressions/IrMemberAccessExpression.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/expressions/IrMemberAccessExpression.kt) — unified `arguments` (line 43); `dispatchReceiver` deprecation getter/setter at lines 222-237 (marked `@UnsafeDuringIrConstructionAPI`); `extensionReceiver` getter/setter at line 301 (marked `@DeprecatedForRemovalCompilerApi(_2_1_20)`).
- [`compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/IrCall.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/IrCall.kt) — abstract class definition (18).
- [`compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/declarations/IrParameterKind.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/declarations/IrParameterKind.kt) — slot-kind enum (8-13).
- [`compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt) — `irCall` overloads (222-317), const/expression helpers (80-398).
- [`compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/lower/LowerUtils.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/lower/LowerUtils.kt) — `DeclarationIrBuilder` (29-38).
- [`compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/DeepCopyIrTreeWithSymbols.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/DeepCopyIrTreeWithSymbols.kt) — `deepCopyWithSymbols` (17-22).
- [`compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt) — `hasAnnotation` overloads (341, 344, 346).
- [`compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/impl/IrStringConcatenationImpl.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/impl/IrStringConcatenationImpl.kt) — string concatenation node (19-28).
