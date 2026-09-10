# Evidence for guide.md

All quoted snippets below originate from the JetBrains/kotlin repository under the **Apache License 2.0** (Copyright 2010-2024 JetBrains s.r.o and respective authors and developers). See [`../../NOTICE.md`](../../NOTICE.md) for the consolidated attribution. Permalinks point to tag `v2.4.0`.

---

## `FirScriptConfiguratorExtension` API surface

### Claim: abstract methods `accepts`, `configureContainingFile`, `configure`
- **File**: [`kotlin/compiler/fir/raw-fir/raw-fir.common/src/org/jetbrains/kotlin/fir/builder/FirScriptConfiguratorExtension.kt:18-46`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/fir/raw-fir/raw-fir.common/src/org/jetbrains/kotlin/fir/builder/FirScriptConfiguratorExtension.kt#L18-L46)
- **Snippet** (full class body):
  ```kotlin
  abstract class FirScriptConfiguratorExtension(
      session: FirSession,
  ) : FirExtension(session) {
      companion object {
          val NAME: FirExtensionPointName = FirExtensionPointName("ScriptConfigurator")
      }

      final override val name: FirExtensionPointName get() = NAME
      final override val extensionType: KClass<out FirExtension> = FirScriptConfiguratorExtension::class

      fun interface Factory : FirExtension.Factory<FirScriptConfiguratorExtension>

      abstract fun accepts(sourceFile: KtSourceFile?, scriptSource: KtSourceElement): Boolean
      abstract fun FirScriptBuilder.configureContainingFile(fileBuilder: FirFileBuilder)
      abstract fun FirScriptBuilder.configure(sourceFile: KtSourceFile?, context: Context<*>)
  }

  val FirExtensionService.scriptConfigurators: List<FirScriptConfiguratorExtension>
          by FirExtensionService.registeredExtensions()
  ```

### Claim: reference impl uses `sourceFile != null` as its `accepts(...)` filter (i.e. it claims every script source)
- **File**: [`kotlin/plugins/scripting/scripting-compiler/src/org/jetbrains/kotlin/scripting/compiler/plugin/services/FirScriptConfigurationExtensionImpl.kt:62-63`](https://github.com/JetBrains/kotlin/blob/v2.4.10/plugins/scripting/scripting-compiler/src/org/jetbrains/kotlin/scripting/compiler/plugin/services/FirScriptConfigurationExtensionImpl.kt#L62-L63)
- **Snippet**:
  ```kotlin
  override fun accepts(sourceFile: KtSourceFile?, scriptSource: KtSourceElement): Boolean =
      sourceFile != null
  ```

---

## `FirScriptResolutionConfigurationExtension` API surface

### Claim: single abstract method `getScriptDefaultImports(script: FirScript): List<FirImport>?`
- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirScriptResolutionConfigurationExtension.kt:11-29`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirScriptResolutionConfigurationExtension.kt#L11-L29)
- **Snippet**:
  ```kotlin
  abstract class FirScriptResolutionConfigurationExtension(
      session: FirSession,
  ) : FirExtension(session) {
      companion object {
          val NAME: FirExtensionPointName = FirExtensionPointName("FirScriptResolutionConfiguration")
      }

      final override val name: FirExtensionPointName get() = NAME
      final override val extensionType: KClass<out FirExtension> = FirScriptResolutionConfigurationExtension::class

      fun interface Factory : FirExtension.Factory<FirScriptResolutionConfigurationExtension>

      abstract fun getScriptDefaultImports(script: FirScript): List<FirImport>?
  }

  val FirExtensionService.firScriptResolutionConfigurators: List<FirScriptResolutionConfigurationExtension>
          by FirExtensionService.registeredExtensions()
  ```

  The return type is `List<FirImport>?` — `null` means "no opinion for this script" and lets other extensions contribute; `emptyList()` is an explicit "I have considered and have nothing to add".

---

## `Fir2IrScriptConfiguratorExtension` API surface

### Claim: single abstract method `IrScript.configure(script: FirScript, getIrScriptByFirSymbol: (FirScriptSymbol) -> IrScriptSymbol?)`
- **File**: [`kotlin/compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrScriptConfiguratorExtension.kt:13-34`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrScriptConfiguratorExtension.kt#L13-L34)
- **Snippet**:
  ```kotlin
  abstract class Fir2IrScriptConfiguratorExtension(
      session: FirSession,
  ) : FirExtension(session) {
      companion object {
          val NAME: FirExtensionPointName = FirExtensionPointName("Fir2IrScriptConversion")
      }

      final override val name: FirExtensionPointName get() = NAME
      final override val extensionType: KClass<out FirExtension> = Fir2IrScriptConfiguratorExtension::class

      fun interface Factory : FirExtension.Factory<Fir2IrScriptConfiguratorExtension>

      abstract fun IrScript.configure(
          script: FirScript,
          getIrScriptByFirSymbol: (FirScriptSymbol) -> IrScriptSymbol?,
      )
  }
  ```

---

## Registration via `FirExtensionRegistrar`

### Claim: All three scripting extensions still appear in `AVAILABLE_EXTENSIONS` at v2.4.10
- **File**: [`kotlin/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt:23-43`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt#L23-L43)
- The three entries are: `FirScriptConfiguratorExtension::class` (line 33), `FirScriptResolutionConfigurationExtension::class` (line 34), `Fir2IrScriptConfiguratorExtension::class` (line 35).

---

## Reference implementations

The JetBrains scripting plugin lives at `kotlin/plugins/scripting/scripting-compiler/`. Its three `*Impl.kt` files mirror this skill's three sections:

- `FirScriptConfigurationExtensionImpl.kt` — implements `FirScriptConfiguratorExtension`; mutates `FirScriptBuilder.parameters`, `receivers`, `declarations` to inject the script's implicit shape from a `ScriptCompilationConfiguration`.
- `FirScriptResolutionConfigurationExtensionImpl.kt` — implements `FirScriptResolutionConfigurationExtension`; reads `defaultImports` from the script's host config and returns them.
- `Fir2IrScriptConfiguratorExtensionImpl.kt` — implements `Fir2IrScriptConfiguratorExtension`; finalises the generated `IrScript`'s name and statement order for the runtime model.

All three are Apache-2.0 licensed; line numbers shift across patches, so navigate by class name rather than line anchor.
