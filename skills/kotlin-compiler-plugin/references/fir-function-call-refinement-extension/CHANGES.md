# Changes affecting this skill

API migrations relevant to writing `FirFunctionCallRefinementExtension`. This skill targets the **current stable Kotlin** (2.4.20).

The extension is gated by `@FirExtensionApiInternals` and the source explicitly warns it is "highly unstable and not recommended to use" — expect API breakage at almost every Kotlin minor. The notes below are the breaks that actually shipped.

## Kotlin 2.4.10 → 2.4.20

### `KtFakeSourceElementKind.PluginGenerated` became a sealed class; generated local declarations must have distinct source elements

Commit `a509d381297f` ("[FIR] Require distinct source elements for local plugin-generated declarations in source FIR files", KT-84344) turned the plain `object PluginGenerated` into `sealed class PluginGenerated { object Default; class Custom(val marker: Any) }` (`KtSourceElement.kt:889-917` at v2.4.20). The commit message acknowledges this is source-breaking and accepts it because the FIR plugin API is not stabilized.

**What breaks**: any value usage of the old object, i.e. `source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)`, no longer compiles. `is KtFakeSourceElementKind.PluginGenerated` type checks keep working (they now match both subclasses).

**New contract for this extension**: the KDoc added at the same time (`FirFunctionCallRefinementExtension.kt:35-44`) requires every local declaration generated into an existing source file to carry a *distinct* source element, built with a `PluginGenerated` kind — preferably `PluginGenerated.Custom` with a marker unique per generated declaration. Top-level declarations from `FirDeclarationGenerationExtension` remain exempt (`KtSourceElement.kt:1015-1032`), but the local classes this extension injects are not. The official test infrastructure now enforces it: `FirDistinctSourceElementsHandler` is in the default diagnostic-test handler set (`BaseDiagnosticConfiguration.kt:174`), so two generated local classes sharing a source element fail a diagnostic test with "Duplicate source elements in test file ...".

Before (2.4.10):

```kotlin
val schema = buildRegularClass {
    source = callSite.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
    // ...
}
val scope = buildRegularClass {
    source = callSite.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated) // same (realSource, kind) as `schema`
    // ...
}
```

After (2.4.20):

```kotlin
private sealed class MySourceKind {
    data class Schema(val name: String) : MySourceKind()
    data class Scope(val name: String) : MySourceKind()
}

val schema = buildRegularClass {
    source = callSite.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated.Custom(MySourceKind.Schema(schemaId.shortClassName.asString())))
    // ...
}
val scope = buildRegularClass {
    source = callSite.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated.Custom(MySourceKind.Scope(scopeId.shortClassName.asString())))
    // ...
}
```

**How to migrate**: replace `KtFakeSourceElementKind.PluginGenerated` with `KtFakeSourceElementKind.PluginGenerated.Default` where you only need "some plugin-generated kind" (declaration-generation extensions, supertype refs), and with `PluginGenerated.Custom(marker)` for every local declaration emitted from `intercept`/`transform`. The marker must have stable `equals`/`hashCode`/`toString` — a `data class` keyed by the generated class name is what kotlin-dataframe uses (`FunctionCallTransformer.kt:119-127`, `:321-325`, `:611-618` at v2.4.20). Multi-version plugins need a version-specific source set or reflection for this call, since the object and the sealed class cannot both be referenced from one compilation unit.

### KDoc reworded (no API change)

The class KDoc was restructured into a bullet list and the `!!!! ... !!!!` banner became `**This extension is highly unstable and not recommended to use!**` (`FirFunctionCallRefinementExtension.kt:21-45`). The five abstract methods, `CallReturnType`, `callRefinementExtensions`, the `CandidateFactory.replaceFromPluginsIfNeeded` intercept site and the `FirExpressionsResolveTransformer` transform site are unchanged apart from line drift.


## Kotlin 2.2.10 → 2.2.20 (the major API migration)

Commit `352f6704de6c` (April 2025) — "K2/plugins: migrate `FirFunctionCallRefinementExtension` to provide API for KT-77157" — first shipped in **Kotlin 2.2.20**. This is a substantive API change, not a rename.

The pre-migration API exposed a single `intercept`-like method that returned the new return type. Post-migration, the API is the **two-phase intercept/transform** contract documented in `guide.md`:

```kotlin
abstract fun intercept(callInfo: CallInfo, symbol: FirNamedFunctionSymbol): CallReturnType?
abstract fun transform(call: FirFunctionCall, originalSymbol: FirNamedFunctionSymbol): FirFunctionCall
abstract fun ownsSymbol(symbol: FirRegularClassSymbol): Boolean
abstract fun anchorElement(symbol: FirRegularClassSymbol): KtSourceElement
abstract fun restoreSymbol(call: FirFunctionCall, name: Name): FirRegularClassSymbol?
```

The `CallReturnType(typeRef, callback)` wrapper, the `let { ... }`-block code-generation pattern in `transform`, and the `ownsSymbol` / `anchorElement` / `restoreSymbol` triplet for IDE integration **did not exist before 2.2.20**. Older plugins (notably `kotlin-dataframe.k2`'s `FunctionCallTransformer`) had to be rewritten against the new shape — see commit `352f6704de6c`'s diff for the canonical migration.

**Migration from 2.2.10 and earlier**: rewrite the extension class against the new five-method shape. The dataframe plugin's pre/post diff is the best worked example. There is no source-compatible bridging — pick the minimum Kotlin version your plugin supports and target that API.

## Kotlin 2.3.0 → 2.3.20

### `FirSimpleFunction` rename does not affect this extension

The 2.3.20 rename of `FirSimpleFunction` → `FirNamedFunction` (see CHANGES.md for [`fir-sam-conversion-transformer-extension`](../fir-sam-conversion-transformer-extension/guide.md)) does **not** affect this extension's API surface — it works on `FirNamedFunctionSymbol` and `FirFunctionCall`, neither of which were renamed.

## Kotlin 2.0.0 → 2.2.10

The pre-migration API existed in roughly the shape `KT-77157` later replaced. If you're targeting these versions, the extension takes a very different form — refer to the corresponding kotlin-lang source at the version's tag. Practical advice: don't target these versions for new plugins; the post-2.2.20 API is significantly more capable.

## Internal package move (does not affect plugins)

Commit `ba5fce9e990e` ("[FIR] Move everything related to candidates into dedicated package") shifted internal candidate-resolution classes; this is invisible to plugin authors because the public-facing `intercept`'s `CallInfo` parameter is unaffected.

## Stability outlook

`@FirExtensionApiInternals` is the compiler's signal that the API may change at any minor. Treat each Kotlin minor as a potential breakage; pin your plugin's tested Kotlin version range narrowly.
