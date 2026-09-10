# Evidence dossier — `ir-body-modification`

Citations are against the Kotlin compiler source tree at `/Users/kitakkun/Documents/GitHub/kotlin-lang/`. Paths use `kotlin/<path>:line` form so reviewers can map them directly onto the upstream layout.

## IR body shapes

### Claim: `IrBlockBody` exposes `statements: MutableList<IrStatement>` via `IrStatementContainer`

**File**: [`kotlin/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/IrBlockBody.kt:18`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/IrBlockBody.kt#L18)

**Snippet**:
```kotlin
abstract class IrBlockBody : IrBody(), IrStatementContainer {
```

**File**: [`kotlin/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/IrStatementContainer.kt:17-19`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/IrStatementContainer.kt#L17-L19)

**Snippet**:
```kotlin
interface IrStatementContainer : IrElement {
    val statements: MutableList<IrStatement>
}
```

### Claim: `IrExpressionBody` is a sibling body type carrying a single `expression`

**File**: [`kotlin/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/IrExpressionBody.kt:17-18`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/IrExpressionBody.kt#L17-L18)

**Snippet**:
```kotlin
abstract class IrExpressionBody : IrBody() {
    abstract var expression: IrExpression
```

### Claim: `IrSyntheticBody` is the body type used for compiler-synthesised members (e.g. data class `equals`)

**File**: [`kotlin/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/IrSyntheticBody.kt:16-17`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/IrSyntheticBody.kt#L16-L17)

**Snippet**:
```kotlin
abstract class IrSyntheticBody : IrBody() {
    abstract var kind: IrSyntheticBodyKind
```

### Claim: All three are sealed under `IrBody`

**File**: [`kotlin/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/IrBody.kt:18`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/IrBody.kt#L18)

**Snippet**:
```kotlin
sealed class IrBody : IrElementBase(), IrElement {
```

## Builder helpers in `ExpressionHelpers.kt`

### Claim: `irBlockBody { ... }` exists as a builder DSL entry point

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt:448-457`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt#L448-L457)

**Snippet**:
```kotlin
inline fun IrBuilderWithScope.irBlockBody(
    startOffset: Int = this.startOffset,
    endOffset: Int = this.endOffset,
    body: IrBlockBodyBuilder.() -> Unit
): IrBlockBody =
```

### Claim: `irBlock(resultType = ...)` accepts an explicit result type for the synthesised container expression

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt:409-421`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt#L409-L421)

**Snippet**:
```kotlin
inline fun IrBuilderWithScope.irBlock(
    startOffset: Int = this.startOffset,
    endOffset: Int = this.endOffset,
    origin: IrStatementOrigin? = null,
    resultType: IrType? = null,
    body: IrBlockBuilder.() -> Unit
): IrContainerExpression =
```

### Claim: `irTry(type, tryResult, catches, finallyExpression)` is the exact signature

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt:406-407`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt#L406-L407)

**Snippet**:
```kotlin
fun IrBuilder.irTry(type: IrType, tryResult: IrExpression, catches: List<IrCatch>, finallyExpression: IrExpression?) =
    IrTryImpl(startOffset, endOffset, type, tryResult, catches, finallyExpression)
```

### Claim: `irReturn(value)` is on `IrBuilderWithScope`

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt:71-78`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt#L71-L78)

**Snippet**:
```kotlin
fun IrBuilderWithScope.irReturn(value: IrExpression) =
    IrReturnImpl(
        startOffset, endOffset,
        context.irBuiltIns.nothingType,
        scope.scopeOwnerSymbol as? IrReturnTargetSymbol
            ?: throw AssertionError(...),
        value
    )
```

### Claim: `irExprBody(value)` constructs an `IrExpressionBody` via the irFactory

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt:65-66`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt#L65-L66)

**Snippet**:
```kotlin
fun IrBuilder.irExprBody(value: IrExpression) =
    context.irFactory.createExpressionBody(startOffset, endOffset, value)
```

### Claim: `irGetField(receiver, field)` — receiver first, then field (NOT `irGet(field, receiver)`)

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt:149-150`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt#L149-L150)

**Snippet**:
```kotlin
fun IrBuilder.irGetField(receiver: IrExpression?, field: IrField, type: IrType = field.type) =
    IrGetFieldImpl(startOffset, endOffset, field.symbol, type, receiver)
```

### Claim: `irTemporary` parameter is named `nameHint` (not `name`)

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt:47-53`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt#L47-L53)

**Snippet**:
```kotlin
fun <T : IrElement> IrStatementsBuilder<T>.irTemporary(
    value: IrExpression? = null,
    nameHint: String? = null,
    irType: IrType = value?.type!!,
    isMutable: Boolean = false,
    origin: IrDeclarationOrigin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
): IrVariable {
```

## Function parameters API

### Claim: `IrFunction.parameters: List<IrValueParameter>` is the unified accessor

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/declarations/IrFunction.kt:45-47`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/declarations/IrFunction.kt#L45-L47)

**Snippet**:
```kotlin
@OptIn(DelicateIrParameterIndexSetter::class)
var parameters: List<IrValueParameter>
    get() = _parameters
```

The KDoc immediately above (lines 41-44) states the parameter ordering is `[dispatch receiver, context parameters, extension receiver, regular parameters]` — confirming this single list replaces the older `valueParameters` accessor (which is no longer present in this file). `dispatchReceiverParameter` survives only as a derived getter (line 62-63).

### Claim: `IrParameterKind` enum has exactly `DispatchReceiver`, `Context`, `ExtensionReceiver`, `Regular`

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/declarations/IrParameterKind.kt:8-13`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/declarations/IrParameterKind.kt#L8-L13)

**Snippet**:
```kotlin
enum class IrParameterKind {
    DispatchReceiver,
    Context,
    ExtensionReceiver,
    Regular,
}
```

## `IrDeclarationOrigin.GeneratedByPlugin`

### Claim: Public constructor takes a `GeneratedDeclarationKey`

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/declarations/IrDeclarationOrigin.kt:131-132`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/declarations/IrDeclarationOrigin.kt#L131-L132)

**Snippet**:
```kotlin
class GeneratedByPlugin private constructor(val pluginId: String, val pluginKey: GeneratedDeclarationKey?) : IrDeclarationOrigin {
    constructor(pluginKey: GeneratedDeclarationKey) : this(pluginKey::class.qualifiedName!!, pluginKey)
```

The primary constructor is private; callers use the secondary `constructor(pluginKey: GeneratedDeclarationKey)` form, which derives `pluginId` from the key's qualified class name. The companion `fromSerializedString` (line 135-138) is the deserialisation path used for klibs (where `pluginKey` is `null`).

## Transformer surface

### Claim: `IrElementTransformerVoidWithContext.visitFunctionNew` exists at line 129

**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/IrElementTransformerVoidWithContext.kt:129-131`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/IrElementTransformerVoidWithContext.kt#L129-L131)

**Snippet**:
```kotlin
open fun visitFunctionNew(declaration: IrFunction): IrStatement {
    return super.visitFunction(declaration)
}
```

### Claim: Plain `IrElementTransformerVoid` has only `visitFunction` (no `visitFunctionNew`)

**File**: [`kotlin/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/visitors/IrElementTransformerVoid.kt:71-78`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/visitors/IrElementTransformerVoid.kt#L71-L78)

**Snippet**:
```kotlin
open fun visitFunction(declaration: IrFunction): IrStatement =
    ...
final override fun visitFunction(declaration: IrFunction, data: Nothing?): IrStatement =
    visitFunction(declaration)
```

A grep for `visitFunctionNew` in this file returns no hits — the `New`-suffixed entry point is only on the `WithContext` subclass.

## Slot accessor on `IrCall`

### Claim: `arguments[i]` is the canonical slot accessor; `dispatchReceiver` is a convenience that delegates to `arguments[0]`

**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/expressions/IrMemberAccessExpression.kt:222-237`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/expressions/IrMemberAccessExpression.kt#L222-L237)

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
            require(value == null) {
                "${this.javaClass.simpleName} has no argument slot for the corresponding dispatch receiver parameter. ..."
            }
        }
    }
```

The doc block immediately above (lines 109-140) explicitly recommends using `arguments[0]` / `arguments[1]` directly when dealing with calls of known shape, and notes that `dispatchReceiver` may be deprecated in future. The unified `arguments` list itself is declared at line 41:

```kotlin
val arguments: ValueArgumentsList = ValueArgumentsList()
```

— matching the cross-references in `guide.md` and `CHANGES.md`.
