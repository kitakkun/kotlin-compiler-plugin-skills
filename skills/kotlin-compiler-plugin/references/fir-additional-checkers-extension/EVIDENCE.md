# Evidence for guide.md

Primary-source citations against `/Users/kitakkun/Documents/GitHub/kotlin-lang/`. Paths use the `kotlin/<path>` form (rooted at the repo).

## FirAdditionalCheckersExtension open members

### Claim: `FirAdditionalCheckersExtension` exposes four open checker buckets, all defaulting to `EMPTY`.
- **File**: [`kotlin/compiler/fir/checkers/src/org/jetbrains/kotlin/fir/analysis/extensions/FirAdditionalCheckersExtension.kt:18-26`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/checkers/src/org/jetbrains/kotlin/fir/analysis/extensions/FirAdditionalCheckersExtension.kt#L18-L26)
- **Snippet**:
```kotlin
abstract class FirAdditionalCheckersExtension(session: FirSession) : FirExtension(session) {
    companion object {
        val NAME: FirExtensionPointName = FirExtensionPointName("ExtensionCheckers")
    }

    open val declarationCheckers: DeclarationCheckers = DeclarationCheckers.EMPTY
    open val expressionCheckers: ExpressionCheckers = ExpressionCheckers.EMPTY
    open val typeCheckers: TypeCheckers = TypeCheckers.EMPTY
    open val languageVersionSettingsCheckers: LanguageVersionSettingsCheckers = LanguageVersionSettingsCheckers.EMPTY
```

### Claim: `fun interface Factory : FirExtension.Factory<FirAdditionalCheckersExtension>`
- **File**: [`kotlin/compiler/fir/checkers/src/org/jetbrains/kotlin/fir/analysis/extensions/FirAdditionalCheckersExtension.kt:31`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/checkers/src/org/jetbrains/kotlin/fir/analysis/extensions/FirAdditionalCheckersExtension.kt#L31)

## FirDeclarationChecker.check context-parameter signature

### Claim: `check` is declared with context parameters, not value parameters.
- **File**: [`kotlin/compiler/fir/checkers/src/org/jetbrains/kotlin/fir/analysis/checkers/declaration/FirDeclarationChecker.kt:15-18`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/checkers/src/org/jetbrains/kotlin/fir/analysis/checkers/declaration/FirDeclarationChecker.kt#L15-L18)
- **Snippet**:
```kotlin
abstract class FirDeclarationChecker<D : FirDeclaration>(final override val mppKind: MppCheckerKind) : FirCheckerWithMppKind {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    abstract fun check(declaration: D)
}
```

Note: `D` is invariant, not `in D` (intentional — see file comment line 14).

## Fir*Checker typealiases

### Claim: `FirRegularClassChecker`, `FirNamedFunctionChecker`, `FirPropertyChecker`, `FirFileChecker`, `FirBasicDeclarationChecker` are typealiases of `FirDeclarationChecker<...>`.
- **File**: [`kotlin/compiler/fir/checkers/gen/org/jetbrains/kotlin/fir/analysis/checkers/declaration/FirDeclarationCheckerAliases.kt:36-45`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/checkers/gen/org/jetbrains/kotlin/fir/analysis/checkers/declaration/FirDeclarationCheckerAliases.kt#L36-L45)
- **Snippet**:
```kotlin
typealias FirBasicDeclarationChecker = FirDeclarationChecker<FirDeclaration>
typealias FirNamedFunctionChecker = FirDeclarationChecker<FirNamedFunction>
typealias FirPropertyChecker = FirDeclarationChecker<FirProperty>
typealias FirRegularClassChecker = FirDeclarationChecker<FirRegularClass>
typealias FirFileChecker = FirDeclarationChecker<FirFile>
```

Note: `FirNamedFunctionChecker` is `FirDeclarationChecker<FirNamedFunction>`. At v2.4.10 the same alias (same file, line 39) was named `FirSimpleFunctionChecker`; upstream commit `6790ace6fb17` ("FE: rename FirSimpleFunctionChecker -> FirNamedFunctionChecker (#6036)", between v2.4.10 and v2.4.20) renamed it, and `git grep FirSimpleFunctionChecker v2.4.20 -- compiler plugins` returns nothing, so no deprecated alias is kept.

### Claim: the `DeclarationCheckers` bucket for named functions is `namedFunctionCheckers` (was `simpleFunctionCheckers` at v2.4.10).
- **File**: [`kotlin/compiler/fir/checkers/gen/org/jetbrains/kotlin/fir/analysis/checkers/declaration/DeclarationCheckers.kt:26`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/checkers/gen/org/jetbrains/kotlin/fir/analysis/checkers/declaration/DeclarationCheckers.kt#L26)
- **Snippet**:
```kotlin
open val namedFunctionCheckers: Set<FirNamedFunctionChecker> = emptySet()
```

At v2.4.10 line 26 of the same file read `open val simpleFunctionCheckers: Set<FirSimpleFunctionChecker> = emptySet()`.

### Claim: `FirFunctionCallChecker` and `FirReturnExpressionChecker` are typealiases of `FirExpressionChecker<...>`.
- **File**: [`kotlin/compiler/fir/checkers/gen/org/jetbrains/kotlin/fir/analysis/checkers/expression/FirExpressionCheckerAliases.kt:56,66`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/checkers/gen/org/jetbrains/kotlin/fir/analysis/checkers/expression/FirExpressionCheckerAliases.kt#L56)
- **Snippet**:
```kotlin
typealias FirFunctionCallChecker = FirExpressionChecker<FirFunctionCall>
typealias FirReturnExpressionChecker = FirExpressionChecker<FirReturnExpression>
```

### Claim: `FirTypeRefChecker = FirTypeChecker<FirTypeRef>`.
- **File**: [`kotlin/compiler/fir/checkers/gen/org/jetbrains/kotlin/fir/analysis/checkers/type/FirTypeCheckerAliases.kt:18`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/checkers/gen/org/jetbrains/kotlin/fir/analysis/checkers/type/FirTypeCheckerAliases.kt#L18)

## KtDiagnosticsContainer.getRendererFactory()

### Claim: `KtDiagnosticsContainer` is abstract and exposes `getRendererFactory(): BaseDiagnosticRendererFactory` as a function (not a property — to avoid cyclic init).
- **File**: [`kotlin/compiler/frontend.common/src/org/jetbrains/kotlin/diagnostics/KtDiagnosticsContainer.kt:10-15`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/frontend.common/src/org/jetbrains/kotlin/diagnostics/KtDiagnosticsContainer.kt#L10-L15)
- **Snippet**:
```kotlin
abstract class KtDiagnosticsContainer {
    /**
     * !!!! Don't convert this function to property, as it might lead to cyclic initialization problems !!!!
     */
    abstract fun getRendererFactory(): BaseDiagnosticRendererFactory
}
```

## error0 / error1 / ... / warning4 helpers

### Claim: `error0` ... `error4` and `warning0` ... `warning4` are top-level inline functions with `context(container: KtDiagnosticsContainer)`, returning `*DelegateProvider`.
- **File**: [`kotlin/compiler/frontend.common-psi/src/org/jetbrains/kotlin/diagnostics/KtDiagnosticFactoryDsl.kt:30-103`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/frontend.common-psi/src/org/jetbrains/kotlin/diagnostics/KtDiagnosticFactoryDsl.kt#L30-L103)
- **Snippet**:
```kotlin
context(container: KtDiagnosticsContainer)
inline fun <reified P : PsiElement> warning0(
    positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT
): DiagnosticFactory0DelegateProvider { ... }

context(container: KtDiagnosticsContainer)
inline fun <reified P : PsiElement> error0(
    positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT
): DiagnosticFactory0DelegateProvider { ... }
```

The full set: `warning0..warning4`, `error0..error4` (lines 31, 38, 45, 52, 59 for warnings; 71, 78, 85, 92, 99 for errors). The same file also declares the sourceless helpers `strongWarningWithoutSource()` (line 16), `warningWithoutSource()` (line 21), `infoWithoutSource()` (line 26, new in v2.4.20) and `errorWithoutSource()` (line 66), all `context(container: KtDiagnosticsContainer)` and returning `SourcelessDiagnosticFactoryDelegateProvider`. This is also where the `context(container: KtDiagnosticsContainer)` requirement comes from — the helpers must be called from within a `KtDiagnosticsContainer` (i.e. inside the `object MyDiagnostics : KtDiagnosticsContainer()` body).

## KtDiagnosticFactoryToRendererMap

### Claim: package is `org.jetbrains.kotlin.diagnostics`, NOT `org.jetbrains.kotlin.diagnostics.rendering`.
- **File**: [`kotlin/compiler/frontend.common/src/org/jetbrains/kotlin/diagnostics/KtDiagnosticFactoryToRendererMap.kt:6`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/frontend.common/src/org/jetbrains/kotlin/diagnostics/KtDiagnosticFactoryToRendererMap.kt#L6)
- **Snippet**:
```kotlin
package org.jetbrains.kotlin.diagnostics
```

### Claim: primary constructor is `internal`.
- **File**: [`kotlin/compiler/frontend.common/src/org/jetbrains/kotlin/diagnostics/KtDiagnosticFactoryToRendererMap.kt:11`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/frontend.common/src/org/jetbrains/kotlin/diagnostics/KtDiagnosticFactoryToRendererMap.kt#L11)
- **Snippet**:
```kotlin
class KtDiagnosticFactoryToRendererMap internal constructor(val name: String) {
```

### Claim: top-level factory function `KtDiagnosticFactoryToRendererMap(name, init)` returns `Lazy<KtDiagnosticFactoryToRendererMap>`.
- **File**: [`kotlin/compiler/frontend.common/src/org/jetbrains/kotlin/diagnostics/KtDiagnosticFactoryToRendererMap.kt:129-136`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/frontend.common/src/org/jetbrains/kotlin/diagnostics/KtDiagnosticFactoryToRendererMap.kt#L129-L136)
- **Snippet**:
```kotlin
fun KtDiagnosticFactoryToRendererMap(
    name: String,
    init: (KtDiagnosticFactoryToRendererMap) -> Unit,
): Lazy<KtDiagnosticFactoryToRendererMap> {
    return lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        KtDiagnosticFactoryToRendererMap(name).also(init)
    }
}
```

This is what makes the `override val MAP by KtDiagnosticFactoryToRendererMap("...") { ... }` `by`-delegate idiom work — the function returns `Lazy<...>`.

## BaseDiagnosticRendererFactory.MAP

### Claim: `BaseDiagnosticRendererFactory` is abstract and declares `abstract val MAP: KtDiagnosticFactoryToRendererMap`.
- **File**: [`kotlin/compiler/frontend.common/src/org/jetbrains/kotlin/diagnostics/rendering/DiagnosticRendererFactory.kt:16-24`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/frontend.common/src/org/jetbrains/kotlin/diagnostics/rendering/DiagnosticRendererFactory.kt#L16-L24)
- **Snippet**:
```kotlin
abstract class BaseDiagnosticRendererFactory : DiagnosticRendererFactory {
    override operator fun invoke(diagnostic: KtDiagnostic): KtDiagnosticRenderer? {
        val factory = diagnostic.factory
        @Suppress("UNCHECKED_CAST")
        return MAP[factory]
    }

    abstract val MAP: KtDiagnosticFactoryToRendererMap
}
```

## SourceElementPositioningStrategies.MODALITY_MODIFIER and friends

### Claim: `MODALITY_MODIFIER`, `VISIBILITY_MODIFIER`, `NAME_IDENTIFIER` exist on `SourceElementPositioningStrategies`.
- **File**: [`kotlin/compiler/frontend.common-psi/src/org/jetbrains/kotlin/diagnostics/SourceElementPositioningStrategies.kt:74-82, 303-305`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/frontend.common-psi/src/org/jetbrains/kotlin/diagnostics/SourceElementPositioningStrategies.kt#L74-L82)
- **Snippet**:
```kotlin
val VISIBILITY_MODIFIER = SourceElementPositioningStrategy(
    LightTreePositioningStrategies.VISIBILITY_MODIFIER,
    PositioningStrategies.VISIBILITY_MODIFIER
)

val MODALITY_MODIFIER = SourceElementPositioningStrategy(
    LightTreePositioningStrategies.MODALITY_MODIFIER,
    PositioningStrategies.MODALITY_MODIFIER
)
...
val NAME_IDENTIFIER = SourceElementPositioningStrategy(
    LightTreePositioningStrategies.NAME_IDENTIFIER,
    PositioningStrategies.NAME_IDENTIFIER
```

The other strategies named in the guide's table also exist at v2.4.20 as `val`s of `SourceElementPositioningStrategies`: `DECLARATION_RETURN_TYPE` (line 39), `DECLARATION_SIGNATURE` (59), `SUPERTYPES_LIST` (184), plus `DEFAULT`, `INLINE_FUN_MODIFIER`, `OPERATOR`, `TYPE_PARAMETERS_LIST`, `VAL_OR_VAR_NODE`, `OVERRIDE_MODIFIER`. Between v2.4.10 and v2.4.20, `VALUE_ARGUMENTS` was removed (`VALUE_ARGUMENTS_LIST` at line 179 remains) and `RECEIVER_OF_DOT_QUALIFIED` was added (line 213).

## reporter.reportOn(source, factory) context-receiver form

### Claim: `reportOn` has a `context(context: DiagnosticContext)` overload that omits the trailing `context` argument.
- **File**: [`kotlin/compiler/frontend.common/src/org/jetbrains/kotlin/diagnostics/KtDiagnosticReportHelpers.kt:35-42`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/frontend.common/src/org/jetbrains/kotlin/diagnostics/KtDiagnosticReportHelpers.kt#L35-L42)
- **Snippet**:
```kotlin
context(context: DiagnosticContext)
fun DiagnosticReporter.reportOn(
    source: AbstractKtSourceElement?,
    factory: KtDiagnosticFactory0,
    positioningStrategy: AbstractSourceElementPositioningStrategy? = null
) {
    report(factory.on(source.requireNotNull(), positioningStrategy, context), context)
}
```

The non-context overload (lines 26-33) takes a trailing `context: DiagnosticContext` value parameter. `CheckerContext` is a `DiagnosticContext`, so the context-receiver form picks up the in-scope `context` automatically. Equivalent context-receiver overloads exist for `KtDiagnosticFactory1`...`KtDiagnosticFactory4` (lines 56, 80, 105, 132).

Note: `requireNotNull` on the source means a `null` source raises `IllegalArgumentException` (relevant to the SKILL gotcha around fake/null sources).

## -Xcontext-parameters flag

### Claim: on 2.4.20 a redundant `-Xcontext-parameters` is reported as `w: The argument '-Xcontext-parameters' is redundant for the current language version 2.4.`
- **File**: [`kotlin/compiler/cli/src/org/jetbrains/kotlin/cli/common/arguments.kt:212-215`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/src/org/jetbrains/kotlin/cli/common/arguments.kt#L212-L215)
- **Snippet**:
  ```kotlin
  this.report(
      CliDiagnostics.REDUNDANT_CLI_ARG,
      "The argument '$renderedArgument' is redundant for the current language version $languageVersion.",
  )
  ```
- **Notes**: `REDUNDANT_CLI_ARG` is a `strongWarningWithoutSource()` factory (`compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/CliDiagnostics.kt:17`), rendered as `w:`. Observed verbatim in `verification/17-named-function-checker` on 2.4.20; the line is suppressed at Gradle's `-q` log level.

### Claim: `infoWithoutSource()` lives in `compiler/frontend.common-psi`, not `compiler/frontend.common`
- **File**: [`kotlin/compiler/frontend.common-psi/src/org/jetbrains/kotlin/diagnostics/KtDiagnosticFactoryDsl.kt:25-28`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/frontend.common-psi/src/org/jetbrains/kotlin/diagnostics/KtDiagnosticFactoryDsl.kt#L25-L28)
- **Snippet**:
  ```kotlin
  context(container: KtDiagnosticsContainer)
  fun infoWithoutSource(): SourcelessDiagnosticFactoryDelegateProvider {
      return SourcelessDiagnosticFactoryDelegateProvider(Severity.INFO, container)
  }
  ```

### Claim: the flag `-Xcontext-parameters` exists and enables `LanguageFeature.ContextParameters`.
- **File**: [`kotlin/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt:288-293`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt#L288-L293)
- **Snippet**:
```kotlin
@Argument(
    value = "-Xcontext-parameters",
    description = "Enable experimental context parameters.",
)
@Enables(LanguageFeature.ContextParameters)
var contextParameters: Boolean = false
```

## MppCheckerKind {Common, Platform} (session-routing semantics)

### Claim: `Common` runs in the session owning the declaration; `Platform` runs in the leaf-platform session against sources from all modules. This is session-routing, not an expect/actual filter.
- **File**: [`kotlin/compiler/fir/checkers/src/org/jetbrains/kotlin/fir/analysis/checkers/MppCheckerKind.kt:8-19`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/checkers/src/org/jetbrains/kotlin/fir/analysis/checkers/MppCheckerKind.kt#L8-L19)
- **Snippet**:
```kotlin
/**
 * - [MppCheckerKind.Common] means that this checker should run from the same
 *   session to which corresponding declaration belongs
 * - [MppCheckerKind.Platform] means that in case of MPP compilation this
 *   checker should run with session of leaf platform module for sources
 *   of all modules
 *
 *  For more information see the doc: compiler/fir/checkers/module.md
 */
enum class MppCheckerKind {
    Common, Platform
}
```

The KDoc never mentions `expect`/`actual` filtering — it only documents session routing.

## registerDiagnosticContainers(...) on ExtensionRegistrarContext

### Claim: `registerDiagnosticContainers(vararg KtDiagnosticsContainer)` is a member of `FirExtensionRegistrar.ExtensionRegistrarContext`, accumulating into the registrar's diagnostic-container list.
- **File**: [`kotlin/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt:232-234`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt#L232-L234)
- **Snippet**:
```kotlin
// ------------------ diagnostics ------------------

fun registerDiagnosticContainers(vararg diagnosticContainers: KtDiagnosticsContainer) {
    this@FirExtensionRegistrar.diagnosticsContainers += diagnosticContainers
}
```

The accumulated list is then handed to the session's `registeredDiagnosticFactoriesStorage` at registration time:
- **File**: [`kotlin/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt:325`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt#L325)
- **Snippet**:
```kotlin
session.registeredDiagnosticFactoriesStorage.registerDiagnosticContainers(registeredExtensions.diagnosticsContainers)
```

The same idiom is used internally for built-in containers — e.g. [`kotlin/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/checkers/CheckersContainers.kt:35`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/checkers/CheckersContainers.kt#L35) calls `registerDiagnosticContainers(FirErrors, FirSyntaxErrors, CliFrontendDiagnostics)`. So plugins follow exactly the same registration path the compiler uses for its own diagnostics.
