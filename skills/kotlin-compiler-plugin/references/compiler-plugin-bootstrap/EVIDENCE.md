# Evidence for guide.md

Source citations against `/Users/kitakkun/Documents/GitHub/kotlin-lang/`. Paths are quoted as `kotlin/<path>:line`.

## CompilerPluginRegistrar abstract members

### Claim: `pluginId: String` is `abstract` (no default).
- **File**: [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt:23`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt#L23)
- **Snippet**:
  ```kotlin
  abstract val pluginId: String
  ```

### Claim: `supportsK2: Boolean` is `abstract`.
- **File**: [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt:60`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt#L60)
- **Snippet**:
  ```kotlin
  abstract val supportsK2: Boolean
  ```

### Claim: `registerExtensions` is an extension function on `ExtensionStorage`.
- **File**: [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt:25`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt#L25)
- **Snippet**:
  ```kotlin
  abstract fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration)
  ```

### Claim: `ExtensionStorage` exposes `registerExtension` as an extension on `ExtensionPointDescriptor<T>`.
- **File**: [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt:39`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt#L39)
- **Snippet**:
  ```kotlin
  fun <T : Any> ExtensionPointDescriptor<T>.registerExtension(extension: T) {
      registeredExtensions.getOrPut(this, ::mutableListOf).add(extension)
  }
  ```

## CommandLineProcessor interface

### Claim: `pluginId`, `pluginOptions`, and `processOption` are members of `CommandLineProcessor`.
- **File**: [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CommandLineProcessor.kt:24-29`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CommandLineProcessor.kt#L24-L29)
- **Snippet**:
  ```kotlin
  interface CommandLineProcessor {
      val pluginId: String
      val pluginOptions: Collection<AbstractCliOption>
      @Throws(CliOptionProcessingException::class)
      fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {}
  ```
  (`processOption` has an empty default body, so plugins without options need not override it.)

## CliOption parameters with defaults

### Claim: `CliOption` constructor has defaults `required = true` and `allowMultipleOccurrences = false`.
- **File**: [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CliOptions.kt:30-36`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CliOptions.kt#L30-L36)
- **Snippet**:
  ```kotlin
  class CliOption(
      override val optionName: String,
      override val valueDescription: String,
      override val description: String,
      override val required: Boolean = true,
      override val allowMultipleOccurrences: Boolean = false
  ) : AbstractCliOption
  ```

## CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY

### Claim: The key exists as `CompilerConfigurationKey<MessageCollector>`, and since 2.4.20 it is gated behind the `@MessageCollectorAccess` opt-in.
- **File**: [`kotlin/compiler/config/gen/org/jetbrains/kotlin/config/CommonConfigurationKeys.kt:99-101`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/config/gen/org/jetbrains/kotlin/config/CommonConfigurationKeys.kt#L99-L101)
- **Snippet**:
  ```kotlin
  @JvmField
  @MessageCollectorAccess
  val MESSAGE_COLLECTOR_KEY = CompilerConfigurationKey.create<MessageCollector>("MESSAGE_COLLECTOR_KEY")
  ```

### Claim: `MessageCollectorAccess` is a `@RequiresOptIn` marker (default level = ERROR), so reading `MESSAGE_COLLECTOR_KEY` or `CompilerConfiguration.messageCollector` without `@OptIn(MessageCollectorAccess::class)` is a compile error on 2.4.20. The annotation does not exist in 2.4.10.
- **File**: [`kotlin/compiler/config/src/org/jetbrains/kotlin/config/MessageCollectorAccess.kt:8-9`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/config/src/org/jetbrains/kotlin/config/MessageCollectorAccess.kt#L8-L9)
- **Snippet**:
  ```kotlin
  @RequiresOptIn("Direct access to the message collector is discouraged. Consider using `CompilerConfiguration.report`.")
  annotation class MessageCollectorAccess
  ```
- **Verification (git)**: `git log --oneline v2.4.10..v2.4.20 -- compiler/config/gen/org/jetbrains/kotlin/config/CommonConfigurationKeys.kt` includes `4dacc99b77f9 [CLI] Add opt-in to CompilerConfiguration.messageCollector` (KT-78277); `git tag --contains 4dacc99b77f9` lists `v2.4.20` but not `v2.4.10`.
- **Suggested alternative named in the opt-in message**: the compiler's own `CompilerConfiguration.report*` helpers live in [`kotlin/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/CliDiagnosticReporting.kt:26-62`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/CliDiagnosticReporting.kt#L26-L62) (`report(factory: KtSourcelessDiagnosticFactory, ...)`, `reportInfo`, `reportLog`, `reportOutput`, `reportException`); `reportInfo`/`reportOutput`/`reportException` are themselves `@OptIn(MessageCollectorAccess::class)` wrappers around `messageCollector.report(...)`. There is no `reportWarning` helper, so a plugin that wants a `w:` line still needs the opt-in.

### Claim: The standard fallback when retrieving it is `MessageCollector.NONE` (the `messageCollector` accessor carries the same opt-in).
- **File**: [`kotlin/compiler/config/gen/org/jetbrains/kotlin/config/CommonConfigurationKeys.kt:233-236`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/config/gen/org/jetbrains/kotlin/config/CommonConfigurationKeys.kt#L233-L236)
- **Snippet**:
  ```kotlin
  @MessageCollectorAccess
  var CompilerConfiguration.messageCollector: MessageCollector
      get() = get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
      set(value) { put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, value) }
  ```
- `MessageCollector.NONE` itself: [`kotlin/compiler/util/src/org/jetbrains/kotlin/cli/common/messages/MessageCollector.kt:17`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/util/src/org/jetbrains/kotlin/cli/common/messages/MessageCollector.kt#L17).

## IrGenerationExtension companion is an ExtensionPointDescriptor

### Claim: `IrGenerationExtension`'s companion object extends `ExtensionPointDescriptor<IrGenerationExtension>`, so `IrGenerationExtension.registerExtension(...)` works directly inside `ExtensionStorage.registerExtensions`.
- **File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGenerationExtension.kt:12-16`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGenerationExtension.kt#L12-L16)
- **Snippet**:
  ```kotlin
  interface IrGenerationExtension {
      companion object : ExtensionPointDescriptor<IrGenerationExtension>(
          name = "org.jetbrains.kotlin.irGenerationExtension",
          extensionClass = IrGenerationExtension::class.java,
      )
  ```

### Claim: `ExtensionPointDescriptor` is the base abstraction; `ProjectExtensionDescriptor` is the IntelliJ-project-bound subclass.
- **File**: [`kotlin/compiler/util/src/org/jetbrains/kotlin/extensions/ProjectExtensionDescriptor.kt:16`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/util/src/org/jetbrains/kotlin/extensions/ProjectExtensionDescriptor.kt#L16)
- **Snippet** (verbatim at v2.4.20, lines 16-18 — `ExtensionPointDescriptor` is `abstract` and exposes both constructor parameters as public `val`s; `ProjectExtensionDescriptor` is the `open` subclass that additionally owns an IntelliJ `ExtensionPointName`):
  ```kotlin
  abstract class ExtensionPointDescriptor<T : Any>(val name: String, val extensionClass: Class<T>)

  open class ProjectExtensionDescriptor<T : Any>(name: String, extensionClass: Class<T>) : ExtensionPointDescriptor<T>(name, extensionClass) {
  ```

## pluginId became abstract in Kotlin 2.3 (KT-55300)

### Claim: Commit `1180951a80f6` "[Plugins] Require a unique pluginId for all compiler plugins" introduced the abstract requirement.
- **Verification (git)**: `git -C kotlin/ log --all --oneline --grep="unique pluginId"` returns:
  ```
  1180951a80f6 [Plugins] Require a unique pluginId for all compiler plugins
  ```
- **Effect on source**: [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt:23`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt#L23) now declares `abstract val pluginId: String` (no default getter).
- **Doc string co-mentions ordering**: same file, lines 19-22, document that `pluginId` "can be used in combination with `-Xcompiler-plugin-order` to control execution order".

## Legacy K1 `ComponentRegistrar` removed in 2.4.20

### Claim: `org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar` (deprecated with `DeprecationLevel.ERROR` since KT-52665) no longer exists in `compiler/plugin-api` at v2.4.20; `CompilerPluginRegistrar` is the only registrar entry point.
- **Verification (git)**: `git diff v2.4.10 v2.4.20 --stat -- compiler/plugin-api` reports `compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/ComponentRegistrar.kt | 40 ----------` (file deleted); `git log --oneline v2.4.10..v2.4.20 -- compiler/plugin-api` includes `ce92663ae659 Drop legacy (K1) ComponentRegistrar and inheritors` (KT-85816) and `7908b1524ad5 Drop usages of deprecated ComponentRegistrar`.
- **Last version of the removed declaration** (v2.4.10): [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/ComponentRegistrar.kt:23-29`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/ComponentRegistrar.kt#L23-L29) — `@Deprecated(level = DeprecationLevel.ERROR) interface ComponentRegistrar`.
- **Side effect visible in real plugins**: allopen's registrar dropped its K1 `DeclarationAttributeAltererExtension.registerExtension(...)` call and now registers only `FirExtensionRegistrar` — [`kotlin/plugins/allopen/allopen.cli/src/org/jetbrains/kotlin/allopen/AllOpenPlugin.kt:46-56`](https://github.com/JetBrains/kotlin/blob/v2.4.20/plugins/allopen/allopen.cli/src/org/jetbrains/kotlin/allopen/AllOpenPlugin.kt#L46-L56).

## CLI argument syntax: -Xplugin and -Xcompiler-plugin

### Claim: `-Xplugin=<path>` (legacy form) is declared in `CommonCompilerArguments`.
- **File**: [`kotlin/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt:893-899`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt#L893-L899)
- **Snippet**:
  ```kotlin
  @Argument(
      value = "-Xplugin",
      valueDescription = "<path>",
      description = "Load plugins from the given classpath.",
  )
  var pluginClasspaths: Array<String> = emptyArray()
  ```

### Claim: `-Xcompiler-plugin=<path1>,<path2>[=<optionName>=<value>,<optionName>=<value>]` (modern form) is declared in `CommonCompilerArguments`.
- **File**: [`kotlin/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt:249-256`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt#L249-L256)
- **Snippet**:
  ```kotlin
  @Argument(
      value = "-Xcompiler-plugin",
      valueDescription = "<path1>,<path2>[=<optionName>=<value>,<optionName>=<value>]",
      description = "Register a compiler plugin.",
      delimiter = Argument.Delimiters.none,
  )
  var pluginConfigurations: Array<String> = emptyArray()
  ```
  (Both properties are non-null `Array<String>` initialized to `emptyArray()`; this was already the case at v2.4.10.)

### Claim: The parsing splits classpath from options on the first `=`, then comma-splits each side.
- **File**: [`kotlin/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/plugins/PluginsOptionsParser.kt:20-36`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/plugins/PluginsOptionsParser.kt#L20-L36)
- **Snippet**:
  ```kotlin
  private const val regularDelimiter = ","
  private const val classpathOptionsDelimiter = "="
  ...
  fun extractPluginClasspathAndOptions(pluginConfiguration: String): PluginClasspathAndOptions {
      val rawClasspath = pluginConfiguration.substringBefore(classpathOptionsDelimiter)
      val rawOptions = pluginConfiguration.substringAfter(classpathOptionsDelimiter, missingDelimiterValue = "")
      val classPath = rawClasspath.split(regularDelimiter)
      val options = rawOptions.takeIf { it.isNotBlank() }?.split(regularDelimiter)?.mapNotNull { parseModernPluginOption(it) } ?: emptyList()
      ...
  }
  ```

### Claim: Legacy `-P plugin:<id>:<key>=<value>` option format is parsed by `parseLegacyPluginOption`.
- **File**: [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CliOptions.kt:78-86`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CliOptions.kt#L78-L86)
- **Snippet**:
  ```kotlin
  fun parseLegacyPluginOption(argumentValue: String): CliOptionValue? {
      val pattern = Pattern.compile("""^plugin:([^:]*):([^=]*)=(.*)$""")
      ...
  }
  ```

## META-INF/services FQNs match package paths

### Claim: The service files in real plugins (e.g. allopen) use the literal interface FQNs as filenames, with one impl FQN per line.
- **File (filename = registrar interface FQN)**: `kotlin/plugins/allopen/allopen.cli/resources/META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar:1`
- **Content**:
  ```
  org.jetbrains.kotlin.allopen.AllOpenComponentRegistrar
  ```
- **File (filename = command-line processor interface FQN)**: `kotlin/plugins/allopen/allopen.cli/resources/META-INF/services/org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor:1`
- **Content**:
  ```
  org.jetbrains.kotlin.allopen.AllOpenCommandLineProcessor
  ```
- **Cross-check**: the listed FQNs match the actual class declarations in [`kotlin/plugins/allopen/allopen.cli/src/org/jetbrains/kotlin/allopen/AllOpenPlugin.kt:23`](https://github.com/JetBrains/kotlin/blob/v2.4.20/plugins/allopen/allopen.cli/src/org/jetbrains/kotlin/allopen/AllOpenPlugin.kt#L23) (`class AllOpenCommandLineProcessor`) and `:46` (`class AllOpenComponentRegistrar`). Package = `org.jetbrains.kotlin.allopen`, source dir mirrors that path under `allopen.cli/src/`.

## Java 25 / IntelliJ JavaVersion parsing

### Claim: The Kotlin CLI / BTAPI compiler reads the host JDK version through IntelliJ's `com.intellij.util.lang.JavaVersion.current()`, which in turn calls the bundled `JavaVersion.parse(...)`. Older IntelliJ Util builds reject "25.0.2".
- **File (representative call site in CLI)**: [`kotlin/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/jvm/modules/javaVersionUtils.kt:8-12`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/jvm/modules/javaVersionUtils.kt#L8-L12)
- **Snippet**:
  ```kotlin
  import com.intellij.util.lang.JavaVersion
  ...
  fun isAtLeastJava9(): Boolean {
      return JavaVersion.current() >= JavaVersion.compose(9)
  }
  ```
- **Other call sites that rely on the same `JavaVersion.current()`**:
  - [`kotlin/compiler/util/src/org/jetbrains/kotlin/utils/KotlinPaths.kt:121-122`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/util/src/org/jetbrains/kotlin/utils/KotlinPaths.kt#L121-L122)
  - [`kotlin/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/services/configuration/JvmEnvironmentConfigurator.kt:150`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/services/configuration/JvmEnvironmentConfigurator.kt#L150)
- **Note**: `JavaVersion` itself ships in IntelliJ Util, not in this repo, so the offending `parse` lives outside `kotlin/`. The failure surfaces when these call sites are reached during a Kotlin 2.3.x BTAPI compile launched on JDK 25 — verified on 2.3.20, 2.3.21, 2.4.0, and 2.4.10 (`gradle/versions.properties` pins `versions.intellijSdk=251.27812.49` in all four tags, so the bundled `JavaVersion` is identical). The v2.4.20 tag pins the same `versions.intellijSdk=251.27812.49`, so nothing changed on the compiler side between 2.4.10 and 2.4.20. Not runtime-verified on 2.4.20: a naive reproduction (the bootstrap example, `jvmToolchain(21)`, default daemon execution strategy, Gradle 9.5.0 launched on Homebrew OpenJDK 25.0.2) builds successfully on *both* 2.4.10 and 2.4.20, so that setup never reaches the failing `JavaVersion.parse` path; the original failure was observed with the Build Tools API compile path and is assumed unchanged because the bundled IntelliJ SDK is.

Path confirmation: `skills/kotlin-compiler-plugin/references/compiler-plugin-bootstrap/EVIDENCE.md`.
