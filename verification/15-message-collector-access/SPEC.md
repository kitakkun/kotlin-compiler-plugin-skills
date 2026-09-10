# Verification 15 — `MessageCollectorAccess` opt-in and `CompilerConfiguration.report*` helpers (Kotlin 2.4.20)

## Goal

Verify the three claims the skill makes about plugin-side message reporting since Kotlin 2.4.20
(`references/compiler-plugin-bootstrap/guide.md` "`@MessageCollectorAccess` opt-in" paragraph,
`references/compiler-plugin-debugging/guide.md` section 1, and both `CHANGES.md` files):

1. Reading `CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY` or the `CompilerConfiguration.messageCollector`
   accessor inside `CompilerPluginRegistrar.registerExtensions` requires
   `@OptIn(org.jetbrains.kotlin.config.MessageCollectorAccess::class)`. Without it the **plugin module**
   fails to compile with
   `Direct access to the message collector is discouraged. Consider using `CompilerConfiguration.report`.`
2. With the opt-in, a `MessageCollector` pulled in the registrar and handed to an `IrGenerationExtension`
   can `report(CompilerMessageSeverity.WARNING, "hello from plugin")`, and that line shows up in the
   sample's `:sample:compileKotlin` Gradle output as `w: hello from plugin`.
3. The opt-in-free helpers in `org.jetbrains.kotlin.cli` (`CompilerConfiguration.report(factory, message)`,
   `reportInfo`, `reportLog`) also work from the registrar. Record which severity / prefix each prints with.

## Project layout

- `plugin/` — the compiler plugin.
  - `MessageCollectorAccessComponentRegistrar` (`CompilerPluginRegistrar`, `supportsK2 = true`), annotated
    `@OptIn(ExperimentalCompilerApi::class, MessageCollectorAccess::class)`. In `registerExtensions` it:
    - calls `configuration.reportInfo(...)`, `configuration.reportLog(...)` and
      `configuration.report(CliDiagnostics.COMPILER_PLUGIN_INITIALIZATION_WARNING, ...)` (claim 3, no opt-in needed);
    - reads `configuration.messageCollector` (claim 1), reports one WARNING directly, and passes the collector to
      `HelloIrGenerationExtension`.
  - `HelloIrGenerationExtension` (`IrGenerationExtension`) reports `WARNING "hello from plugin"` in `generate` (claim 2).
- `sample/` — a compile-only module (no `application` plugin) with one trivial `Main.kt`. Loads the plugin via the
  `compilerPlugin` configuration + `-Xplugin=` exactly as probe 12 does.

## PASS criterion

`../gradlew --no-daemon clean :sample:compileKotlin` succeeds and its output contains `w: hello from plugin`.

Additionally (claim 1, negative case, not part of the committed state): temporarily removing
`MessageCollectorAccess::class` from the registrar's `@OptIn` makes `:plugin:compileKotlin` fail with the
`Direct access to the message collector is discouraged...` error. The opt-in is restored afterwards.

## FAIL criterion

- The plugin module compiles without the opt-in (claim 1 false), or fails with the opt-in present, or
- `:sample:compileKotlin` succeeds but no `w: hello from plugin` line is printed (claim 2 false), or
- any of the `org.jetbrains.kotlin.cli.report*` helpers does not resolve / throws at registrar time (claim 3 false).

## Skills consulted

- `references/compiler-plugin-bootstrap/guide.md` (registrar template + "`@MessageCollectorAccess` opt-in" paragraph)
- `references/compiler-plugin-debugging/guide.md` (section 1 "MessageCollector", section 0 on daemon vs in-process severities)
- `references/compiler-plugin-bootstrap/CHANGES.md`, `references/compiler-plugin-debugging/CHANGES.md` (2.4.20 entries)

## Reference patterns from kotlin v2.4.20

- `compiler/config/src/org/jetbrains/kotlin/config/MessageCollectorAccess.kt` — the `@RequiresOptIn` marker and its message.
- `compiler/config/gen/org/jetbrains/kotlin/config/CommonConfigurationKeys.kt:99-101, 233-236` — the annotated key and accessor.
- `compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/CliDiagnosticReporting.kt` — `report` / `reportInfo` / `reportLog` / `reportOutput` / `reportException`.
- `compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/CliDiagnostics.kt` — `KtSourcelessDiagnosticFactory` instances usable with `report(...)`, including `COMPILER_PLUGIN_INITIALIZATION_WARNING`.
- `compiler/config/src/org/jetbrains/kotlin/config/ReportingUtils.kt` — the `reportLog` that `org.jetbrains.kotlin.cli.reportLog` delegates to.
