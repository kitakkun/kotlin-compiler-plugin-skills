# Evidence for guide.md

All citations are against `/Users/kitakkun/Documents/GitHub/kotlin-lang/`.
Primary source: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt).

## `needTransformStatus` signature

### Claim: `abstract fun needTransformStatus(declaration: FirDeclaration): Boolean`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt:26`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt#L26)
- **Snippet**:
  ```kotlin
  abstract fun needTransformStatus(declaration: FirDeclaration): Boolean
  ```

## Typed `transformStatus(...)` overloads (9 total)

### Claim: Generic protected fallback `transformStatus(status, declaration)`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt:28-33`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt#L28-L33)
- **Snippet**:
  ```kotlin
  protected open fun transformStatus(
      status: FirDeclarationStatus,
      declaration: FirDeclaration
  ): FirDeclarationStatus { return status }
  ```

### Claim: `transformStatus(status, property: FirProperty, containingClass, isLocal)`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt:35-42`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt#L35-L42)

### Claim: `transformStatus(status, function: FirNamedFunction, containingClass, isLocal)`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt:44-51`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt#L44-L51)
- **Snippet**:
  ```kotlin
  open fun transformStatus(
      status: FirDeclarationStatus,
      function: FirNamedFunction,
      containingClass: FirClassLikeSymbol<*>?,
      isLocal: Boolean
  ): FirDeclarationStatus
  ```

### Claim: `transformStatus(status, regularClass: FirRegularClass, containingClass, isLocal)`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt:59-66`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt#L59-L66)

### Claim: `transformStatus(status, typeAlias: FirTypeAlias, containingClass, isLocal)`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt:74-81`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt#L74-L81)

### Claim: `transformStatus(status, propertyAccessor: FirPropertyAccessor, containingClass, containingProperty, isLocal)`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt:83-91`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt#L83-L91)
- **Snippet**: This overload is unique — it includes a `containingProperty: FirProperty?` parameter.

### Claim: `transformStatus(status, constructor: FirConstructor, containingClass, isLocal)`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt:93-100`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt#L93-L100)

### Claim: `transformStatus(status, field: FirField, containingClass, isLocal)`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt:102-109`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt#L102-L109)

### Claim: `transformStatus(status, backingField: FirBackingField, containingClass, isLocal)`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt:111-118`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt#L111-L118)

### Claim: `transformStatus(status, enumEntry: FirEnumEntry, containingClass, isLocal)`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt:120-127`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt#L120-L127)

## Visibility-immutability constraints (KDoc quotes)

### Claim: "It's forbidden for this extension to change the visibility of a regular class in any way, as this may influence type resolve thus violating our phase contracts."
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt:53-58`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt#L53-L58)
- **Snippet**:
  ```kotlin
  /**
   * This function may change the status (in general, visibility, modality, and modifiers) of a regular class.
   *
   * Limitation: it's forbidden for this extension to change the visibility of a regular class in any way,
   * as this may influence type resolve thus violating our phase contracts.
   */
  ```

### Claim: "It's forbidden for this extension to change the visibility of a type alias in any way, as this may influence type resolve thus violating our phase contracts."
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt:68-73`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt#L68-L73)
- **Snippet**:
  ```kotlin
  /**
   * This function may change the status (in general, visibility, modality, and modifiers) of a type alias.
   *
   * Limitation: it's forbidden for this extension to change the visibility of a type alias in any way,
   * as this may influence type resolve thus violating our phase contracts.
   */
  ```

## `FirDeclarationStatus.transform(...)` helper

### Claim: helper signature `transform(visibility, modality, init): FirDeclarationStatus`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt:134-138`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt#L134-L138)
- **Snippet**:
  ```kotlin
  inline fun FirDeclarationStatus.transform(
      visibility: Visibility = this.visibility,
      modality: Modality? = this.modality,
      init: FirDeclarationStatusImpl.() -> Unit = {}
  ): FirDeclarationStatus
  ```

## Preserved-flags list (the 17 flags copied by `transform`)

### Claim: full preserved-flag list (`isExpect`, `isActual`, `isOverride`, `isOperator`, `isInfix`, `isInline`, `isValue`, `isTailRec`, `isExternal`, `isConst`, `isLateInit`, `isInner`, `isCompanion`, `isData`, `isSuspend`, `isStatic`, `isFromSealedClass`, `isFromEnumClass`)
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt:141-158`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt#L141-L158)
- **Snippet**:
  ```kotlin
  isExpect = this@transform.isExpect
  isActual = this@transform.isActual
  isOverride = this@transform.isOverride
  isOperator = this@transform.isOperator
  isInfix = this@transform.isInfix
  isInline = this@transform.isInline
  isValue = this@transform.isValue
  isTailRec = this@transform.isTailRec
  isExternal = this@transform.isExternal
  isConst = this@transform.isConst
  isLateInit = this@transform.isLateInit
  isInner = this@transform.isInner
  isCompanion = this@transform.isCompanion
  isData = this@transform.isData
  isSuspend = this@transform.isSuspend
  isStatic = this@transform.isStatic
  isFromSealedClass = this@transform.isFromSealedClass
  isFromEnumClass = this@transform.isFromEnumClass
  ```

## Allopen plugin uses generic `transformStatus(status, declaration)` + `copyWithNewDefaults`

### Claim: Allopen overrides only the protected generic overload, not the typed ones, and uses `copyWithNewDefaults(...)` rather than `transform { ... }`.
- **File**: [`kotlin/plugins/allopen/allopen.k2/src/org/jetbrains/kotlin/allopen/fir/FirAllOpenStatusTransformer.kt:43-54`](https://github.com/JetBrains/kotlin/blob/v2.4.20/plugins/allopen/allopen.k2/src/org/jetbrains/kotlin/allopen/fir/FirAllOpenStatusTransformer.kt#L43-L54)
- **Snippet**:
  ```kotlin
  override fun transformStatus(status: FirDeclarationStatus, declaration: FirDeclaration): FirDeclarationStatus {
      @OptIn(SymbolInternals::class)
      val explicitModality = when (declaration) {
          is FirPropertyAccessor -> declaration.propertySymbol.fir.status.modality
          else -> status.modality
      }

      return when (explicitModality) {
          null -> status.copyWithNewDefaults(modality = Modality.OPEN, defaultModality = Modality.OPEN)
          else -> status.copyWithNewDefaults(defaultModality = Modality.OPEN)
      }
  }
  ```

## `FirNamedFunction` is the current type (rename from `FirSimpleFunction`)

### Claim: The function overload uses parameter type `function: FirNamedFunction` (older codebases used `FirSimpleFunction`).
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt:46`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirStatusTransformerExtension.kt#L46)
- **Snippet**:
  ```kotlin
  function: FirNamedFunction,
  ```
- Confirmed by the import on line 11 (`import org.jetbrains.kotlin.fir.declarations.*`) which resolves `FirNamedFunction` from the `declarations` package; no reference to `FirSimpleFunction` exists in this file.
