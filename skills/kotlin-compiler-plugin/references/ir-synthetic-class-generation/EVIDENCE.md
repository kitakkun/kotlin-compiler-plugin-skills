# Evidence Dossier: ir-synthetic-class-generation

Each claim below cites the upstream Kotlin compiler source under
`/Users/kitakkun/Documents/GitHub/kotlin-lang/`. Paths are written in
`kotlin/<path>:line` form (the leading `kotlin/` stands in for that local
checkout root).

---

### Claim: `pluginContext.irFactory` is inherited from `IrGeneratorContext`, whose `irFactory` defaults to `irBuiltIns.irFactory`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/IrGenerator.kt:34-36`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/IrGenerator.kt#L34-L36)
**Snippet**:
```kotlin
interface IrGeneratorContext : IrGeneratorContextInterface {
    val irFactory: IrFactory get() = irBuiltIns.irFactory
}
```

### Claim: `LoweringContext` also exposes `irFactory` (for plugin contexts that derive from it)
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/LoweringContext.kt:35`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/LoweringContext.kt#L35)
**Snippet**:
```kotlin
interface LoweringContext : LoggingContext, ErrorReportingContext {
    ...
    val irFactory: IrFactory
```

### Claim: `factory.buildClass { ... }` DSL is defined on `IrFactory`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt:50`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt#L50)
**Snippet**:
```kotlin
inline fun IrFactory.buildClass(builder: IrClassBuilder.() -> Unit) =
    IrClassBuilder().run {
        builder()
        buildClass(this)
    }
```

### Claim: `IrFactory.buildField { ... }` DSL is defined on `IrFactory`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt:74`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt#L74)
**Snippet**:
```kotlin
inline fun IrFactory.buildField(builder: IrFieldBuilder.() -> Unit) =
    IrFieldBuilder().run {
        builder()
        buildField(this)
    }
```

### Claim: `IrClass.addProperty { ... }` is an extension on `IrClass`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt:127`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt#L127)
**Snippet**:
```kotlin
inline fun IrClass.addProperty(builder: IrPropertyBuilder.() -> Unit): IrProperty =
    factory.buildProperty(builder).also { property ->
        declarations.add(property)
        property.parent = this@addProperty
    }
```

### Claim: `IrClass.addFunction { ... }` is an extension on `IrClass`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt:280`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt#L280)
**Snippet**:
```kotlin
inline fun IrClass.addFunction(builder: IrFunctionBuilder.() -> Unit): IrSimpleFunction =
    factory.addFunction(this, builder)
```

### Claim: `IrClass.addConstructor { ... }` is an extension on `IrClass` and auto-sets `returnType = defaultType`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt:320`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt#L320)
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
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt:155`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt#L155)
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
**File**: [`kotlin/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/impl/IrInstanceInitializerCallImpl.kt:19-25`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/impl/IrInstanceInitializerCallImpl.kt#L19-L25)
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
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/expressions/impl/builders.kt:515-526`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/expressions/impl/builders.kt#L515-L526)
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
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt:334`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/ExpressionHelpers.kt#L334)
**Snippet**:
```kotlin
fun IrBuilder.irDelegatingConstructorCall(callee: IrConstructor): IrDelegatingConstructorCall =
    IrDelegatingConstructorCallImpl(
        startOffset, endOffset, context.irBuiltIns.unitType, callee.symbol,
        callee.parentAsClass.typeParameters.size
    )
```

### Claim: `createThisReceiverParameter()` is the current API for installing the implicit dispatch receiver on an `IrClass`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt:1087`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt#L1087)
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
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt:1633-1635`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt#L1633-L1635)
**Snippet**:
```kotlin
@DeprecatedForRemovalCompilerApi(CompilerVersionOfApiDeprecation._2_1_20, replaceWith = "createThisReceiverParameter()")
fun IrClass.createParameterDeclarations() =
    createThisReceiverParameter()
```

### Claim: `IrGeneratedDeclarationsRegistrar.registerFunctionAsMetadataVisible(IrSimpleFunction)` exists
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt:24`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt#L24)
**Snippet**:
```kotlin
abstract fun registerFunctionAsMetadataVisible(irFunction: IrSimpleFunction)
```

### Claim: `IrGeneratedDeclarationsRegistrar.registerConstructorAsMetadataVisible(IrConstructor)` exists
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt:25`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt#L25)
**Snippet**:
```kotlin
abstract fun registerConstructorAsMetadataVisible(irConstructor: IrConstructor)
```

### Claim: `IrGeneratedDeclarationsRegistrar.addMetadataVisibleAnnotationsToElement(IrDeclaration, List<IrAnnotation>)` exists
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt:18`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt#L18)
**Snippet** (since v2.4.0 — the element type changed from `IrConstructorCall` to `IrAnnotation`; `IrAnnotation : IrConstructorCall()`, built via `DeclarationIrBuilder.irAnnotation(...)`):
```kotlin
abstract fun addMetadataVisibleAnnotationsToElement(declaration: IrDeclaration, annotations: List<IrAnnotation>)
```

A `vararg` convenience overload is also provided at line 20:
```kotlin
fun addMetadataVisibleAnnotationsToElement(declaration: IrDeclaration, vararg annotations: IrAnnotation)
```

### Claim: `IrGeneratedDeclarationsRegistrar.addCustomMetadataExtension(...)` exists
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt:29`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt#L29)
**Snippet**:
```kotlin
abstract fun addCustomMetadataExtension(
    irDeclaration: IrDeclaration,
    pluginId: String,
    data: ByteArray,
)
```

### Claim: `IrGeneratedDeclarationsRegistrar.registerPropertyAsMetadataVisible(IrProperty)` exists (new in 2.4.20; resolves KT-63881)
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt:26`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt#L26)
**Snippet**:
```kotlin
abstract fun registerPropertyAsMetadataVisible(irProperty: IrProperty)
```
At v2.4.10 this line was still the commented-out `// TODO: KT-63881` placeholder; commit `5abd9ccd3cd8` ("[FIR2IR] Support making plugin-generated properties metadata-visible", `^KT-63881 Fixed`) turned it into a real abstract member.

### Claim: `registerPropertyAsMetadataVisible` requires the property to have a getter and skips local properties
**File**: [`kotlin/compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrIrGeneratedDeclarationsRegistrar.kt:174-177`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrIrGeneratedDeclarationsRegistrar.kt#L174-L177)
**Snippet**:
```kotlin
override fun registerPropertyAsMetadataVisible(irProperty: IrProperty) {
    if (irProperty.isLocal || irProperty.parentClassOrNull?.isLocal == true) return
    val irGetter = irProperty.getter
        ?: error("Property without getter is not supported: ${irProperty.render()}")
```

### Claim: `IrGeneratedDeclarationsRegistrar.registerClassAsMetadataVisible(IrClass)` exists (new in 2.4.20; KT-79565)
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt:27`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt#L27)
**Snippet**:
```kotlin
abstract fun registerClassAsMetadataVisible(irClass: IrClass)
```
Added by the three commits `00d2669d672c` / `6b2fb9576a3a` / `077a2bd38394` ("[FIR2IR] Support making plugin-generated classes metadata-visible. Part 1/3 .. 3/3": top-level, nested, and inner classes respectively; all `^KT-79565`). Before 2.4.20 `grep -rn "registerClassAsMetadataVisible"` over the Kotlin repo returned no matches, which is why older tutorials that call it did not compile.

### Claim: `registerClassAsMetadataVisible` rejects enum classes with an `error(...)`
**File**: [`kotlin/compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrIrGeneratedDeclarationsRegistrar.kt:355-358`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrIrGeneratedDeclarationsRegistrar.kt#L355-L358)
**Snippet**:
```kotlin
override fun registerClassAsMetadataVisible(irClass: IrClass) {
    if (irClass.kind == ClassKind.ENUM_CLASS) {
        error("Enum classes are not supported for registerClassAsMetadataVisible: ${irClass.render()}")
    }
```

### Claim: `registerClassAsMetadataVisible` recursively registers the class's constructors, non-accessor functions, properties, and nested classes (skipping fake overrides)
**File**: [`kotlin/compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrIrGeneratedDeclarationsRegistrar.kt:421-437`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrIrGeneratedDeclarationsRegistrar.kt#L421-L437)
**Snippet**:
```kotlin
for (irMember in irClass.declarations) {
    if (irMember.origin == IrDeclarationOrigin.FAKE_OVERRIDE) continue
    when (irMember) {
        is IrConstructor -> {
            registerConstructorAsMetadataVisible(irMember)
        }
        is IrSimpleFunction -> if (irMember.correspondingPropertySymbol == null) {
            registerFunctionAsMetadataVisible(irMember)
        }
        is IrProperty -> {
            registerPropertyAsMetadataVisible(irMember)
        }
        is IrClass -> {
            registerClassAsMetadataVisible(irMember)
        }
        else -> {} // IrAnonymousInitializer, IrField (non-backing) — out of scope
    }
}
```
Sealed subclasses are recorded too (`firClass.setSealedClassInheritors(irClass.sealedSubclasses...)` at line 418), so a sealed hierarchy needs `sealedSubclasses` populated before the call.

### Claim: plugin-sandbox calls `registerClassAsMetadataVisible` once on the outermost class and relies on the recursive member/nested registration
**File**: [`kotlin/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/ir/GeneratedTopLevelClassIrGenerator.kt:115`](https://github.com/JetBrains/kotlin/blob/v2.4.20/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/ir/GeneratedTopLevelClassIrGenerator.kt#L115) and [`kotlin/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/ir/GeneratedTopLevelClassIrGenerator.kt:163`](https://github.com/JetBrains/kotlin/blob/v2.4.20/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/ir/GeneratedTopLevelClassIrGenerator.kt#L163)
**Snippet**:
```kotlin
for (klass in listOf(plain, withGeneric, extendsSource, extendsPlain, mySealed, subA, subB)) {
    file.declarations += klass
    context.metadataDeclarationRegistrar.registerClassAsMetadataVisible(klass)
}
...
context.metadataDeclarationRegistrar.registerClassAsMetadataVisible(withNestedFamily)
```

### Claim: the non-FIR (dummy) registrar in `IrPluginContextImpl` implements the new members as no-ops
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContextImpl.kt:221-223`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContextImpl.kt#L221-L223)
**Snippet**:
```kotlin
override fun registerPropertyAsMetadataVisible(irProperty: IrProperty) {}

override fun registerClassAsMetadataVisible(irClass: IrClass) {}
```

### Claim: `IrClass.companionObject()` extension lives at `AdditionalIrUtils.kt:210`
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/AdditionalIrUtils.kt:210`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/AdditionalIrUtils.kt#L210)
**Snippet**:
```kotlin
@UnsafeDuringIrConstructionAPI
fun IrClass.companionObject(): IrClass? =
    this.declarations.singleOrNull { it is IrClass && it.isCompanion } as IrClass?
```
