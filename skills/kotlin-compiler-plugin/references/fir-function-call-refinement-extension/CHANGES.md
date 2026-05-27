# Changes affecting this skill

API migrations relevant to writing `FirFunctionCallRefinementExtension`. This skill targets the **current stable Kotlin** (2.3.x).

The extension is gated by `@FirExtensionApiInternals` and the source explicitly warns it is "highly unstable and not recommended to use" — expect API breakage at almost every Kotlin minor. The notes below are the breaks that actually shipped.

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
