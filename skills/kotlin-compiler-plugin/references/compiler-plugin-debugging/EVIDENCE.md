# Evidence for guide.md

## MessageCollector

### Claim: `MessageCollector` interface with `report(severity, message, location)` and `MessageCollector.NONE` no-op
- **File**: [`kotlin/compiler/util/src/org/jetbrains/kotlin/cli/common/messages/MessageCollector.kt:9-28`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/util/src/org/jetbrains/kotlin/cli/common/messages/MessageCollector.kt#L9-L28)
- **Snippet**:
  ```kotlin
  interface MessageCollector {
      fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation? = null)
      companion object { val NONE: MessageCollector = object : MessageCollector { ... } }
  }
  ```

## IrDiagnosticReporter

### Claim: `IrDiagnosticReporter.at(...).report(factory)` is the modern IR-time API
- **File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/IrDiagnosticReporter.kt:16-50`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/IrDiagnosticReporter.kt#L16-L50)

### Claim: `IrPluginContext.messageCollector` is `@Deprecated(level = WARNING)`
- **File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt:133-137`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrPluginContext.kt#L133-L137)
- **Snippet**:
  ```kotlin
  @Deprecated("Consider using diagnosticReporter instead. ...", level = DeprecationLevel.WARNING)
  val messageCollector: MessageCollector
  ```

## Compiler flags

### Claim: `-Xprint-ir` is Native-only
- **File**: [`kotlin/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/K2NativeCompilerArguments.kt:501-510`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/K2NativeCompilerArguments.kt#L501-L510)
- **Notes**: Defined only in `K2NativeCompilerArguments`; absent from `CommonCompilerArguments` and `K2JVMCompilerArguments`. Verified via grep across the entire `gen/.../arguments/` directory.

### Claim: `-Xphases-to-dump-after`, `-Xphases-to-dump-before`, `-Xlist-phases`
- **File**: [`kotlin/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt:551`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt#L551) (`-Xlist-phases`), `:701` (`-Xphases-to-dump-after`), `:711` (`-Xphases-to-dump-before`)

### Claim: `-Xprint-fir` does not exist
- **File**: search result — no occurrences in `kotlin/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/`
- **Notes**: Verified by `grep -rn "Xprint-fir"` returning empty.

## IR validation phases

### Claim: `JvmIrValidationAfterLoweringPhase`, `JvmK1IrValidationBeforeLoweringPhase`
- **File**: [`kotlin/compiler/ir/backend.jvm/lower/src/org/jetbrains/kotlin/backend/jvm/lower/irValidation.kt:16`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/backend.jvm/lower/src/org/jetbrains/kotlin/backend/jvm/lower/irValidation.kt#L16) (`JvmK1IrValidationBeforeLoweringPhase`), `:28` (`JvmIrValidationAfterLoweringPhase`)
- **File**: [`kotlin/compiler/ir/backend.jvm/lower/src/org/jetbrains/kotlin/backend/jvm/JvmLoweringPhases.kt:21,147`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/backend.jvm/lower/src/org/jetbrains/kotlin/backend/jvm/JvmLoweringPhases.kt#L21) — registration sites

### Claim: `IrValidationBeforeLoweringPhase`, `IrValidationAfterLoweringPhase` are common superclasses
- **File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/phaser/IrValidationPhase.kt:40`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/phaser/IrValidationPhase.kt#L40) (`abstract class IrValidationBeforeLoweringPhase`), `:127` (`open class IrValidationAfterLoweringPhase`)

### Claim: `IrValidator` location and `Cause` enum (`IrTreeInconsistency`, `UnboundSymbol`)
- **File**: [`kotlin/compiler/ir/ir.validation/src/org/jetbrains/kotlin/ir/validation/IrValidator.kt:34-49`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/ir.validation/src/org/jetbrains/kotlin/ir/validation/IrValidator.kt#L34-L49)
- **Snippet**:
  ```kotlin
  class IrValidationError(... val cause: Cause, ...) {
      interface Cause {
          object IrTreeInconsistency : Cause
          object UnboundSymbol : Cause
      }
  }
  private class IrValidator(...)
  ```

## Daemon log path

### Claim: `kotlin.daemon.log.path` is the system property name (defined as `COMPILE_DAEMON_LOG_PATH_PROPERTY` in `Properties.kt`)
- **File**: [`kotlin/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/common/Properties.kt:33`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/common/Properties.kt#L33)
- **Snippet**: `COMPILE_DAEMON_LOG_PATH_PROPERTY("kotlin.daemon.log.path"),`
- **Notes**: guide.md attributes this to `Properties.kt`; the file is `Properties.kt` and the enum is `CompilerSystemProperties`.

## Gradle execution strategy

### Claim: Gradle property `kotlin.compiler.execution.strategy` selects in-process vs daemon
- **File**: [`kotlin/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/PropertiesProvider.kt:424-427`](https://github.com/JetBrains/kotlin/blob/v2.4.10/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/PropertiesProvider.kt#L424-L427)
- **Snippet**:
  ```kotlin
  val kotlinCompilerExecutionStrategy: KotlinCompilerExecutionStrategy
      get() = KotlinCompilerExecutionStrategy.fromProperty(
          this.property("kotlin.compiler.execution.strategy").orNull?.toLowerCaseAsciiOnly()
      )
  ```

### Claim: Daemon path applies severity-threshold filtering; in-process path does not
- **Daemon path** — [`kotlin/compiler/daemon/src/org/jetbrains/kotlin/daemon/report/CompileServicesFacadeMessageCollector.kt:26-67`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/daemon/src/org/jetbrains/kotlin/daemon/report/CompileServicesFacadeMessageCollector.kt#L26-L67)
- **Snippet** (verified at v2.4.10 — note the constructor takes two parameters, not three; earlier drafts of this dossier added a fabricated `warningsAsErrors` param):
  ```kotlin
  internal class CompileServicesFacadeMessageCollector(
          private val servicesFacade: CompilerServicesFacadeBase,
          compilationOptions: CompilationOptions
  ) : MessageCollector {
      private val mySeverity = compilationOptions.reportSeverity
      override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
          log.info("Message: " + ...)
          val reportSeverity = severity.toReportSeverity()
          if (reportSeverity.code <= mySeverity) {
              servicesFacade.report(ReportCategory.COMPILER_MESSAGE, reportSeverity, message, location)
          }
      }
  }
  ```
  Messages whose `reportSeverity.code > mySeverity` are silently dropped. Severity codes (in `compiler/daemon/daemon-common/src/.../CompilerServicesFacadeBase.kt`): `ERROR=0, WARNING=1, INFO=2, DEBUG=3`. With Gradle's default `reportSeverity = INFO (=2)`, the filter only drops DEBUG; WARNINGs and ERRORs surface on both paths. The MessageCollector visibility difference between paths is therefore restricted to DEBUG-level messages by default — the bigger differences (no debugger / classloader caching) matter more in practice.
- **In-process path** — [`kotlin/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/logging/GradlePrintingMessageCollector.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/logging/GradlePrintingMessageCollector.kt)
- **Notes**: `GradlePrintingMessageCollector.report()` maps every severity to a Gradle logger level (error/warn/lifecycle/info) without any threshold check — all messages are rendered and logged.
