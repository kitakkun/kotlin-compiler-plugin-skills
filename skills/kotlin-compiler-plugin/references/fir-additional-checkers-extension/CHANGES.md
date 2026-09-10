# Changes affecting this skill

API migrations relevant to writing FIR additional-checkers extensions. This skill targets the **current stable Kotlin** (2.4.20).

## Kotlin 2.4.10 → 2.4.20

### `FirSimpleFunctionChecker` renamed to `FirNamedFunctionChecker` (and `simpleFunctionCheckers` to `namedFunctionCheckers`)

Upstream commit `6790ace6fb17` ("FE: rename FirSimpleFunctionChecker -> FirNamedFunctionChecker") finished the `FirSimpleFunction` → `FirNamedFunction` naming cleanup on the checker side. At v2.4.20:

- `typealias FirNamedFunctionChecker = FirDeclarationChecker<FirNamedFunction>` (generated: `compiler/fir/checkers/gen/org/jetbrains/kotlin/fir/analysis/checkers/declaration/FirDeclarationCheckerAliases.kt:39`) — the old `FirSimpleFunctionChecker` alias is **gone**, with no deprecated forwarding alias.
- `DeclarationCheckers.namedFunctionCheckers: Set<FirNamedFunctionChecker>` (generated: `compiler/fir/checkers/gen/org/jetbrains/kotlin/fir/analysis/checkers/declaration/DeclarationCheckers.kt:26`) replaces `simpleFunctionCheckers`.

A plugin built against 2.4.10 that references either name fails to compile against 2.4.20 with an unresolved reference (and, for the bucket, `'simpleFunctionCheckers' overrides nothing.` — the message names the *old* identifier still present in your source).

```kotlin
// Before (≤ 2.4.10)
object NoFooChecker : FirSimpleFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirNamedFunction) { /* ... */ }
}

object MyDeclarationCheckers : DeclarationCheckers() {
    override val simpleFunctionCheckers: Set<FirSimpleFunctionChecker> = setOf(NoFooChecker)
}

// After (2.4.20+)
object NoFooChecker : FirNamedFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirNamedFunction) { /* ... */ }
}

object MyDeclarationCheckers : DeclarationCheckers() {
    override val namedFunctionCheckers: Set<FirNamedFunctionChecker> = setOf(NoFooChecker)
}
```

**Migration**: rename the two identifiers. The `check(declaration: FirNamedFunction)` parameter type is unchanged. If one artifact must compile against both 2.4.10 and 2.4.20, declare the checker as `FirDeclarationChecker<FirFunction>` (i.e. a `FirFunctionChecker`, whose alias name is unchanged) with `check(declaration: FirFunction)`, early-return unless `declaration is FirNamedFunction`, and put it in `functionCheckers: Set<FirFunctionChecker>` — that bucket exists under the same name on both versions and is folded into the named-function dispatch (`allNamedFunctionCheckers` at `DeclarationCheckers.kt:52`). `D` is invariant, so a `FirDeclarationChecker<FirNamedFunction>` cannot go into `functionCheckers`; the wider type is the price of the shared bucket, at the cost of also being invoked for constructors, anonymous functions and accessors, so guard with `if (declaration !is FirNamedFunction) return`.

### New `infoWithoutSource()` diagnostic-factory helper

`compiler/frontend.common-psi/src/org/jetbrains/kotlin/diagnostics/KtDiagnosticFactoryDsl.kt:25-28` adds `infoWithoutSource()` (`Severity.INFO`) next to the existing `errorWithoutSource()` / `warningWithoutSource()` / `strongWarningWithoutSource()`. Additive; nothing to migrate.

### `SourceElementPositioningStrategies.VALUE_ARGUMENTS` removed

`SourceElementPositioningStrategies.VALUE_ARGUMENTS` was removed between v2.4.10 and v2.4.20; `VALUE_ARGUMENTS_LIST` remains, and `RECEIVER_OF_DOT_QUALIFIED` was added (`SourceElementPositioningStrategies.kt:209-216`). Only relevant if a factory was declared with `VALUE_ARGUMENTS` — switch it to `VALUE_ARGUMENTS_LIST` or `DEFAULT`.

## Kotlin 2.3 → 2.4

### `CheckerContext` is a `SessionHolder` — don't pass `session` explicitly to `fullyExpandedType`

On 2.4.0, `check()` runs inside `context(context: CheckerContext, reporter: DiagnosticReporter)`, and `CheckerContext` **is a `SessionHolder`**. `ConeKotlinType.fullyExpandedType` gained `context(SessionHolder)` overloads (`TypeExpansionUtils.kt:57`, `:63` at v2.4.0): a no-arg `fullyExpandedType()` that uses the implicit `SessionHolder`, plus a `context(_: SessionHolder) fullyExpandedType(useSiteSession)` form. So calling `coneType.fullyExpandedType(session)` from inside a checker — where a `SessionHolder` is already in implicit scope — now triggers an error along the lines of *"When a SessionHolder is available as an implicit value, passing the session explicitly is only required when it's different…"*.

Two correct shapes:

```kotlin
// (a) Inside the checker — a SessionHolder is implicitly available, so pass nothing:
context(context: CheckerContext, reporter: DiagnosticReporter)
override fun check(expression: FirFunctionCall) {
    val expanded = expression.resolvedType.fullyExpandedType()   // no `session` argument
}

// (b) For 2.3.x ⇔ 2.4.0 cross-compat — expand in a plain helper *outside* any
//     SessionHolder context, where the explicit-session overload still applies:
private fun expand(type: ConeKotlinType, session: FirSession) = type.fullyExpandedType(session)
```

The 2.3.x no-arg overload may be absent, which is why the cross-version path uses an explicit-session helper deliberately placed where no `SessionHolder` is in scope.

### `-Xcontext-parameters` may now warn as redundant

The checker `check()` API has used context parameters since 2.2.20 (see the timeline below), so the `-Xcontext-parameters` flag is still needed for plugins that build against ≤2.3.x. On 2.4.0 the compiler reports the flag as **redundant** for the affected code (2.4.20 text: `w: The argument '-Xcontext-parameters' is redundant for the current language version 2.4.`; hidden under Gradle `-q`). Keep it while you still compile against ≤2.3.x; drop it once you target 2.4+ exclusively.

## Kotlin 2.1.x → 2.2.0 → 2.2.20 (the context-parameter migration timeline)

The `FirDeclarationChecker.check` API moved to context parameters in three stages — the precise version range matters when targeting older compilers:

| Kotlin | `check` signature on `FirDeclarationChecker` |
|---|---|
| 2.1.x and earlier | only `abstract fun check(declaration: D, context: CheckerContext, reporter: DiagnosticReporter)` |
| **2.2.0** | **BOTH forms exist** — context-param form is the new abstract; value-param form is `@DeprecatedForRemovalCompilerApi` and forwards to the new one. Either override compiles. |
| **2.2.20+** | **only context-param form** — `context(context: CheckerContext, reporter: DiagnosticReporter) abstract fun check(declaration: D)`. Value-param overrides no longer satisfy the abstract member; the class fails compilation with "is not abstract and does not implement abstract member 'check'". |
| 2.3.x | unchanged from 2.2.20 |

So a plugin shipping for **Kotlin 2.2.0 only** can still use the value-param form, but anything targeting 2.2.20+ must use context parameters. This was verified empirically against `git show v2.2.0:.../FirDeclarationChecker.kt` and `v2.2.20:` of the same file.

```kotlin
// Kotlin 2.2.20+ form (also works on 2.2.0):
context(context: CheckerContext, reporter: DiagnosticReporter)
abstract fun check(declaration: D)
```

A regular three-parameter override does **not** override the abstract member on 2.2.20+ — it becomes an unrelated method, and the abstract one stays unimplemented.

**Migration**: rewrite every override in your checker classes:

```kotlin
// Before
override fun check(declaration: FirRegularClass, context: CheckerContext, reporter: DiagnosticReporter) {
    reporter.reportOn(declaration.source, MyDiagnostics.X, context)
}

// After
context(context: CheckerContext, reporter: DiagnosticReporter)
override fun check(declaration: FirRegularClass) {
    reporter.reportOn(declaration.source, MyDiagnostics.X)  // no trailing context arg
}
```

You also need `-Xcontext-parameters` in the plugin module's `freeCompilerArgs` (context parameters are still experimentally gated as of 2.3.x):

```kotlin
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
}
```

### `KtDiagnosticFactoryToRendererMap` constructor became `internal`

Older code constructed the map directly:

```kotlin
override val MAP = KtDiagnosticFactoryToRendererMap("MyPlugin").also {
    it.put(MY_FACTORY, "...")
}
```

In 2.3.x, the constructor is `internal` — direct construction from a plugin module fails to compile. The supported entry is the top-level factory function via the `by` delegate:

```kotlin
override val MAP by KtDiagnosticFactoryToRendererMap("MyPlugin") { map ->
    map.put(MY_FACTORY, "...")
}
```

### Package of `KtDiagnosticFactoryToRendererMap`

The class lives in `org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap`, **not** in the `org.jetbrains.kotlin.diagnostics.rendering` package. The `rendering` sub-package contains a separate, older `DiagnosticFactoryToRendererMap` (no `Kt` prefix) which is easy to land on by mistake.

### `MppCheckerKind` semantics clarified

Earlier docs sometimes described `MppCheckerKind` as an "expect/actual filter". The actual semantics (verified against `MppCheckerKind.kt`):

- `Common` — the checker runs in the declaration's owning session (default, what most checkers want).
- `Platform` — the checker runs in the leaf-platform session against sources from all modules. Use only when the check needs the fully-resolved platform view.

Whether to skip `expect` declarations is a separate concern; guard with `if (declaration.isExpect) return` inside the checker body.
