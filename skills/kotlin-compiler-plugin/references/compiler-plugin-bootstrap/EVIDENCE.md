# Evidence for guide.md

Source citations against `/Users/kitakkun/Documents/GitHub/kotlin-lang/`. Paths are quoted as `kotlin/<path>:line`.

## CompilerPluginRegistrar abstract members

### Claim: `pluginId: String` is `abstract` (no default).
- **File**: [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt:23`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt#L23)
- **Snippet**:
  ```kotlin
  abstract val pluginId: String
  ```

### Claim: `supportsK2: Boolean` is `abstract`.
- **File**: [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt:60`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt#L60)
- **Snippet**:
  ```kotlin
  abstract val supportsK2: Boolean
  ```

### Claim: `registerExtensions` is an extension function on `ExtensionStorage`.
- **File**: [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt:28`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt#L28)
- **Snippet**:
  ```kotlin
  abstract fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration)
  ```

### Claim: `ExtensionStorage` exposes `registerExtension` as an extension on `ProjectExtensionDescriptor<T>`.
- **File**: [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt:39`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt#L39)
- **Snippet**:
  ```kotlin
  fun <T : Any> ProjectExtensionDescriptor<T>.registerExtension(extension: T) { ... }
  ```

## CommandLineProcessor interface

### Claim: `pluginId`, `pluginOptions`, and `processOption` are members of `CommandLineProcessor`.
- **File**: [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CommandLineProcessor.kt:24-29`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CommandLineProcessor.kt#L24-L29)
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
- **File**: [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CliOptions.kt:30-36`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CliOptions.kt#L30-L36)
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

### Claim: The key exists as `CompilerConfigurationKey<MessageCollector>`.
- **File**: [`kotlin/compiler/config/gen/org/jetbrains/kotlin/config/CommonConfigurationKeys.kt:102`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/config/gen/org/jetbrains/kotlin/config/CommonConfigurationKeys.kt#L102)
- **Snippet**:
  ```kotlin
  @JvmField
  val MESSAGE_COLLECTOR_KEY: CompilerConfigurationKey<MessageCollector> =
      CompilerConfigurationKey.create("message collector")
  ```

### Claim: The standard fallback when retrieving it is `MessageCollector.NONE`.
- **File**: [`kotlin/compiler/config/gen/org/jetbrains/kotlin/config/CommonConfigurationKeys.kt:238-240`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/config/gen/org/jetbrains/kotlin/config/CommonConfigurationKeys.kt#L238-L240)
- **Snippet**:
  ```kotlin
  var CompilerConfiguration.messageCollector: MessageCollector
      get() = get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
  ```

## IrGenerationExtension companion is an ProjectExtensionDescriptor

### Claim: `IrGenerationExtension`'s companion object extends `ProjectExtensionDescriptor<IrGenerationExtension>`, so `IrGenerationExtension.registerExtension(...)` works directly inside `ExtensionStorage.registerExtensions`.
- **File**: [`kotlin/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGenerationExtension.kt:12-16`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGenerationExtension.kt#L12-L16)
- **Snippet**:
  ```kotlin
  interface IrGenerationExtension {
      companion object : ProjectExtensionDescriptor<IrGenerationExtension>(
          name = "org.jetbrains.kotlin.irGenerationExtension",
          extensionClass = IrGenerationExtension::class.java,
      )
  ```

### Claim: `ProjectExtensionDescriptor` is the base abstraction.
- **File**: [`kotlin/compiler/util/src/org/jetbrains/kotlin/extensions/ProjectExtensionDescriptor.kt:16`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/util/src/org/jetbrains/kotlin/extensions/ProjectExtensionDescriptor.kt#L16)
- **Snippet** (verbatim at v2.4.10 — note `open class` not `abstract class`, and `extensionClass` is `private val`):
  ```kotlin
  open class ProjectExtensionDescriptor<T : Any>(name: String, private val extensionClass: Class<T>)
  ```

## pluginId became abstract in Kotlin 2.3 (KT-55300)

### Claim: Commit `1180951a80f6` "[Plugins] Require a unique pluginId for all compiler plugins" introduced the abstract requirement.
- **Verification (git)**: `git -C kotlin/ log --all --oneline --grep="unique pluginId"` returns:
  ```
  1180951a80f6 [Plugins] Require a unique pluginId for all compiler plugins
  ```
- **Effect on source**: [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt:23`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CompilerPluginRegistrar.kt#L23) now declares `abstract val pluginId: String` (no default getter).
- **Doc string co-mentions ordering**: same file, lines 19-22, document that `pluginId` "can be used in combination with `-Xcompiler-plugin-order` to control execution order".

## CLI argument syntax: -Xplugin and -Xcompiler-plugin

### Claim: `-Xplugin=<path>` (legacy form) is declared in `CommonCompilerArguments`.
- **File**: [`kotlin/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt:750-755`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt#L750-L755)
- **Snippet**:
  ```kotlin
  @Argument(
      value = "-Xplugin",
      valueDescription = "<path>",
      description = "Load plugins from the given classpath.",
  )
  var pluginClasspaths: Array<String>? = null
  ```

### Claim: `-Xcompiler-plugin=<path1>,<path2>[=<optionName>=<value>,<optionName>=<value>]` (modern form) is declared in `CommonCompilerArguments`.
- **File**: [`kotlin/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt:218-224`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/cli/cli-base/gen/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt#L218-L224)
- **Snippet**:
  ```kotlin
  @Argument(
      value = "-Xcompiler-plugin",
      valueDescription = "<path1>,<path2>[=<optionName>=<value>,<optionName>=<value>]",
      description = "Register a compiler plugin.",
      delimiter = Argument.Delimiters.none,
  )
  var pluginConfigurations: Array<String>? = null
  ```

### Claim: The parsing splits classpath from options on the first `=`, then comma-splits each side.
- **File**: [`kotlin/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/plugins/PluginsOptionsParser.kt:20-36`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/plugins/PluginsOptionsParser.kt#L20-L36)
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
- **File**: [`kotlin/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CliOptions.kt:78-86`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/plugin-api/src/org/jetbrains/kotlin/compiler/plugin/CliOptions.kt#L78-L86)
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
- **Cross-check**: the listed FQNs match the actual class declarations in [`kotlin/plugins/allopen/allopen.cli/src/org/jetbrains/kotlin/allopen/AllOpenPlugin.kt:24`](https://github.com/JetBrains/kotlin/blob/v2.4.10/plugins/allopen/allopen.cli/src/org/jetbrains/kotlin/allopen/AllOpenPlugin.kt#L24) (`class AllOpenCommandLineProcessor`) and `:47` (`class AllOpenComponentRegistrar`). Package = `org.jetbrains.kotlin.allopen`, source dir mirrors that path under `allopen.cli/src/`.

## Java 25 / IntelliJ JavaVersion parsing

### Claim: The Kotlin CLI / BTAPI compiler reads the host JDK version through IntelliJ's `com.intellij.util.lang.JavaVersion.current()`, which in turn calls the bundled `JavaVersion.parse(...)`. Older IntelliJ Util builds reject "25.0.2".
- **File (representative call site in CLI)**: [`kotlin/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/jvm/modules/javaVersionUtils.kt:8-12`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/jvm/modules/javaVersionUtils.kt#L8-L12)
- **Snippet**:
  ```kotlin
  import com.intellij.util.lang.JavaVersion
  ...
  fun isAtLeastJava9(): Boolean {
      return JavaVersion.current() >= JavaVersion.compose(9)
  }
  ```
- **Other call sites that rely on the same `JavaVersion.current()`**:
  - [`kotlin/compiler/util/src/org/jetbrains/kotlin/utils/KotlinPaths.kt:117-118`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/util/src/org/jetbrains/kotlin/utils/KotlinPaths.kt#L117-L118)
  - [`kotlin/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/services/configuration/JvmEnvironmentConfigurator.kt:149`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/tests-common-new/testFixtures/org/jetbrains/kotlin/test/services/configuration/JvmEnvironmentConfigurator.kt#L149)
- **Note**: `JavaVersion` itself ships in IntelliJ Util, not in this repo, so the offending `parse` lives outside `kotlin/`. The failure surfaces when these call sites are reached during a Kotlin 2.3.x BTAPI compile launched on JDK 25 — verified on 2.3.20, 2.3.21, 2.4.0, and 2.4.10 (`gradle/versions.properties` pins `versions.intellijSdk=251.27812.49` in all four tags, so the bundled `JavaVersion` is identical).

Path confirmation: `/Users/kitakkun/Documents/GitHub/kotlin-compiler-plugin-skills/skills/compiler-plugin-bootstrap/EVIDENCE.md`.
