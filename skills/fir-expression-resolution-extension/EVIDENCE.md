# Evidence for SKILL.md

## `addNewImplicitReceivers` signature

### Claim: `abstract fun addNewImplicitReceivers(functionCall: FirFunctionCall, sessionHolder: SessionAndScopeSessionHolder, containingCallableSymbol: FirBasedSymbol<*>): List<ImplicitExtensionReceiverValue>`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirExpressionResolutionExtension.kt:29-33`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirExpressionResolutionExtension.kt#L29-L33)
- **Snippet**:
  ```kotlin
  abstract fun addNewImplicitReceivers(
      functionCall: FirFunctionCall,
      sessionHolder: SessionAndScopeSessionHolder,
      containingCallableSymbol: FirBasedSymbol<*>,
  ): List<ImplicitExtensionReceiverValue>
  ```

## `SessionAndScopeSessionHolder` location

### Claim: declared as an interface combining `SessionHolder` and `ScopeSessionHolder`
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/SessionHolder.kt:18`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/tree/src/org/jetbrains/kotlin/fir/SessionHolder.kt#L18)
- **Snippet**: `interface SessionAndScopeSessionHolder : SessionHolder, ScopeSessionHolder`

## `ImplicitExtensionReceiverValue` location

### Claim: class declared in `FirReceivers.kt`, takes `FirReceiverParameterSymbol` + `ConeKotlinType` + sessions
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/resolve/calls/FirReceivers.kt:190-209`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/providers/src/org/jetbrains/kotlin/fir/resolve/calls/FirReceivers.kt#L190-L209)
- **Snippet**:
  ```kotlin
  class ImplicitExtensionReceiverValue private constructor(
      boundSymbol: FirReceiverParameterSymbol,
      type: ConeKotlinType,
      originalType: ConeKotlinType,
      useSiteSession: FirSession,
      scopeSession: ScopeSession,
      mutable: Boolean,
  ) : ImplicitReceiverValue<FirReceiverParameterSymbol>(...)
  ```

## `captureValueInAnalyze` extensions

### Claim: `var FirReceiverParameter.captureValueInAnalyze: Boolean?` and `val FirReceiverParameterSymbol.captureValueInAnalyze: Boolean`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirExpressionResolutionExtension.kt:49-50`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirExpressionResolutionExtension.kt#L49-L50)
- **Snippet**:
  ```kotlin
  var FirReceiverParameter.captureValueInAnalyze: Boolean? by FirDeclarationDataRegistry.data(CaptureValueInAnalyzeKey)
  val FirReceiverParameterSymbol.captureValueInAnalyze: Boolean get() = fir.captureValueInAnalyze ?: true
  ```

## Real plugin examples

### Claim: `kotlin-dataframe`'s `ReturnTypeBasedReceiverInjector` extends `FirExpressionResolutionExtension`
- **File**: [`kotlin/plugins/kotlin-dataframe/kotlin-dataframe.k2/src/org/jetbrains/kotlinx/dataframe/plugin/extensions/ReturnTypeBasedReceiverInjector.kt:29`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/kotlin-dataframe/kotlin-dataframe.k2/src/org/jetbrains/kotlinx/dataframe/plugin/extensions/ReturnTypeBasedReceiverInjector.kt#L29)
- **Snippet**: `class ReturnTypeBasedReceiverInjector(session: FirSession) : FirExpressionResolutionExtension(session)`

### Claim: `plugin-sandbox`'s `AlgebraReceiverInjector` extends `FirExpressionResolutionExtension`
- **File**: [`kotlin/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/AlgebraReceiverInjector.kt:31`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/AlgebraReceiverInjector.kt#L31)
- **Snippet**: `class AlgebraReceiverInjector(session: FirSession) : FirExpressionResolutionExtension(session)`

### Claim: `plugin-sandbox`'s `DataFrameLikeReturnTypeInjector` extends `FirExpressionResolutionExtension`
- **File**: [`kotlin/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/DataFrameLikeReturnTypeInjector.kt:32`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/DataFrameLikeReturnTypeInjector.kt#L32)
- **Snippet**: `class DataFrameLikeReturnTypeInjector(session: FirSession) : FirExpressionResolutionExtension(session)`

## `KaFirCompilerFacility` location

### Claim: `KaFirCompilerFacility` lives at `kotlin/analysis/analysis-api-fir/...`
- **File**: [`kotlin/analysis/analysis-api-fir/src/org/jetbrains/kotlin/analysis/api/fir/components/KaFirCompilerFacility.kt:158`](https://github.com/JetBrains/kotlin/blob/v2.3.21/analysis/analysis-api-fir/src/org/jetbrains/kotlin/analysis/api/fir/components/KaFirCompilerFacility.kt#L158)
- **Snippet**: `internal class KaFirCompilerFacility(`
