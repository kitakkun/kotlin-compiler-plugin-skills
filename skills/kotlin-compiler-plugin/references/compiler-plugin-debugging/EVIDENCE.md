# Evidence for guide.md

## MessageCollector

### Claim: `MessageCollector` interface with `report(severity, message, location)` and `MessageCollector.NONE` no-op
- **File**: [`kotlin/compiler/util/src/org/jetbrains/kotlin/cli/common/messages/MessageCollector.kt:9-29`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/util/src/org/jetbrains/kotlin/cli/common/messages/MessageCollector.kt#L9-L29)
- **Snippet**:
  ```kotlin
  interface MessageCollector {
      fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation? = null)
      companion object { val NONE: MessageCollector = object : MessageCollector { ... } }
  }
  ```

### Claim: `MessageCollectorWithDiagnosticId` (new in 2.4.20) adds a `diagnosticId: String?` overload; the `MessageCollector.report(..., diagnosticId)` extension falls back to the three-arg form for plain collectors
- **File**: [`kotlin/compiler/util/src/org/jetbrains/kotlin/cli/common/messages/MessageCollector.kt:31-59`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/util/src/org/jetbrains/kotlin/cli/common/messages/MessageCollector.kt#L31-L59)
- **Snippet**:
  ```kotlin
  @JvmDefaultWithCompatibility
  interface MessageCollectorWithDiagnosticId : MessageCollector {
      override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
          report(severity, message, location, diagnosticId = null)
      }
      fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation? = null, diagnosticId: String?)
  }
  fun MessageCollector.report(severity, message, location = null, diagnosticId: String?) { when (this) { is MessageCollectorWithDiagnosticId -> ...; else -> report(severity, message, location) } }
  ```
- **Notes**: Added by upstream commit `4c36640ec819` ("Preserve compiler diagnostic IDs before rendering"). Plugin code that only *calls* `report(severity, message)` is unaffected; a plugin that *implements* `MessageCollector` still compiles (the new interface is a separate subtype).

### Claim: `CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY` and `CompilerConfiguration.messageCollector` require `@OptIn(MessageCollectorAccess::class)` since 2.4.20
- **File**: [`kotlin/compiler/config/gen/org/jetbrains/kotlin/config/CommonConfigurationKeys.kt:99-101`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/config/gen/org/jetbrains/kotlin/config/CommonConfigurationKeys.kt#L99-L101) (key), [`:233-236`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/config/gen/org/jetbrains/kotlin/config/CommonConfigurationKeys.kt#L233-L236) (accessor)
- **Snippet**:
  ```kotlin
  @JvmField
  @MessageCollectorAccess
  val MESSAGE_COLLECTOR_KEY = CompilerConfigurationKey.create<MessageCollector>("MESSAGE_COLLECTOR_KEY")

  @MessageCollectorAccess
  var CompilerConfiguration.messageCollector: MessageCollector
      get() = get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
  ```
- **Marker**: [`kotlin/compiler/config/src/org/jetbrains/kotlin/config/MessageCollectorAccess.kt:8-9`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/config/src/org/jetbrains/kotlin/config/MessageCollectorAccess.kt#L8-L9) — `@RequiresOptIn("Direct access to the message collector is discouraged. Consider using `CompilerConfiguration.report`.")` with the default `Level.ERROR` (`kotlin/libraries/stdlib/src/kotlin/annotations/OptIn.kt:101-103`).
- **Notes**: Introduced by commit `4dacc99b77f9` ("[CLI] Add opt-in to CompilerConfiguration.messageCollector", KT-78277). At v2.4.10 the same generated file (`:99-100`, `:236`) carried no annotation. The opt-in-free wrappers live in [`kotlin/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/CliDiagnosticReporting.kt:45-62`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/CliDiagnosticReporting.kt#L45-L62) (`reportInfo`, `reportLog`, `reportOutput`, `reportException`) — there is no `reportWarning`, hence the guide keeps `MessageCollector` + `@OptIn` for visible plugin output.

## IrDiagnosticReporter

### Claim: `IrDiagnosticReporter.at(...)` has exactly four overloads and `at(...).report(factory)` is the modern IR-time API
- **File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/IrDiagnosticReporter.kt:17-53`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/IrDiagnosticReporter.kt#L17-L53)
- **Notes**: The only 2.4.10 → 2.4.20 change in this interface is the sourceless overload, now `report(factory: KtSourcelessDiagnosticFactory, message: String, location: CompilerMessageSourceLocation? = null)` (`:23`) — a new defaulted parameter, source-compatible for callers.

### Claim: `IrPluginContext.messageCollector` is `@Deprecated(level = WARNING)`
- **File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:127-131`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L127-L131)
- **Snippet**:
  ```kotlin
  @Deprecated("Consider using diagnosticReporter instead. ...", level = DeprecationLevel.WARNING)
  val messageCollector: MessageCollector
  ```
- **Notes**: File unchanged between v2.4.10 and v2.4.20. The property itself is *not* annotated with `MessageCollectorAccess`; only the pipeline that feeds it opts in (`kotlin/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/pipeline/convertToIr.kt:249-250`), so `@Suppress("DEPRECATION")` remains sufficient on the plugin side.

## Compiler flags

### Claim: `-Xprint-ir` is Native-only
- **File**: [`kotlin/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/K2NativeCompilerArguments.kt:502-506`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/K2NativeCompilerArguments.kt#L502-L506)
- **Notes**: Defined only in `K2NativeCompilerArguments`; absent from `CommonCompilerArguments` and `K2JVMCompilerArguments`. Verified via grep across the entire `gen/.../arguments/` directory.

### Claim: `-Xphases-to-dump-after`, `-Xphases-to-dump-before`, `-Xlist-phases`
- **File**: [`kotlin/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt:691-694`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt#L691-L694) (`-Xlist-phases`), [`:844-847`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt#L844-L847) (`-Xphases-to-dump-after`), [`:854-857`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt#L854-L857) (`-Xphases-to-dump-before`)

### Claim: `-Xrender-internal-diagnostic-names` exists in the common arguments
- **File**: [`kotlin/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt:925-926`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt#L925-L926)

### Claim: `-Xverify-ir={none|warning|error}` defaults to no verification
- **File**: [`kotlin/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt:1114-1119`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt#L1114-L1119)
- **Snippet**: `description = "IR verification mode (no verification by default).", ... var verifyIr: String? = null`
- **Also**: [`kotlin/compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrConfiguration.kt:71-76`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrConfiguration.kt#L71-L76) — `forJvmCompilation` builds `IrVerificationSettings(mode = compilerConfiguration.get(CommonConfigurationKeys.VERIFY_IR, IrVerificationMode.NONE), ..., validateForKlibSerialization = false)`; and [`kotlin/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/pipeline/convertToIr.kt:483-488`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/pipeline/convertToIr.kt#L483-L488) — `runMandatoryIrValidation` returns `false` immediately when `mode == NONE && !validateForKlibSerialization`.

### Claim: `-Xdisable-ir-checkers` / `-Xenable-additional-ir-checkers` (new in 2.4.20) replace `-Xverify-ir-visibility` / `-Xverify-ir-nested-offsets`
- **File**: [`kotlin/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt:367-373`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt#L367-L373) (`-Xdisable-ir-checkers`), [`:455-461`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt#L455-L461) (`-Xenable-additional-ir-checkers`)
- **Snippet**:
  ```kotlin
  @Argument(value = "-Xdisable-ir-checkers", valueDescription = "<checker1>,<checker2>",
      description = """A list of IR checkers to disable, specified by a simple name of the checker class. A name of an annotation can also be used to match all tagged checkers.
  Only has effect if '-Xverify-ir' is not 'none'.""")
  var disableIrCheckers: Array<String> = emptyArray()

  @Argument(value = "-Xenable-additional-ir-checkers", valueDescription = "<checker1>,<checker2>",
      description = """A list of IR checkers to enable, specified by a simple name of the checker class.
  It may only be used with specific checkers that are not enabled by default, and which are prepared to be enabled this way. Only has effect if '-Xverify-ir' is not 'none'.""")
  var enableAdditionalIrCheckers: Array<String> = emptyArray()
  ```
- **Notes**: Commit `8723063d1970` ("[IR] Refactor configuration of IR checkers") — the v2.4.10 → v2.4.20 diff of the generated file removes `value = "-Xverify-ir-nested-offsets"` and `value = "-Xverify-ir-visibility"` and adds the two flags above. `IrNestedOffsetRangeChecker` is the opt-in checker wired through `withCheckersByName(context.configuration.additionalIrCheckers, listOf(IrNestedOffsetRangeChecker))` in every `IrValidationPhase` subclass (`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/phaser/IrValidationPhase.kt:58,74,94,113`).

### Claim: `-Xprint-fir` does not exist
- **File**: search result — no occurrences in `kotlin/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/`
- **Notes**: Verified by `grep -rn "Xprint-fir"` returning empty at v2.4.20.

## IR validation phases

### Claim: phase names printed by `-Xlist-phases` are lowering-class simple names
- **File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/phaser/PhaseFactories.kt:72-76`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/phaser/PhaseFactories.kt#L72-L76)
- **Snippet**: `abstract class LoweringPhase<...>(val loweringClass: Class<out Pass>, ...) : NamedCompilerPhase<Context, Input, Input>(loweringClass.simpleName, ...)`

### Claim: `JvmIrValidationAfterLoweringPhase` is the only IR-validation phase registered in the JVM lowering pipeline
- **File**: [`kotlin/compiler/ir/backend.jvm/lower/src/org/jetbrains/kotlin/backend/jvm/lower/irValidation.kt:15-21`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.jvm/lower/src/org/jetbrains/kotlin/backend/jvm/lower/irValidation.kt#L15-L21) (definition)
- **File**: [`kotlin/compiler/ir/backend.jvm/lower/src/org/jetbrains/kotlin/backend/jvm/JvmLoweringPhases.kt:145`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.jvm/lower/src/org/jetbrains/kotlin/backend/jvm/JvmLoweringPhases.kt#L145) — registration site (`::JvmIrValidationAfterLoweringPhase`); `grep -n -i validation` on the file at v2.4.20 returns only this line.
- **Notes**: `JvmK1IrValidationBeforeLoweringPhase` (v2.4.10 `irValidation.kt:16`, registered at `JvmLoweringPhases.kt:21`) was deleted by commit `425b8d40d14f` ("[IR] Remove JvmK1IrValidationBeforeLoweringPhase"); the v2.4.10 → v2.4.20 diff removes the class and the `::JvmK1IrValidationBeforeLoweringPhase` registration line.

### Claim: `KlibIrValidationBeforeLoweringPhase` / `IrValidationAfterLoweringPhase` are the common phases in `backend.common`; the abstract `IrValidationBeforeLoweringPhase` no longer exists
- **File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/phaser/IrValidationPhase.kt:41`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/phaser/IrValidationPhase.kt#L41) (`class KlibIrValidationBeforeLoweringPhase<Context : LoweringContext>(context: Context) : IrValidationPhase<Context>(context)`), [`:109`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/phaser/IrValidationPhase.kt#L109) (`open class IrValidationAfterLoweringPhase`)
- **Notes**: At v2.4.10 `abstract class IrValidationBeforeLoweringPhase` sat at `:40` and `KlibIrValidationBeforeLoweringPhase` extended it; in v2.4.20 the abstract class is gone and `KlibIrValidationBeforeLoweringPhase` extends `IrValidationPhase` directly. `IrValidationPhase.lower` (`:27-38`) now passes `context.diagnosticReporter` (was `context.configuration.messageCollector`) to `validateIr`, and uses `this.javaClass.simpleName` as the phase name.

### Claim: `IrValidator` location and `IrValidationError.Cause` (`IrTreeInconsistency`, `UnboundSymbol`)
- **File**: [`kotlin/compiler/ir/ir.validation/src/org/jetbrains/kotlin/ir/validation/IrValidator.kt:33-52`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.validation/src/org/jetbrains/kotlin/ir/validation/IrValidator.kt#L33-L52)
- **Snippet**:
  ```kotlin
  class IrValidationError(val file: IrFile?, val element: IrElement, val cause: Cause, val message: String, val parentChain: List<IrElement>) {
      interface Cause {
          object IrTreeInconsistency : Cause
          object UnboundSymbol : Cause
      }
  }
  private class IrValidator(...)
  ```

### Claim: IR validation violations are reported through `IrDiagnosticReporter` as `IR_VALIDATION_ERROR` / `IR_VALIDATION_WARNING` since 2.4.20
- **File**: [`kotlin/compiler/ir/ir.validation/src/org/jetbrains/kotlin/ir/validation/IrValidationDiagnostics.kt:15-17`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.validation/src/org/jetbrains/kotlin/ir/validation/IrValidationDiagnostics.kt#L15-L17)
- **Snippet**:
  ```kotlin
  object IrValidationDiagnostics : KtDiagnosticsContainer() {
      val IR_VALIDATION_WARNING by warningWithoutSource()
      val IR_VALIDATION_ERROR by errorWithoutSource()
  ```
- **File**: [`kotlin/compiler/ir/ir.validation/src/org/jetbrains/kotlin/ir/validation/IrValidator.kt:156-159`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.validation/src/org/jetbrains/kotlin/ir/validation/IrValidator.kt#L156-L159) (`enum class IrValidationSeverity(val factory: KtSourcelessDiagnosticFactory)`), [`:167-175`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.validation/src/org/jetbrains/kotlin/ir/validation/IrValidator.kt#L167-L175) (`validateIr(..., diagnosticReporter: IrDiagnosticReporter, getSeverity: (IrValidationError) -> IrValidationSeverity?, ...)`), [`:211-230`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.validation/src/org/jetbrains/kotlin/ir/validation/IrValidator.kt#L211-L230) (`IrDiagnosticReporter.report(error, severity, phaseName, customMessagePrefix)`)
- **Notes**: Commit `0edc97b2e85b` ("[IR] Use diagnostic reporter in IrValidator"). At v2.4.10 the same functions took a `MessageCollector` and a `CompilerMessageSeverity`. Commit `9db629bf7869` also removed the `throw IrValidationException()` from the reporter-based `validateIr` overload (the KDoc at `:164` still mentions the throw — stale upstream comment).

### Claim: rendered message shape — prefix, checker message, element, `inside <parent>` chain; `[IR VALIDATION] <phase>: ` for lowering phases, plugin-blaming prefix for `IrGenerationExtension` runs
- **File**: [`kotlin/compiler/ir/ir.validation/src/org/jetbrains/kotlin/ir/validation/IrValidator.kt:232-250`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.validation/src/org/jetbrains/kotlin/ir/validation/IrValidator.kt#L232-L250) — `IrValidationError.render(phaseName, customMessagePrefix)`
- **File**: [`kotlin/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/pipeline/convertToIr.kt:479-561`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/pipeline/convertToIr.kt#L479-L561) — `runMandatoryIrValidation(extension, module)`: `phaseName = if (extension == null) "FIR2IR" else "Applying IR compiler plugins"`, `customMessagePrefix = "The compiler plugin '${extension.javaClass.name}' ... generated invalid IR. Please report this bug to the plugin vendor."`; called once before plugins (`:280`) and after each `extension.generate(...)` (`:567-572`).
- **File**: [`kotlin/compiler/ir/ir.validation/src/org/jetbrains/kotlin/ir/validation/checkers/CheckTreeConsistencyVisitor.kt:100-105`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.validation/src/org/jetbrains/kotlin/ir/validation/checkers/CheckTreeConsistencyVisitor.kt#L100-L105) — `"Declaration with wrong parent:"` / `expectedParent:` / `actualParent:` wording used in the guide's sample.

## Daemon log path

### Claim: `kotlin.daemon.log.path` is the system property name (defined as `COMPILE_DAEMON_LOG_PATH_PROPERTY` in `Properties.kt`)
- **File**: [`kotlin/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/common/Properties.kt:33`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/common/Properties.kt#L33)
- **Snippet**: `COMPILE_DAEMON_LOG_PATH_PROPERTY("kotlin.daemon.log.path"),`
- **Notes**: guide.md attributes this to `Properties.kt`; the file is `Properties.kt` and the enum is `CompilerSystemProperties`.

## Gradle execution strategy

### Claim: Gradle property `kotlin.compiler.execution.strategy` selects in-process vs daemon
- **File**: [`kotlin/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/PropertiesProvider.kt:451-454`](https://github.com/JetBrains/kotlin/blob/v2.4.20/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/PropertiesProvider.kt#L451-L454)
- **Snippet**:
  ```kotlin
  val kotlinCompilerExecutionStrategy: KotlinCompilerExecutionStrategy
      get() = KotlinCompilerExecutionStrategy.fromProperty(
          this.property("kotlin.compiler.execution.strategy").orNull?.toLowerCaseAsciiOnly()
      )
  ```
- **Notes**: The v2.4.10 → v2.4.20 diff of `PropertiesProvider.kt` only adds unrelated properties (wasm compilation mode, BTAPI toggles for JS/Wasm/metadata, cocoapods, playwright); the property name and accessor are unchanged.

### Claim: Daemon path applies severity-threshold filtering; in-process path does not
- **Daemon path** — [`kotlin/compiler/daemon/src/org/jetbrains/kotlin/daemon/report/CompileServicesFacadeMessageCollector.kt:26-83`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/daemon/src/org/jetbrains/kotlin/daemon/report/CompileServicesFacadeMessageCollector.kt#L26-L83)
- **Snippet** (verified at v2.4.20 — the constructor now takes three parameters; at v2.4.10 it had only the first two, and the collector implemented plain `MessageCollector`):
  ```kotlin
  internal class CompileServicesFacadeMessageCollector(
      private val servicesFacade: CompilerServicesFacadeBase,
      compilationOptions: CompilationOptions,
      private val warningsAsErrors: Boolean,
  ) : MessageCollectorWithDiagnosticId {
      private val mySeverity = compilationOptions.reportSeverity
      override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?, diagnosticId: String?) {
          log.info("Message: " + ...)
          ...
          val reportSeverity = when (severity) {
              CompilerMessageSeverity.WARNING if warningsAsErrors -> ReportSeverity.ERROR
              CompilerMessageSeverity.STRONG_WARNING if warningsAsErrors -> ReportSeverity.ERROR
              CompilerMessageSeverity.ERROR -> ReportSeverity.ERROR
              CompilerMessageSeverity.WARNING, CompilerMessageSeverity.STRONG_WARNING, CompilerMessageSeverity.FIXED_WARNING -> ReportSeverity.WARNING
              CompilerMessageSeverity.INFO -> ReportSeverity.INFO
              CompilerMessageSeverity.LOGGING -> ReportSeverity.DEBUG
          }
          if (reportSeverity.code <= mySeverity) {
              servicesFacade.report(ReportCategory.COMPILER_MESSAGE, reportSeverity, message, attachment)
          }
      }
  }
  ```
  Messages whose `reportSeverity.code > mySeverity` are silently dropped. Severity codes (in `compiler/daemon/daemon-common/src/.../CompilerServicesFacadeBase.kt`): `ERROR=0, WARNING=1, INFO=2, DEBUG=3`. With Gradle's default `reportSeverity = INFO (=2)`, the filter only drops DEBUG (`LOGGING`); WARNINGs and ERRORs surface on both paths. `OUTPUT` and `EXCEPTION` are now handled by dedicated branches (`:46-51`) before the threshold. The MessageCollector visibility difference between paths is therefore restricted to DEBUG-level messages by default — the bigger differences (no debugger / classloader caching) matter more in practice.
- **In-process path** — [`kotlin/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/logging/GradlePrintingMessageCollector.kt:32-57`](https://github.com/JetBrains/kotlin/blob/v2.4.20/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/logging/GradlePrintingMessageCollector.kt#L32-L57)
- **Notes**: `GradlePrintingMessageCollector.report()` maps every severity to a Gradle logger level (error/warn/info/debug) without any threshold check — all messages are rendered and logged. File unchanged between v2.4.10 and v2.4.20.
