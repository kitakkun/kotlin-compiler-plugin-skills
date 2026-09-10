# Evidence Dossier: ir-synthetic-class-generation

Each claim below cites the upstream Kotlin compiler source under
`/Users/kitakkun/Documents/GitHub/kotlin-lang/`. Paths are written in
`kotlin/<path>:line` form (the leading `kotlin/` stands in for that local
checkout root).

---

### Claim: `pluginContext.irFactory` is inherited from `IrGeneratorContext`, whose `irFactory` defaults to `irBuiltIns.irFactory`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/IrGenerator.kt:34-36`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/IrGenerator.kt#L34-L36)
**Snippet**:
```kotlin
interface IrGeneratorContext : IrGeneratorContextInterface {
    val irFactory: IrFactory get() = irBuiltIns.irFactory
}
```

### Claim: `LoweringContext` also exposes `irFactory` (for plugin contexts that derive from it)
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/LoweringContext.kt:38`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/LoweringContext.kt#L38)
**Snippet**:
```kotlin
interface LoweringContext : LoggingContext, ErrorReportingContext {
    ...
    val irFactory: IrFactory
```

### Claim: `factory.buildClass { ... }` DSL is defined on `IrFactory`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt:50`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt#L50)
**Snippet**:
```kotlin
inline fun IrFactory.buildClass(builder: IrClassBuilder.() -> Unit) =
    IrClassBuilder().run {
        builder()
        buildClass(this)
    }
```

### Claim: `IrFactory.buildField { ... }` DSL is defined on `IrFactory`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt:74`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt#L74)
**Snippet**:
```kotlin
inline fun IrFactory.buildField(builder: IrFieldBuilder.() -> Unit) =
    IrFieldBuilder().run {
        builder()
        buildField(this)
    }
```

### Claim: `IrClass.addProperty { ... }` is an extension on `IrClass`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt:127`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt#L127)
**Snippet**:
```kotlin
inline fun IrClass.addProperty(builder: IrPropertyBuilder.() -> Unit): IrProperty =
    factory.buildProperty(builder).also { property ->
        declarations.add(property)
        property.parent = this@addProperty
    }
```

### Claim: `IrClass.addFunction { ... }` is an extension on `IrClass`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt:280`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt#L280)
**Snippet**:
```kotlin
inline fun IrClass.addFunction(builder: IrFunctionBuilder.() -> Unit): IrSimpleFunction =
    factory.addFunction(this, builder)
```

### Claim: `IrClass.addConstructor { ... }` is an extension on `IrClass` and auto-sets `returnType = defaultType`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt:320`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt#L320)
**Snippet**:
```kotlin
inline fun IrClass.addConstructor(builder: IrFunctionBuilder.() -> Unit = {}): IrConstructor =
    factory.buildConstructor {
        builder()
        returnType = defaultType
    }.also { constructor ->
        declarations.add(constructor)
        constructor.parent = this@addConstructor
    }
```

### Claim: `addDefaultGetter` lives in `org.jetbrains.kotlin.ir.builders.declarations` (NOT `ir.util`)
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt:155`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt#L155)
**Snippet**:
```kotlin
fun IrProperty.addDefaultGetter(parentClass: IrClass, builtIns: IrBuiltIns) {
    val field = backingField!!
    addGetter {
        origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
        returnType = field.type
    }.apply { ... }
}
```
The package declaration of this file is `org.jetbrains.kotlin.ir.builders.declarations` (line 7 of the same file). No definition of `addDefaultGetter` exists under `org.jetbrains.kotlin.ir.util`.

### Claim: Internal generated `IrInstanceInitializerCallImpl` constructor uses parameter order `(start, end, type, classSymbol)`
**File**: [`kotlin/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/impl/IrInstanceInitializerCallImpl.kt:19-25`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/impl/IrInstanceInitializerCallImpl.kt#L19-L25)
**Snippet**:
```kotlin
class IrInstanceInitializerCallImpl internal constructor(
    @Suppress("UNUSED_PARAMETER") constructorIndicator: IrElementConstructorIndicator?,
    override var startOffset: Int,
    override var endOffset: Int,
    override var type: IrType,
    override var classSymbol: IrClassSymbol,
) : IrInstanceInitializerCall() { ... }
```

### Claim: Public factory `IrInstanceInitializerCallImpl(...)` uses parameter order `(start, end, classSymbol, type)` — opposite of the internal constructor; named arguments required
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/expressions/impl/builders.kt:515-526`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/expressions/impl/builders.kt#L515-L526)
**Snippet**:
```kotlin
fun IrInstanceInitializerCallImpl(
    startOffset: Int,
    endOffset: Int,
    classSymbol: IrClassSymbol,
    type: IrType,
) = IrInstanceInitializerCallImpl(
    constructorIndicator = null,
    startOffset = startOffset,
    endOffset = endOffset,
    classSymbol = classSymbol,
    type = type,
)
```

### Claim: `irDelegatingConstructorCall` is an `IrBuilder` extension producing `IrDelegatingConstructorCallImpl`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt:334`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt#L334)
**Snippet**:
```kotlin
fun IrBuilder.irDelegatingConstructorCall(callee: IrConstructor): IrDelegatingConstructorCall =
    IrDelegatingConstructorCallImpl(
        startOffset, endOffset, context.irBuiltIns.unitType, callee.symbol,
        callee.parentAsClass.typeParameters.size
    )
```

### Claim: `createThisReceiverParameter()` is the current API for installing the implicit dispatch receiver on an `IrClass`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt:1105`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt#L1105)
**Snippet**:
```kotlin
fun IrClass.createThisReceiverParameter() {
    thisReceiver = buildReceiverParameter {
        origin = IrDeclarationOrigin.INSTANCE_RECEIVER
        type = symbol.typeWithParameters(typeParameters)
    }
}
```

### Claim: `createParameterDeclarations()` is deprecated for removal at 2.1.20, with replacement `createThisReceiverParameter()`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt:1623-1625`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt#L1623-L1625)
**Snippet**:
```kotlin
@DeprecatedForRemovalCompilerApi(CompilerVersionOfApiDeprecation._2_1_20, replaceWith = "createThisReceiverParameter()")
fun IrClass.createParameterDeclarations() =
    createThisReceiverParameter()
```

### Claim: `IrGeneratedDeclarationsRegistrar.registerFunctionAsMetadataVisible(IrSimpleFunction)` exists
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt:22`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt#L22)
**Snippet**:
```kotlin
abstract fun registerFunctionAsMetadataVisible(irFunction: IrSimpleFunction)
```

### Claim: `IrGeneratedDeclarationsRegistrar.registerConstructorAsMetadataVisible(IrConstructor)` exists
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt:23`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt#L23)
**Snippet**:
```kotlin
abstract fun registerConstructorAsMetadataVisible(irConstructor: IrConstructor)
```

### Claim: `IrGeneratedDeclarationsRegistrar.addMetadataVisibleAnnotationsToElement(IrDeclaration, List<IrAnnotation>)` exists
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt:16`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt#L16)
**Snippet** (v2.4.0 — the element type changed from `IrConstructorCall` to `IrAnnotation`; `IrAnnotation : IrConstructorCall()`, built via `DeclarationIrBuilder.irAnnotation(...)`):
```kotlin
abstract fun addMetadataVisibleAnnotationsToElement(declaration: IrDeclaration, annotations: List<IrAnnotation>)
```

A `vararg` convenience overload is also provided at line 18:
```kotlin
fun addMetadataVisibleAnnotationsToElement(declaration: IrDeclaration, vararg annotations: IrAnnotation)
```

### Claim: `IrGeneratedDeclarationsRegistrar.addCustomMetadataExtension(...)` exists
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt:28`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt#L28)
**Snippet**:
```kotlin
abstract fun addCustomMetadataExtension(
    irDeclaration: IrDeclaration,
    pluginId: String,
    data: ByteArray,
)
```

### Claim: KT-63881 TODO marks the absent `registerPropertyAsMetadataVisible` variant
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt:25`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt#L25)
**Snippet**:
```kotlin
// TODO: KT-63881
// abstract fun registerPropertyAsMetadataVisible(irProperty: IrProperty)
```

### Claim: `registerClassAsMetadataVisible` does NOT exist anywhere in the Kotlin repo
**Verification**: `grep -rn "registerClassAsMetadataVisible" /Users/kitakkun/Documents/GitHub/kotlin-lang/` returns no matches (and the same scoped to `compiler/` returns no matches). The `IrGeneratedDeclarationsRegistrar` abstract class above lists every `register…AsMetadataVisible` member it declares, and none mentions `Class`.

### Claim: `IrClass.companionObject()` extension lives at `AdditionalIrUtils.kt:194`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/AdditionalIrUtils.kt:194`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/AdditionalIrUtils.kt#L194)
**Snippet**:
```kotlin
@UnsafeDuringIrConstructionAPI
fun IrClass.companionObject(): IrClass? =
    this.declarations.singleOrNull { it is IrClass && it.isCompanion } as IrClass?
```
