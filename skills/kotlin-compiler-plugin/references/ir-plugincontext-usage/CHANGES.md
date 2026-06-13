# Changes affecting this skill

API migrations relevant to using `IrPluginContext`. This skill targets the **current stable Kotlin** (2.4.0).

## Kotlin 2.2 → 2.3 (and trailing earlier deprecations still encountered)

### Modern lookup API: `finderForBuiltins()` / `finderForSource(IrFile)`

The earlier `IrPluginContext.referenceClass`, `referenceClassifier`, `referenceConstructors`, `referenceFunctions`, `referenceProperties` are now `@Deprecated(level = WARNING)`. They still work but record incremental-compilation lookups incorrectly (as if every file referenced the symbol).

**Migration**:

| Old | New |
|---|---|
| `pluginContext.referenceClass(classId)` | `pluginContext.finderForBuiltins().findClass(classId)` (for stdlib / required library) |
| `pluginContext.referenceFunctions(callableId)` | `pluginContext.finderForBuiltins().findFunctions(callableId)` |
| (when the lookup is *because of* something in a specific user file) | `pluginContext.finderForSource(fromFile).findClass(...)` |

For lookups derived from a specific user file (e.g. you're transforming `irFile` and need a symbol it references), use `finderForSource(irFile)` so incremental compilation only re-runs your plugin against files that actually used the symbol.

### `IrPluginContext.messageCollector` deprecated in favour of `diagnosticReporter`

`messageCollector: MessageCollector` on `IrPluginContext` is now `@Deprecated(WARNING)` (KT-78277). Use the more structured `diagnosticReporter: IrDiagnosticReporter`.

```kotlin
// Older
pluginContext.messageCollector.report(CompilerMessageSeverity.WARNING, "...")

// Modern (preferred for new code)
pluginContext.diagnosticReporter.at(element, file).report(MyDiagnostics.X)
```

For ad-hoc one-line warnings without a registered factory, `messageCollector` is still the easiest path; opt-in with `@Suppress("DEPRECATION")` until a richer alternative ships.

### `symbolTable` and `moduleDescriptor` are `@ObsoleteDescriptorBasedAPI`

These two members on `IrPluginContext` are K1-era descriptor-based plumbing. They still exist for backwards compatibility but new code (K2 only) should not reach for them. Use the `DeclarationFinder` methods and FIR symbols instead.
