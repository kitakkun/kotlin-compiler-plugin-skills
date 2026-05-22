# Evidence for SKILL.md

Primary-source citations for claims in `SKILL.md`. All paths are rooted under `kotlin/` (= `/Users/kitakkun/Documents/GitHub/kotlin-lang/`).

## `needTransformSupertypes(declaration)` signature

### Claim: `abstract fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirSupertypeGenerationExtension.kt:26`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirSupertypeGenerationExtension.kt#L26)
- **Snippet**:
  ```kotlin
  abstract fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean
  ```

## `computeAdditionalSupertypes(...)` signature

### Claim: returns `List<ConeKotlinType>` and takes `(FirClassLikeDeclaration, List<FirResolvedTypeRef>, TypeResolveService)`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirSupertypeGenerationExtension.kt:28-32`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirSupertypeGenerationExtension.kt#L28-L32)
- **Snippet**:
  ```kotlin
  abstract fun computeAdditionalSupertypes(
      classLikeDeclaration: FirClassLikeDeclaration,
      resolvedSupertypes: List<FirResolvedTypeRef>,
      typeResolver: TypeResolveService
  ): List<ConeKotlinType>
  ```

## `TypeResolveService.resolveUserType(...)` signature

### Claim: `TypeResolveService.resolveUserType(userType: FirUserTypeRef): FirResolvedTypeRef` is the only method on the service
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirSupertypeGenerationExtension.kt:58-60`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirSupertypeGenerationExtension.kt#L58-L60)
- **Snippet**:
  ```kotlin
  abstract class TypeResolveService {
      abstract fun resolveUserType(type: FirUserTypeRef): FirResolvedTypeRef
  }
  ```

## `computeAdditionalSupertypesForGeneratedNestedClass` signature

### Claim: experimental hook for generated nested classes; default returns empty
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirSupertypeGenerationExtension.kt:50-54`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirSupertypeGenerationExtension.kt#L50-L54)
- **Snippet**:
  ```kotlin
  @ExperimentalSupertypesGenerationApi
  open fun computeAdditionalSupertypesForGeneratedNestedClass(
      klass: FirRegularClass,
      typeResolver: TypeResolveService
  ): List<ConeKotlinType> = emptyList()
  ```

### Claim: limitations — one level only, doesn't work for top-level generated classes; default `Any` is removed if non-empty returned
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirSupertypeGenerationExtension.kt:34-49`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirSupertypeGenerationExtension.kt#L34-L49) (KDoc)
- **Snippet**:
  ```
  If some new types will be generated, then default `Any` supertype will be automatically removed
  IMPORTANT: this method works only on one level, which means that it won't be called for the nested class of the generated class.
    It also doesn't work for top-level classes
  ```

## `@ExperimentalSupertypesGenerationApi` annotation (with `Level.ERROR`)

### Claim: opt-in annotation declared with `RequiresOptIn.Level.ERROR`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirSupertypeGenerationExtension.kt:65-66`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirSupertypeGenerationExtension.kt#L65-L66)
- **Snippet**:
  ```kotlin
  @RequiresOptIn("This API is experimental and works for a limited number of cases", level = RequiresOptIn.Level.ERROR)
  annotation class ExperimentalSupertypesGenerationApi
  ```

## `typeFromQualifierParts` location

### Claim: in [`compiler/fir/resolve/src/.../RawUserTypeBuilder.kt`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/RawUserTypeBuilder.kt), takes a `TypeResolveService` and returns `ConeKotlinType`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/RawUserTypeBuilder.kt:31-43`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/RawUserTypeBuilder.kt#L31-L43)
- **Snippet**:
  ```kotlin
  fun typeFromQualifierParts(
      isMarkedNullable: Boolean,
      typeResolver: FirSupertypeGenerationExtension.TypeResolveService,
      source: KtSourceElement,
      builder: QualifierPartBuilder.() -> Unit
  ): ConeKotlinType { ... }
  ```

## `AbstractSimpleClassPredicateMatchingService` location

### Claim: utility base class lives under `compiler/fir/providers/.../extensions/utils/`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/utils/AbstractSimpleClassPredicateMatchingService.kt:23`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/utils/AbstractSimpleClassPredicateMatchingService.kt#L23)
- **Snippet**:
  ```kotlin
  abstract class AbstractSimpleClassPredicateMatchingService(session: FirSession) : FirExtensionSessionComponent(session) {
      protected abstract val predicate: DeclarationPredicate
      ...
      fun isAnnotated(symbol: FirRegularClassSymbol): Boolean
  ```

## `kotlinx-serialization` uses this extension

### Claim: `SerializationFirSupertypesExtension` extends `FirSupertypeGenerationExtension`
- **File**: [`kotlin/plugins/kotlinx-serialization/kotlinx-serialization.k2/src/org/jetbrains/kotlinx/serialization/compiler/fir/SerializationFirSupertypesExtension.kt:36`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/kotlinx-serialization/kotlinx-serialization.k2/src/org/jetbrains/kotlinx/serialization/compiler/fir/SerializationFirSupertypesExtension.kt#L36)
- **Snippet**:
  ```kotlin
  class SerializationFirSupertypesExtension(session: FirSession) : FirSupertypeGenerationExtension(session) {
  ```

### Claim: kotlinx-serialization uses `typeFromQualifierParts`
- **File**: [`kotlin/plugins/kotlinx-serialization/kotlinx-serialization.k2/src/org/jetbrains/kotlinx/serialization/compiler/fir/SerializationFirSupertypesExtension.kt:19`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/kotlinx-serialization/kotlinx-serialization.k2/src/org/jetbrains/kotlinx/serialization/compiler/fir/SerializationFirSupertypesExtension.kt#L19)
- **Snippet**:
  ```kotlin
  import org.jetbrains.kotlin.fir.extensions.typeFromQualifierParts
  ```

### Claim: kotlinx-serialization injects its `SerializerFactory` supertype **only** on non-JVM/Metadata targets (JS/Native/Wasm); JVM/Metadata get a different codegen path.
- **File**: [`kotlin/plugins/kotlinx-serialization/kotlinx-serialization.k2/src/org/jetbrains/kotlinx/serialization/compiler/fir/SerializationFirSupertypesExtension.kt:38`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/kotlinx-serialization/kotlinx-serialization.k2/src/org/jetbrains/kotlinx/serialization/compiler/fir/SerializationFirSupertypesExtension.kt#L38)
- **Snippet**:
  ```kotlin
  private val isJvmOrMetadata = !session.moduleData.platform.run { isNative() || isJs() || isWasm() }
  ```
  Then `isCompanionAndNeedsFactory(...)` immediately early-returns when `isJvmOrMetadata` is true (line 124). So the factory supertype is injected when the boolean is **false** — i.e. on JS/Native/Wasm only. SKILL.md previously framed this gating in the wrong direction.

### Claim: companion-of-annotated-parent matching pattern
- **File**: [`kotlin/plugins/kotlinx-serialization/kotlinx-serialization.k2/src/org/jetbrains/kotlinx/serialization/compiler/fir/SerializationFirSupertypesExtension.kt:49-56`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/kotlinx-serialization/kotlinx-serialization.k2/src/org/jetbrains/kotlinx/serialization/compiler/fir/SerializationFirSupertypesExtension.kt#L49-L56)
- **Snippet**:
  ```kotlin
  private fun isCompanionAndNeedsFactory(declaration: FirClassLikeDeclaration): Boolean {
      if (isJvmOrMetadata) return false
      if (declaration !is FirRegularClass) return false
      if (!declaration.isCompanion) return false
      val parentSymbol = declaration.symbol.getContainingDeclaration(session) as FirClassSymbol<*>
      return session.predicateBasedProvider.matches(annotatedWithSerializableOrMeta, parentSymbol)
              && parentSymbol.companionNeedsSerializerFactory(session)
  }
  ```

## Parcelize does NOT use this extension (verified absence)

### Claim: Parcelize is not a user of `FirSupertypeGenerationExtension`
- **Verification**: `grep -r "FirSupertypeGenerationExtension" kotlin/plugins/parcelize/` returns no matches.
- **File**: [`kotlin/plugins/parcelize/parcelize-compiler/parcelize.k2/src/org/jetbrains/kotlin/parcelize/fir/FirParcelizeExtensionRegistrar.kt:18-23`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/parcelize/parcelize-compiler/parcelize.k2/src/org/jetbrains/kotlin/parcelize/fir/FirParcelizeExtensionRegistrar.kt#L18-L23)
- **Snippet** (only declaration generator + checkers are registered; no supertype generator):
  ```kotlin
  override fun ExtensionRegistrarContext.configurePlugin() {
      +::FirParcelizeDeclarationGenerator.bind(parcelizeAnnotationFqNames)
      +::firParcelizeCheckersExtension

      registerDiagnosticContainers(KtErrorsParcelize)
  }
  ```
