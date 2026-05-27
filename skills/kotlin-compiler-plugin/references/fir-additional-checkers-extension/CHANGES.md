# Changes affecting this skill

API migrations relevant to writing FIR additional-checkers extensions. This skill targets the **current stable Kotlin** (2.3.x).

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
