---
name: compiler-plugin-debugging
description: Debug Kotlin compiler plugins — see what your plugin actually does during compilation. Covers MessageCollector for visible logging (vs swallowed println), IrDiagnosticReporter for IR-time messages, IR phase dumps via -Xphases-to-dump-after, kotlinc -Xprint-ir output, attaching a debugger to kotlinc / Gradle daemon, and reading IR validator errors. Read compiler-plugin-bootstrap first. NOT a tutorial on the IntelliJ debugger basics.
---

# Compiler Plugin Debugging

The single biggest pain point: **`println` from inside the plugin is silently swallowed by the Kotlin Gradle plugin's Build Tools API worker**. Every other debugging technique builds on having visible output first.

## 0. First: pin Kotlin's compilation strategy to in-process

Before anything else, set this in `gradle.properties` for any project where you're iterating on a compiler plugin:

```properties
kotlin.compiler.execution.strategy=in-process
```

By default, Kotlin compilation runs in a separate daemon process. `in-process` runs the compiler inside the Gradle worker JVM instead. This has three concrete benefits for plugin debugging:

1. **Debugger attachment is simpler** — the compiler runs in the same JVM as Gradle, so a single `-agentlib:jdwp` flag on the Gradle JVM covers the compiler too (see section 7). Under daemon mode you have to attach to the Kotlin compile daemon separately via `kotlin.daemon.jvmargs`.
2. **No classloader caching of the plugin JAR** — the daemon survives across Gradle invocations and pins the first version of your plugin's classes it loads; in-process compilation re-reads the JAR each Gradle build.
3. **`MessageCollector` output for DEBUG-severity messages reliably appears** — the daemon path routes messages through `CompileServicesFacadeMessageCollector`, which applies a severity-threshold filter (`reportSeverity.code <= mySeverity`) where `mySeverity` defaults to `INFO (=2)`. WARNING (1) and ERROR (0) surface on both paths, but DEBUG (3) is dropped on the daemon path. The in-process `GradlePrintingMessageCollector` has no threshold and renders every severity.

Slower for clean builds, but predictable. Recommended as the dev-loop default; switch back to daemon mode for CI / production builds.

## 1. `MessageCollector` (FIR / configuration time)

In your `CompilerPluginRegistrar.registerExtensions(...)` you can pull a `MessageCollector` from the configuration:

```kotlin
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CommonConfigurationKeys

override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val messageCollector = configuration.get(
        CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY,
        MessageCollector.NONE,
    )
    messageCollector.report(CompilerMessageSeverity.WARNING, "MyPlugin: hello from registrar")
    IrGenerationExtension.registerExtension(MyIrGenerationExtension(messageCollector))
}
```

`WARNING` and above appear in Gradle's console as `w: ...` lines. `INFO` is suppressed unless you build with `--info`. `EXCEPTION` halts compilation. Use `LOGGING` for `--debug`-only messages.

Pass the collector to your extension if you want IR-time logging from inside `generate(...)`.

## 2. `IrDiagnosticReporter` (IR time)

For IR-stage diagnostics, prefer `pluginContext.diagnosticReporter` (the modern API) over `MessageCollector`. The factory you pass to `report(...)` must be one you registered via `FirExtensionRegistrar.ExtensionRegistrarContext.registerDiagnosticContainers(...)` — see [`fir-additional-checkers-extension`](../fir-additional-checkers-extension/guide.md) for the registration side.

`IrDiagnosticReporter.at(...)` has four overloads at v2.3.21 — `at(IrDeclaration)`, `at(IrElement, IrFile)`, `at(IrElement, IrDeclaration)`, `at(AbstractKtSourceElement?, IrElement, IrFile)`. **None accepts a bare `IrModuleFragment`**, so the bare-module form does not compile; pass a containing file or a concrete declaration instead:

```kotlin
override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    for (file in moduleFragment.files) {
        file.declarations.filterIsInstance<IrClass>().forEach { irClass ->
            pluginContext.diagnosticReporter
                .at(irClass)                                 // at(IrDeclaration)
                .report(MyDiagnostics.MY_PLUGIN_ERROR)
        }
    }
}
```

For ad-hoc warnings:

```kotlin
@Suppress("DEPRECATION")
pluginContext.messageCollector.report(CompilerMessageSeverity.WARNING, "...")
```

(`IrPluginContext.messageCollector` is `@Deprecated(level = WARNING)` in favour of `diagnosticReporter`, but for one-off prints it's still the easiest. Only `@Suppress("DEPRECATION")` is required — no opt-in annotation needed.)

### Make the diagnostic factory name appear in compile output

By default, the compiler prints only the rendered *message* of a diagnostic — not its factory name (e.g. `MY_PLUGIN_ERROR`). For CI assertions like `grep MY_PLUGIN_ERROR build.log` or for debugging which factory fired, opt the consumer module into name rendering with `-Xrender-internal-diagnostic-names`:

```kotlin
// In the module that consumes your plugin (the "sample" or user code module):
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xrender-internal-diagnostic-names")
}
```

Output then looks like `e: Foo.kt:9:1 [MY_PLUGIN_ERROR] Bad thing happened` instead of just `e: Foo.kt:9:1 Bad thing happened`. Use during development; production builds typically don't need it.

## 3. Write to a file (last resort, always works)

When all else fails:

```kotlin
java.io.File("/tmp/myplugin-trace.log").appendText("visit: ${declaration.name}\n")
```

Crude but bulletproof — file I/O bypasses every layer that might swallow stdout. Use during initial wiring; remove before shipping.

## 4. IR text dump

`-Xprint-ir` is **Kotlin/Native only** — it does not exist on JVM/JS backends. For JVM/JS, use phase dumps (next section).

For Native consumers:

```kotlin
compilerOptions.freeCompilerArgs.add("-Xprint-ir")
```

## 5. Phase-aware IR dumps (the JVM/JS/Native answer)

`-Xphases-to-dump-after=<phase>` and `-Xphases-to-dump-before=<phase>` dump IR around a specific lowering phase:

```kotlin
compilerOptions.freeCompilerArgs.addAll(listOf(
    "-Xphases-to-dump-after=<PhaseClassName>",
))
```

The exact phase names depend on the backend. Run `-Xlist-phases` first to enumerate them. Examples actually seen in the JVM backend (in `kotlin/compiler/ir/backend.jvm/lower/src/.../irValidation.kt`):

- `JvmIrValidationAfterLoweringPhase` — registered JVM K2 phase (verified at `kotlin/compiler/ir/backend.jvm/lower/src/.../irValidation.kt` and `JvmLoweringPhases.kt`)
- `JvmK1IrValidationBeforeLoweringPhase` — registered JVM legacy K1 phase
- `IrValidationBeforeLoweringPhase` / `IrValidationAfterLoweringPhase` — common superclasses defined in `compiler/ir/ir.validation/`

Always start from `-Xlist-phases` to enumerate the phase names registered for your target backend, since the registered set is backend- and Kotlin-version-specific. The names listed above are valid against the JVM backend in current Kotlin (verified against `kotlin/compiler/ir/backend.jvm/lower/src/.../JvmLoweringPhases.kt` and `irValidation.kt`), but earlier Kotlin versions had different names; copy-pasting a phase name from a tutorial without checking `-Xlist-phases` first is the most common reason `-Xphases-to-dump-after=...` silently does nothing.

## 6. `javap` on the resulting bytecode (JVM only)

```bash
./gradlew :sample:compileKotlin
javap -p -c sample/build/classes/kotlin/main/MainKt.class
```

`-p` shows private members; `-c` disassembles methods. Useful for verifying that an IR transformation actually altered bytecode.

## 7. Attach a debugger to the Kotlin compile daemon

Gradle's Kotlin compilation runs in a daemon. To debug:

```bash
./gradlew :sample:compileKotlin --no-daemon \
  -Dorg.gradle.jvmargs="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
```

Then attach IntelliJ's "Remote JVM Debug" run configuration to port 5005. The daemon waits at `suspend=y` until you attach. Set breakpoints in your plugin sources — they'll hit when the relevant code runs.

For Gradle-based integration tests (see [`compiler-plugin-testing`](../compiler-plugin-testing/guide.md)), you can also debug the sample module's compilation by passing the same JVM debug flags to its `compileKotlin` task.

## 8. IR Validator output

If your IR transformation produces malformed IR, you'll see errors from the IR validator. The validator lives at `kotlin/compiler/ir/ir.validation/src/org/jetbrains/kotlin/ir/validation/IrValidator.kt` (its top-level `Cause` enum is just `IrTreeInconsistency` and `UnboundSymbol`); the human-readable messages come from individual `*Checker` classes under `kotlin/compiler/ir/ir.validation/src/.../checkers/`.

Typical-shape error messages (paraphrased — exact wording comes from the checker classes):

```
e: IrValidation: declaration's parent is not set
e: IrValidation: argument is null
e: IrValidation: type mismatch
```

Common causes:
- New declarations without `parent` set
- `arguments[i]` left null
- Return type doesn't match expression type
- `IrConstantValue` referencing a non-existent constructor
- Generic substitution went wrong

## 9. Reproducible minimal repros

When something's wrong, shrink the input:

```bash
mkdir -p /tmp/repro/sample/src/main/kotlin
echo 'fun main() { /* the offending pattern */ }' > /tmp/repro/sample/src/main/kotlin/Main.kt
# Copy your plugin jar
cp plugin/build/libs/plugin.jar /tmp/repro/myplugin.jar
# Run kotlinc directly:
kotlinc /tmp/repro/sample/src/main/kotlin/Main.kt -Xplugin=/tmp/repro/myplugin.jar
```

Bypassing Gradle eliminates noise from build configuration and lets you iterate quickly. (Use the same Kotlin version as your `kotlin-compiler-embeddable` dependency — version mismatches mask real issues.)

## 10. Kotlin compile daemon log

The Kotlin compile daemon log path is controlled by the **JVM system property** `kotlin.daemon.log.path` (defined in `Properties.kt` as `COMPILE_DAEMON_LOG_PATH_PROPERTY`). Pass it via Gradle's daemon JVM args:

```bash
./gradlew :sample:compileKotlin \
  -Dorg.gradle.jvmargs="-Dkotlin.daemon.log.path=/tmp/kotlin-daemon.log"
```

Or via `kotlin.daemon.jvmargs` in `gradle.properties`. The log will contain verbose daemon output — including warnings the BTAPI worker would otherwise filter.

## Common gotchas

### "MessageCollector says NONE but registrar runs"

Reading `CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY` returns `null` if no collector was set; the `.get(key, default)` form returns `MessageCollector.NONE` (a no-op). Calling `report` on it does nothing — silently. If your warnings don't appear, double-check that the collector you got isn't NONE.

### `-Xprint-ir` output is huge

Restrict to a single sample file. Or pipe to a file: `kotlinc Main.kt -Xplugin=... -Xprint-ir > ir.txt`. Then grep for the function or class you care about.

### Debugger attaches but breakpoints don't hit

The compiled plugin JAR your sample uses must be the same one the IDE has source mappings for. `inputs.files(compilerPlugin)` (in the sample's `build.gradle.kts` from [`compiler-plugin-bootstrap`](../compiler-plugin-bootstrap/guide.md)) ensures the JAR rebuilds when the source changes — but if you're running an external `kotlinc`, double-check you're loading the freshly-built JAR.

### `--info` floods the console

True. Pipe to `less` or `grep -i "myplugin"`. Most plugin output uses your registrar's pluginId — grep on it.

### `-Xphases-to-dump-after` doesn't exist for FIR phases

The phase system is IR-only; FIR has its own phase machinery and **no `-X` flag exists to dump FIR text**. To inspect FIR state during compilation, write a `FirAdditionalCheckersExtension` that reports interesting symbols as `WARNING`s. When authoring tests, the official compiler-test infrastructure provides `// FIR_DUMP` to compare against a `.fir.txt` golden — see [`compiler-plugin-testing`](../compiler-plugin-testing/guide.md).

### IR validation errors point to a phase you don't recognise

Many lowering phases run *after* your `IrGenerationExtension`. If validation fails in `JvmIrValidationAfterLoweringPhase` while your transformer ran cleanly, the issue is that your IR was valid initially but a *subsequent* lowering produced something invalid because of unusual inputs you generated. Strip down the test until you isolate the malformed shape.

### Plugin works in tests but not in real builds

Most often: the JAR consumed by the real build differs from the one in tests. Verify with `unzip -l` that the META-INF/services are present, and verify Gradle's `--info` log shows your JAR on the `-Xplugin=` path.

## Relation to other skills

- **Setup that produces visible output by default** → [`compiler-plugin-bootstrap`](../compiler-plugin-bootstrap/guide.md) (the verified end-to-end path uses `MessageCollector.WARNING`)
- **Test-time debugging** → [`compiler-plugin-testing`](../compiler-plugin-testing/guide.md)
- **Lookup APIs you'll log on** → [`ir-plugincontext-usage`](../ir-plugincontext-usage/guide.md)
- **What to inspect when transformations go wrong** → [`ir-call-rewriting`](../ir-call-rewriting/guide.md), [`ir-body-modification`](../ir-body-modification/guide.md)

## What this skill does NOT cover

- IntelliJ Plugin SDK debugging (separate ecosystem)
- Performance profiling of compiler plugins (use JFR, async-profiler — same as any JVM workload)
- Reading the K2 frontend's resolution traces (advanced; involves reading FIR source directly)
