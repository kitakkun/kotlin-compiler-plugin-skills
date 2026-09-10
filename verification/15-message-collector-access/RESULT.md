# 15-message-collector-access — Verification Result

**Status: PASS**

## How verified

Committed state (opt-in present), clean build:

```
$ ../gradlew --no-daemon clean :sample:compileKotlin
> Task :plugin:compileKotlin
> Task :plugin:jar

> Task :sample:compileKotlin
w: 15-mca: hello from registrar
w: hello from plugin
w: 15-mca: report(COMPILER_PLUGIN_INITIALIZATION_WARNING) from registrar

BUILD SUCCESSFUL in 7s
```

`w: hello from plugin` (reported from `IrGenerationExtension.generate` through the collector obtained in the
registrar) is present, so the PASS criterion holds. Kotlin Gradle plugin 2.4.20 ran the compiler through the Build
Tools API with the **Kotlin compile daemon** strategy (`--debug` log shows `v: trying to start a new compiler daemon`).

### Claim 1 — negative case (opt-in temporarily removed, then restored)

`@OptIn(ExperimentalCompilerApi::class, MessageCollectorAccess::class)` was changed to `@OptIn(ExperimentalCompilerApi::class)`:

```
$ ../gradlew --no-daemon -q clean :plugin:compileKotlin
e: file:///.../plugin/src/main/kotlin/com/example/mca/MessageCollectorAccessComponentRegistrar.kt:34:46 Direct access to the message collector is discouraged. Consider using `CompilerConfiguration.report`.
FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':plugin:compileKotlin' (registered by plugin 'org.jetbrains.kotlin.jvm').
   > Compilation error. See log for more details
```

Line 34:46 is the `configuration.messageCollector` read. The error is emitted by the plugin module's own
compilation (it is an opt-in error at level ERROR), exactly as the guides claim. The opt-in was then restored
and the positive build re-run.

### Claim 3 — the opt-in-free `org.jetbrains.kotlin.cli` helpers

All three resolved and ran from the registrar without `MessageCollectorAccess`. Observed severity prefixes:

| Call in the registrar | Prefix printed | Visible at default Gradle log level? |
|---|---|---|
| `configuration.report(CliDiagnostics.COMPILER_PLUGIN_INITIALIZATION_WARNING, msg)` | `w:` | yes |
| `configuration.reportInfo(msg)` | `i:` | no — only with `--info` |
| `configuration.reportLog(msg)` | `v:` | no — only with `--debug` |

```
$ ../gradlew --no-daemon clean :sample:compileKotlin --info 2>&1 | grep -E '15-mca|hello from plugin'
i: 15-mca: reportInfo from registrar
w: 15-mca: hello from registrar
w: hello from plugin
w: 15-mca: report(COMPILER_PLUGIN_INITIALIZATION_WARNING) from registrar

$ ../gradlew --no-daemon clean :sample:compileKotlin --debug 2>&1 | grep -E '15-mca|hello from plugin'
... [DEBUG] [org.gradle.api.Task] v: 15-mca: reportLog from registrar
... [INFO]  [org.gradle.api.Task] i: 15-mca: reportInfo from registrar
... [WARN]  [org.gradle.api.Task] w: 15-mca: hello from registrar
... [WARN]  [org.gradle.api.Task] w: hello from plugin
... [WARN]  [org.gradle.api.Task] w: 15-mca: report(COMPILER_PLUGIN_INITIALIZATION_WARNING) from registrar
```

Note the ordering: the `report(...)` diagnostic is emitted **after** the IR-time `hello from plugin` even though it
was called first in the registrar. `report(...)` goes through `CompilerConfiguration.diagnosticsCollector`
(`CLIConfigurationKeys.DIAGNOSTICS_COLLECTOR`) and is flushed to the message collector later by the CLI pipeline,
whereas `messageCollector.report(...)` / `reportInfo` / `reportLog` write to the collector immediately.

## Key source snippets

`plugin/src/main/kotlin/com/example/mca/MessageCollectorAccessComponentRegistrar.kt`:

```kotlin
@OptIn(ExperimentalCompilerApi::class, MessageCollectorAccess::class)
class MessageCollectorAccessComponentRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "com.example.mca.message-collector-access"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        configuration.reportInfo("15-mca: reportInfo from registrar")
        configuration.reportLog("15-mca: reportLog from registrar")
        configuration.report(
            CliDiagnostics.COMPILER_PLUGIN_INITIALIZATION_WARNING,
            "15-mca: report(COMPILER_PLUGIN_INITIALIZATION_WARNING) from registrar",
        )

        val messageCollector = configuration.messageCollector
        messageCollector.report(CompilerMessageSeverity.WARNING, "15-mca: hello from registrar")
        IrGenerationExtension.registerExtension(HelloIrGenerationExtension(messageCollector))
    }
}
```

`plugin/src/main/kotlin/com/example/mca/HelloIrGenerationExtension.kt`:

```kotlin
class HelloIrGenerationExtension(private val messageCollector: MessageCollector) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        messageCollector.report(CompilerMessageSeverity.WARNING, "hello from plugin")
    }
}
```

## Skill feedback

1. **Correct**: `compiler-plugin-bootstrap/guide.md` and `compiler-plugin-debugging/guide.md` section 1 are
   accurate on the mechanics — both the key and the `messageCollector` accessor require the opt-in, the level is
   ERROR, the error text matches `MessageCollectorAccess.kt` verbatim, and `@OptIn(MessageCollectorAccess::class)`
   on the registrar class fixes it. `import org.jetbrains.kotlin.config.messageCollector` +
   `import org.jetbrains.kotlin.config.MessageCollectorAccess` are the two imports actually needed for the
   accessor form; the guide only lists the `CommonConfigurationKeys` / `MessageCollector` imports for the
   `configuration.get(KEY, NONE)` form.

2. **Wrong / misleading — "there is no opt-in-free wrapper for WARNING".** Both guides and both CHANGES.md say
   (debugging guide section 1): "there is no opt-in-free wrapper for `WARNING`, so the collector itself remains the
   practical choice for visible plugin output", and (bootstrap guide): "its sibling helpers ... have no WARNING
   variant — so for the 'prove the plugin loaded' `w:` line used in this skill, pulling the collector once in the
   registrar is still the pragmatic choice". That is only true if you restrict yourself to the severity-named
   helpers. `org.jetbrains.kotlin.cli.report(factory, message)` — the very function the opt-in message points at —
   accepts any `KtSourcelessDiagnosticFactory`, and `org.jetbrains.kotlin.cli.CliDiagnostics` ships ready-made ones
   including `COMPILER_PLUGIN_INITIALIZATION_WARNING` (strong warning) and `COMPILER_PLUGIN_INITIALIZATION_ERROR`.
   `configuration.report(CliDiagnostics.COMPILER_PLUGIN_INITIALIZATION_WARNING, "...")` printed a `w:` line with
   **no opt-in** at all. The guides should mention this as the intended replacement for a registrar-time `w:` line,
   with two caveats: (a) it goes through `diagnosticsCollector`, so it is flushed later and appears *after* messages
   written directly to the collector (observed ordering above); (b) it is registrar/CLI-time only — it does not give
   an `IrGenerationExtension` a handle to report with, so the "pass the collector to your extension" pattern still
   needs the opt-in (or `IrPluginContext.messageCollector` / `diagnosticReporter`).

3. **Minor precision — `reportLog` is not "opt-in-free" in the same sense.** The guide says `reportInfo(...)` /
   `reportLog(...)` "wrap the collector for you". True from the caller's side, but the CHANGES.md phrase
   "Opt-in-free `CompilerConfiguration.reportInfo(...)` / `reportLog(...)` exist in `org.jetbrains.kotlin.cli`" hides
   that `org.jetbrains.kotlin.cli.reportLog` is a one-line alias for `org.jetbrains.kotlin.config.reportLog`
   (`compiler/config/src/org/jetbrains/kotlin/config/ReportingUtils.kt`), which is the one a plugin without the
   `cli` package on its classpath would reach for. Not wrong, just a missed pointer.

4. **Contradicted by observation — "DEBUG (3) — `CompilerMessageSeverity.LOGGING` — is dropped on the daemon
   path."** (`compiler-plugin-debugging/guide.md`, section 0, bullet 3.) With KGP 2.4.20's default daemon strategy
   (confirmed via `v: trying to start a new compiler daemon` in the log) and Gradle `--debug`, the
   `configuration.reportLog(...)` line surfaced as `v: 15-mca: reportLog from registrar`. So the daemon path does
   forward LOGGING when Gradle runs at debug log level; the severity threshold evidently follows Gradle's log level
   rather than being a fixed `INFO`. The bullet should be softened to "LOGGING is hidden unless Gradle itself runs
   with `--debug`", which also matches the section-1 sentence "Use `LOGGING` for `--debug`-only messages".

5. **Confirmed**: the guide's severity/prefix table for the collector — `WARNING` -> `w:` at default level,
   `INFO` -> `i:` only with `--info` — matches what was observed for both direct `report(...)` and `reportInfo`.
