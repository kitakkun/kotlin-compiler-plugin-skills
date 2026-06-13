# Changes affecting this skill

API migrations relevant to bootstrapping a Kotlin compiler plugin. This skill targets the **current stable Kotlin** (2.4.0). If you're upgrading your plugin from an older Kotlin compiler, the entries below cover what changed.

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

Kotlin 2.3.x's BTAPI bundles a Kotlin compiler whose `JavaVersion.parse` cannot parse the string `"25.0.2"`. Verified on 2.3.20 and 2.3.21 (both pin IntelliJ SDK `251.27812.49`, so the bundled `JavaVersion` is identical). Hosts where the system default JDK is Java 25 (e.g. recent Homebrew `openjdk` formula on macOS) hit `IllegalArgumentException: 25.0.2`. Pin `org.gradle.java.home` to JDK 21 in `gradle.properties` until a future Kotlin patch resolves it. See the guide.md "gradle.properties" section.
