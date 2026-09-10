# Evidence Dossier: ir-plugincontext-usage

All claims are anchored in the upstream Kotlin compiler sources at
`/Users/kitakkun/Documents/GitHub/kotlin-lang/`. Paths below are written as
`kotlin/<path>:line` for review against the published Kotlin source tree.

Primary file under review:
[`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt)

---

### Claim: `finderForBuiltins(): DeclarationFinder` is the IC-aware lookup factory for symbols treated as referenced from every file (stdlib, hard plugin dependency).
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:68`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L68)
**Snippet**:
```kotlin
fun finderForBuiltins(): DeclarationFinder
```

### Claim: `finderForSource(fromFile: IrFile): DeclarationFinder` is the IC-aware lookup factory that records the lookup as originating from a specific user file.
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:75`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L75)
**Snippet**:
```kotlin
fun finderForSource(fromFile: IrFile): DeclarationFinder
```

### Claim: The `DeclarationFinder` interface declares exactly five lookup methods (`findClass`, `findClassifier`, `findConstructors`, `findFunctions`, `findProperties`).
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:141-170`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L141-L170)
**Snippet**:
```kotlin
interface DeclarationFinder {
    fun findClass(classId: ClassId): IrClassSymbol?
    fun findClassifier(classId: ClassId): IrSymbol?
    fun findConstructors(classId: ClassId): Collection<IrConstructorSymbol>
    fun findFunctions(callableId: CallableId): Collection<IrSimpleFunctionSymbol>
    fun findProperties(callableId: CallableId): Collection<IrPropertySymbol>
}
```

### Claim: All five legacy `reference*` methods are annotated `@Deprecated(level = DeprecationLevel.WARNING)`.
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:86`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L86) (`referenceClass`)
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:92`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L92) (`referenceClassifier`)
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:99`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L99) (`referenceConstructors`)
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:105`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L105) (`referenceFunctions`)
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:111`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L111) (`referenceProperties`)

### Claim: The deprecation message literal directs callers to the new finder API.
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:31`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L31)
**Snippet**:
```kotlin
private const val OLD_REFERENCE_API_DEPRECATION_MESSAGE = "Please use `finderForBuiltins()` or `finderForSource(fromFile)` instead."
```

### Claim: `diagnosticReporter: IrDiagnosticReporter` is the modern (non-deprecated) diagnostic entry point on `IrPluginContext`.
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:46`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L46)
**Snippet**:
```kotlin
val diagnosticReporter: IrDiagnosticReporter
```

### Claim: `metadataDeclarationRegistrar: IrGeneratedDeclarationsRegistrar` is the entry point for metadata-visible IR-stage declarations and annotations.
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:54`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L54)
**Snippet**:
```kotlin
val metadataDeclarationRegistrar: IrGeneratedDeclarationsRegistrar
```

### Claim: `messageCollector` on `IrPluginContext` is `@Deprecated(level = WARNING)` and the deprecation message references YouTrack issue KT-78277.
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:127-131`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L127-L131)
**Snippet**:
```kotlin
@Deprecated(
    "Consider using diagnosticReporter instead. See https://youtrack.jetbrains.com/issue/KT-78277 for more details",
    level = DeprecationLevel.WARNING
)
val messageCollector: MessageCollector
```

### Claim: `recordLookup(declaration: IrDeclarationWithName, fromFile: IrFile)` is the manual IC lookup recording API.
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:120`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L120)
**Snippet**:
```kotlin
fun recordLookup(declaration: IrDeclarationWithName, fromFile: IrFile)
```

### Claim: `afterK2: Boolean` indicates whether the frontend was K2.
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:39`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L39)
**Snippet**:
```kotlin
val afterK2: Boolean
```

### Claim: `platform: TargetPlatform?` exposes the compilation target (JVM/JS/Native/Wasm/Common) and is nullable.
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:41`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L41)
**Snippet**:
```kotlin
val platform: TargetPlatform?
```

### Claim: `symbolTable` and `moduleDescriptor` are annotated `@ObsoleteDescriptorBasedAPI` (K1 holdovers).
**File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:133-137`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L133-L137)
**Snippet**:
```kotlin
@ObsoleteDescriptorBasedAPI
val symbolTable: ReferenceSymbolTable

@ObsoleteDescriptorBasedAPI
val moduleDescriptor: ModuleDescriptor
```

### Claim: `UnsafeDuringIrConstructionAPI` is declared in the `org.jetbrains.kotlin.ir.symbols` package (not `org.jetbrains.kotlin.ir.util`), in a separate file `IrSymbol.kt`.
**File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/symbols/IrSymbol.kt:34`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/symbols/IrSymbol.kt#L34)
**Snippet**:
```kotlin
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
annotation class UnsafeDuringIrConstructionAPI
```
