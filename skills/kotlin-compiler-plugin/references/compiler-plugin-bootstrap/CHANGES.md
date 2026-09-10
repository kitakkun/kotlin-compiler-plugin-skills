# Changes affecting this skill

API migrations relevant to bootstrapping a Kotlin compiler plugin. This skill targets the **current stable Kotlin** (2.4.20). If you're upgrading your plugin from an older Kotlin compiler, the entries below cover what changed.

## Kotlin 2.4.10 → 2.4.20

### `MESSAGE_COLLECTOR_KEY` / `CompilerConfiguration.messageCollector` now require `@OptIn(MessageCollectorAccess::class)`

Kotlin 2.4.20 introduced `org.jetbrains.kotlin.config.MessageCollectorAccess`, a `@RequiresOptIn` marker (default level ERROR), and put it on both `CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY` and the `CompilerConfiguration.messageCollector` extension property (commit `4dacc99b77f9`, KT-78277). The opt-in message reads: "Direct access to the message collector is discouraged. Consider using `CompilerConfiguration.report`."

Before (2.4.10 — compiles as is):

```kotlin
@OptIn(ExperimentalCompilerApi::class)
class MyComponentRegistrar : CompilerPluginRegistrar() {
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val messageCollector = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        // ...
    }
}
```

After (2.4.20 — the same code fails with `OPT_IN_USAGE_ERROR` unless you opt in):

```kotlin
import org.jetbrains.kotlin.config.MessageCollectorAccess

@OptIn(ExperimentalCompilerApi::class, MessageCollectorAccess::class)
class MyComponentRegistrar : CompilerPluginRegistrar() {
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val messageCollector = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        // ...
    }
}
```

**Migration**: add `MessageCollectorAccess::class` to the `@OptIn` on every declaration that touches `MESSAGE_COLLECTOR_KEY` or `configuration.messageCollector`, or add `-opt-in=org.jetbrains.kotlin.config.MessageCollectorAccess` to the plugin module's `freeCompilerArgs`. The alternative the message points at, `org.jetbrains.kotlin.cli.report(factory: KtSourcelessDiagnosticFactory, message, location)`, routes through the diagnostics collector and needs a diagnostic factory rather than a `CompilerMessageSeverity` — and `org.jetbrains.kotlin.cli.CliDiagnostics` already provides one: `configuration.report(CliDiagnostics.COMPILER_PLUGIN_INITIALIZATION_WARNING, "...")` prints a `w:` line with no opt-in (`CliDiagnostics.kt:29`). Caveats: it is flushed later than direct `messageCollector.report(...)` calls (so it appears after them in the console), and it is registrar-time only — an `IrGenerationExtension` still needs a collector handle (opt-in) or `IrPluginContext.diagnosticReporter`. The sibling `reportInfo` / `reportLog` / `reportOutput` / `reportException` helpers exist but there is no `reportWarning`; `org.jetbrains.kotlin.cli.reportLog` merely aliases `org.jetbrains.kotlin.config.reportLog` (`compiler/config/src/org/jetbrains/kotlin/config/ReportingUtils.kt`). The accessor form `configuration.messageCollector` needs `import org.jetbrains.kotlin.config.messageCollector`. The annotation class is absent in 2.4.10 and earlier, so a plugin that compiles against both must split the registrar into per-version source sets.

Upstream: [`compiler/config/src/org/jetbrains/kotlin/config/MessageCollectorAccess.kt:8-9`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/config/src/org/jetbrains/kotlin/config/MessageCollectorAccess.kt#L8-L9), [`compiler/config/gen/org/jetbrains/kotlin/config/CommonConfigurationKeys.kt:99-101`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/config/gen/org/jetbrains/kotlin/config/CommonConfigurationKeys.kt#L99-L101) and [`:233-236`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/config/gen/org/jetbrains/kotlin/config/CommonConfigurationKeys.kt#L233-L236), [`compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/CliDiagnosticReporting.kt:26-62`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/CliDiagnosticReporting.kt#L26-L62).

### Legacy K1 `ComponentRegistrar` deleted

`org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar` (deprecated at `DeprecationLevel.ERROR` since KT-52665) was removed from `compiler/plugin-api` together with its `META-INF/services` inheritors (commits `ce92663ae659` "Drop legacy (K1) ComponentRegistrar and inheritors", KT-85816, and `7908b1524ad5`). Any plugin still shipping a `META-INF/services/org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar` file or compiling a `ComponentRegistrar` implementation (even under `@Suppress("DEPRECATION_ERROR")`) will fail to compile against `kotlin-compiler-embeddable:2.4.20`.

**Migration**: implement `CompilerPluginRegistrar` (the only entry point this skill documents) and register it under `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar`; delete the old service file. Upstream diff: `git diff v2.4.10 v2.4.20 --stat -- compiler/plugin-api` shows `ComponentRegistrar.kt | 40 ----------`. Last surviving declaration: [`compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/ComponentRegistrar.kt:23-29` at v2.4.10](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/ComponentRegistrar.kt#L23-L29).

### Unchanged

- `CompilerPluginRegistrar` (`pluginId` / `supportsK2` / `ExtensionStorage.registerExtensions`), `CommandLineProcessor` (`pluginId` / `pluginOptions` / `processOption`), `CliOption`, and the `-Xplugin` / `-Xcompiler-plugin` / `-P plugin:` CLI syntax are unchanged between 2.4.10 and 2.4.20 (only line numbers shifted; `CommandLineProcessor.applyOptionsFrom` switched to the new `[key, values]` destructuring syntax internally).
- **JDK 25 / BTAPI workaround still required.** `versions.intellijSdk=251.27812.49` is unchanged in 2.4.20, so keep the `org.gradle.java.home` → JDK 21 pin shown in guide.md.

## Kotlin 2.3 → 2.4

- **No public `CompilerPluginRegistrar` API change.** `pluginId` / `supportsK2` / `ExtensionStorage.registerExtensions` are unchanged; only line numbers in the source shifted.
- **Compiler source-tree moves (navigation only — not API):** the `compiler/cli/cli-common/` module was renamed to `compiler/cli/cli-base/` (so `CommonCompilerArguments.kt`, `Properties.kt`, `PluginsOptionsParser.kt` moved), and `core/compiler.common/.../name/SpecialNames.kt` moved to `core/names/.../name/SpecialNames.kt`. EVIDENCE.md links were re-pinned accordingly.
- **JDK 25 / BTAPI workaround still required.** The bundled IntelliJ `JavaVersion` that can't parse `"25.0.2"` is unchanged in 2.4.0 (`versions.intellijSdk=251.27812.49`, identical to 2.3.21), so keep the `org.gradle.java.home` → JDK 21 pin shown in guide.md.

## Kotlin 2.2 → 2.3

### `CompilerPluginRegistrar.pluginId` is now abstract

Pre-2.3, the base class declared (a getter with empty default, with a TODO):

```kotlin
abstract class CompilerPluginRegistrar {
    open val pluginId: String
        get() = ""  // TODO(KT-55300): make abstract
    // ...
}
```

In 2.3, `pluginId` became `abstract val pluginId: String` with no default (commit `1180951a80f6`, KT-55300, on `origin/2.3.0` and later).

**Migration**: Add to every `CompilerPluginRegistrar` subclass:

```kotlin
override val pluginId: String = "your.plugin.id"
```

Failing to do so produces:

```
Class 'X' is not abstract and does not implement abstract member 'public abstract val pluginId: String defined in ...'
```

### `CommandLineProcessor.pluginId` was always abstract

For completeness: `CommandLineProcessor.pluginId` has been abstract since 2014 (commit `d961e2f5b45b`). Nothing changed there. The 2.3 change only affects `CompilerPluginRegistrar`.

## Tooling-side considerations

### Java 25 + Gradle's bundled Kotlin compiler

Kotlin's BTAPI bundles a Kotlin compiler whose `JavaVersion.parse` cannot parse the string `"25.0.2"`. Verified on 2.3.20, 2.3.21, 2.4.0, and 2.4.10; 2.4.20 pins the same IntelliJ SDK `251.27812.49`, so the bundled `JavaVersion` is identical across all of them. Hosts where the system default JDK is Java 25 (e.g. recent Homebrew `openjdk` formula on macOS) hit `IllegalArgumentException: 25.0.2`. Pin `org.gradle.java.home` to JDK 21 in `gradle.properties` until a Kotlin release bumps the bundled IntelliJ SDK. See the guide.md "gradle.properties" section.
