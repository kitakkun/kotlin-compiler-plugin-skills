# Changes affecting this skill

API and behavior migrations relevant to debugging a compiler plugin (visible output, IR validation, phase dumps). This skill targets the **current stable Kotlin** (2.4.20).

## Kotlin 2.4.10 → 2.4.20

### `MESSAGE_COLLECTOR_KEY` / `CompilerConfiguration.messageCollector` now require `@OptIn(MessageCollectorAccess::class)`

`CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY` and the generated `CompilerConfiguration.messageCollector` accessor are annotated with the new `@RequiresOptIn` marker `org.jetbrains.kotlin.config.MessageCollectorAccess` (default level `ERROR`). The registrar snippet from older guides stops compiling with an opt-in error.

```kotlin
// 2.4.10
override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val messageCollector = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
    ...
}

// 2.4.20
import org.jetbrains.kotlin.config.MessageCollectorAccess

@OptIn(MessageCollectorAccess::class)
override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val messageCollector = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
    ...
}
```

**Migration**: add `@OptIn(MessageCollectorAccess::class)` at the use site (or `-opt-in=org.jetbrains.kotlin.config.MessageCollectorAccess` in the plugin module's compiler options). `IrPluginContext.messageCollector` is unaffected — it stays `@Deprecated(WARNING)` and needs only `@Suppress("DEPRECATION")`. For a registrar-time `w:` line without the opt-in use `configuration.report(CliDiagnostics.COMPILER_PLUGIN_INITIALIZATION_WARNING, "...")` (`org.jetbrains.kotlin.cli.report` + `org.jetbrains.kotlin.cli.CliDiagnostics`); it is flushed via `diagnosticsCollector`, so it prints after direct collector output, and it is not available inside `generate(...)`. Opt-in-free `CompilerConfiguration.reportInfo(...)` lives in `org.jetbrains.kotlin.cli`; `reportLog(...)` is implemented in `org.jetbrains.kotlin.config` (`ReportingUtils.kt`) with a forwarding alias in `org.jetbrains.kotlin.cli`. There is no severity-named `reportWarning`.

Upstream: `kotlin/compiler/config/gen/org/jetbrains/kotlin/config/CommonConfigurationKeys.kt:99-101,233-236`, `kotlin/compiler/config/src/org/jetbrains/kotlin/config/MessageCollectorAccess.kt:8-9` (commit `4dacc99b77f9`, KT-78277).

### IR validator reports through `IrDiagnosticReporter` (`IR_VALIDATION_ERROR` / `IR_VALIDATION_WARNING`)

`validateIr(...)` and the `IrValidationPhase` subclasses no longer take a `MessageCollector` + `CompilerMessageSeverity`; they take an `IrDiagnosticReporter` + `IrValidationSeverity` and emit the sourceless diagnostics `IrValidationDiagnostics.IR_VALIDATION_ERROR` / `IR_VALIDATION_WARNING`. Effects for plugin authors:

- The factory name is visible in build output with `-Xrender-internal-diagnostic-names`, so `grep IR_VALIDATION_ERROR` works in CI.
- The reporter-based `validateIr` overload no longer throws `IrValidationException` itself; error-severity violations land in the compilation's diagnostics collector and fail the build like any other `e:` diagnostic.
- Test-infrastructure code or custom drivers that called `validateIr(element, irBuiltIns, config, messageCollector, mode)` must pass an `IrDiagnosticReporter`.

Upstream: `kotlin/compiler/ir/ir.validation/src/org/jetbrains/kotlin/ir/validation/IrValidator.kt:156-209`, `kotlin/compiler/ir/ir.validation/src/org/jetbrains/kotlin/ir/validation/IrValidationDiagnostics.kt:15-17`, `kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/phaser/IrValidationPhase.kt:27-38` (commits `0edc97b2e85b`, `9db629bf7869`).

### `-Xverify-ir-visibility` / `-Xverify-ir-nested-offsets` removed; `-Xdisable-ir-checkers` / `-Xenable-additional-ir-checkers` added

The per-checker switches are gone from `CommonCompilerArguments`. The relaxed visibility, vararg, and cross-file field-usage checks now run unconditionally whenever `-Xverify-ir` is not `none` (`IrFieldVisibilityChecker` stays gated on the `ExplicitBackingFields` language feature); `IrNestedOffsetRangeChecker` is opt-in via the new generic flag.

```kotlin
// 2.4.10
compilerOptions.freeCompilerArgs.addAll("-Xverify-ir=error", "-Xverify-ir-visibility", "-Xverify-ir-nested-offsets")

// 2.4.20
compilerOptions.freeCompilerArgs.addAll("-Xverify-ir=error", "-Xenable-additional-ir-checkers=IrNestedOffsetRangeChecker")
// and, to silence a checker your plugin legitimately violates:
compilerOptions.freeCompilerArgs.add("-Xdisable-ir-checkers=IrFieldVisibilityChecker")
```

**Migration**: drop the removed flags from any `freeCompilerArgs` / test directive; the compiler only emits `Flag is not supported by this version of the compiler: -Xverify-ir-visibility` (a `STRONG_WARNING`, `kotlin/compiler/cli/src/org/jetbrains/kotlin/cli/common/arguments.kt:252-253`), so a stale flag is easy to miss — and with `-Werror` it fails the build. Use `-Xdisable-ir-checkers=<SimpleClassName>` to opt out of a specific checker.

Upstream: `kotlin/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt:367-373,455-461` (commit `8723063d1970`).

### `JvmK1IrValidationBeforeLoweringPhase` and `IrValidationBeforeLoweringPhase` removed

The JVM lowering pipeline now registers a single validation phase, `JvmIrValidationAfterLoweringPhase`. `-Xphases-to-dump-before=JvmK1IrValidationBeforeLoweringPhase` (or `-after=`) silently does nothing on 2.4.20; use `-Xlist-phases` and pick a real phase. The common abstract superclass `IrValidationBeforeLoweringPhase` is gone as well — `KlibIrValidationBeforeLoweringPhase` extends `IrValidationPhase` directly. Pre-lowering validation on JVM happens in FIR2IR (`runMandatoryIrValidation`, phase names `FIR2IR` and `Applying IR compiler plugins`), which is where plugin-introduced IR bugs are attributed.

Upstream: `kotlin/compiler/ir/backend.jvm/lower/src/org/jetbrains/kotlin/backend/jvm/lower/irValidation.kt:15-21`, `kotlin/compiler/ir/backend.jvm/lower/src/org/jetbrains/kotlin/backend/jvm/JvmLoweringPhases.kt:145`, `kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/phaser/IrValidationPhase.kt:41` (commit `425b8d40d14f`).

### `MessageCollectorWithDiagnosticId` (additive)

A new sub-interface `MessageCollectorWithDiagnosticId : MessageCollector` adds `report(severity, message, location, diagnosticId: String?)`; the daemon's `CompileServicesFacadeMessageCollector` implements it (and gained a `warningsAsErrors` constructor parameter). Source-compatible for plugins: calling `report(severity, message)` is unchanged, and a plugin's own `MessageCollector` implementation still satisfies the base interface. Only relevant if you wrap the compiler's collector and want to forward diagnostic IDs.

Upstream: `kotlin/compiler/util/src/org/jetbrains/kotlin/cli/common/messages/MessageCollector.kt:31-59`, `kotlin/compiler/daemon/src/org/jetbrains/kotlin/daemon/report/CompileServicesFacadeMessageCollector.kt:26-30` (commit `4c36640ec819`).
