# Evidence for SKILL.md

All quoted snippets below originate from the JetBrains/kotlin repository under the **Apache License 2.0** (Copyright 2010-2024 JetBrains s.r.o and respective authors and developers). See [`../../NOTICE.md`](../../NOTICE.md) for the consolidated attribution. Permalinks point to tag `v2.3.21`.

---

## `FirReplSnippetConfiguratorExtension` API surface

### Claim: four abstract methods including the eval-body rewriter
- **File**: [`kotlin/compiler/fir/raw-fir/raw-fir.common/src/org/jetbrains/kotlin/fir/builder/FirReplSnippetConfiguratorExtension.kt:17-44`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/raw-fir/raw-fir.common/src/org/jetbrains/kotlin/fir/builder/FirReplSnippetConfiguratorExtension.kt#L17-L44)
- **Snippet** (full class body):
  ```kotlin
  abstract class FirReplSnippetConfiguratorExtension(
      session: FirSession,
  ) : FirExtension(session) {
      companion object {
          val NAME: FirExtensionPointName = FirExtensionPointName("ReplSnippetConfigurator")
      }

      final override val name: FirExtensionPointName get() = NAME
      final override val extensionType: KClass<out FirExtension> = FirReplSnippetConfiguratorExtension::class

      fun interface Factory : FirExtension.Factory<FirReplSnippetConfiguratorExtension>

      abstract fun isReplSnippetsSource(sourceFile: KtSourceFile?, scriptSource: KtSourceElement): Boolean
      abstract fun FirReplSnippetBuilder.configureContainingFile(fileBuilder: FirFileBuilder)
      abstract fun FirReplSnippetBuilder.configure(sourceFile: KtSourceFile?, context: Context<*>)

      /**
       * Allows mutating the statements of a `FirReplSnippet` `$$eval` function body as needed before
       * it is created. For example, this can be used to turn the last expression of the body into a
       * property to persist the result of the snippet.
       */
      abstract fun MutableList<FirStatement>.configure(
          sourceFile: KtSourceFile?,
          scriptSource: KtSourceElement,
          context: Context<*>,
      )
  }

  val FirExtensionService.replSnippetConfigurators: List<FirReplSnippetConfiguratorExtension>
          by FirExtensionService.registeredExtensions()
  ```

  The KDoc on the fourth method establishes the "promote final expression to a named property" pattern as the canonical use case.

---

## `FirReplSnippetResolveExtension` API surface

### Claim: three abstract methods (default imports, cross-snippet scope, post-resolve hook)
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirReplSnippetResolveExtension.kt:13-37`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirReplSnippetResolveExtension.kt#L13-L37)
- **Snippet**:
  ```kotlin
  abstract class FirReplSnippetResolveExtension(
      session: FirSession,
  ) : FirExtension(session) {
      companion object {
          val NAME: FirExtensionPointName = FirExtensionPointName("ReplSnippetResolveExtension")
      }

      final override val name: FirExtensionPointName get() = NAME
      final override val extensionType: KClass<out FirExtension> = FirReplSnippetResolveExtension::class

      fun interface Factory : FirExtension.Factory<FirReplSnippetResolveExtension>

      abstract fun getSnippetDefaultImports(sourceFile: KtSourceFile, snippet: FirReplSnippet): List<FirImport>?

      abstract fun getSnippetScope(currentSnippet: FirReplSnippet, useSiteSession: FirSession): FirScope?

      abstract fun updateResolved(snippet: FirReplSnippet)
  }

  val FirExtensionService.replSnippetResolveExtensions: List<FirReplSnippetResolveExtension>
          by FirExtensionService.registeredExtensions()
  ```

---

## `FirReplHistoryProvider` API surface

### Claim: abstract `FirSessionComponent` listed in the same file as `FirReplSnippetResolveExtension`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirReplSnippetResolveExtension.kt:41-46`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirReplSnippetResolveExtension.kt#L41-L46)
- **Snippet**:
  ```kotlin
  abstract class FirReplHistoryProvider : FirSessionComponent {
      abstract fun getSnippets(): Iterable<FirReplSnippetSymbol>
      abstract fun putSnippet(symbol: FirReplSnippetSymbol)
      abstract fun isFirstSnippet(symbol: FirReplSnippetSymbol): Boolean
      abstract fun getSnippetCount(): Int
  }
  ```
  Implementing this is the way to feed `getSnippetScope(...)` with the predecessor list.

---

## `Fir2IrReplSnippetConfiguratorExtension` API surface

### Claim: single abstract method `Fir2IrComponents.prepareSnippet(visitor, firReplSnippet, irSnippet)`
- **File**: [`kotlin/compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrReplSnippetConfiguratorExtension.kt:13-31`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrReplSnippetConfiguratorExtension.kt#L13-L31)
- **Snippet**:
  ```kotlin
  abstract class Fir2IrReplSnippetConfiguratorExtension(
      session: FirSession,
  ) : FirExtension(session) {
      companion object {
          val NAME: FirExtensionPointName = FirExtensionPointName("Fir2IrReplStateDeclarationsHandlerExtension")
      }

      final override val name: FirExtensionPointName get() = NAME
      final override val extensionType: KClass<out FirExtension> = Fir2IrReplSnippetConfiguratorExtension::class

      fun interface Factory : FirExtension.Factory<Fir2IrReplSnippetConfiguratorExtension>

      abstract fun Fir2IrComponents.prepareSnippet(
          fir2IrVisitor: Fir2IrVisitor,
          firReplSnippet: FirReplSnippet,
          irSnippet: IrReplSnippet,
      )
  }

  val FirExtensionService.fir2IrReplSnippetConfigurators:
          List<Fir2IrReplSnippetConfiguratorExtension> by FirExtensionService.registeredExtensions()
  ```

  Note the `NAME` is `"Fir2IrReplStateDeclarationsHandlerExtension"` (legacy name; class was renamed but the point name was preserved).

---

## Registration via `FirExtensionRegistrar`

### Claim: All three REPL extensions appear in `AVAILABLE_EXTENSIONS` at v2.3.21
- **File**: [`kotlin/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt:29-50`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt#L29-L50)
- The entries are: `Fir2IrReplSnippetConfiguratorExtension::class` (line 42), `FirReplSnippetConfiguratorExtension::class` (line 43), `FirReplSnippetResolveExtension::class` (line 44).

---

## Reference implementations

The JetBrains scripting plugin's REPL trio lives at `kotlin/plugins/scripting/scripting-compiler/src/.../services/`:

- `FirReplSnippetConfiguratorExtensionImpl.kt` — implements `FirReplSnippetConfiguratorExtension`. The `MutableList<FirStatement>.configure(...)` override is the canonical reference for the "persist last expression as a named val" pattern.
- `FirReplSnippetResolveExtensionImpl.kt` — implements `FirReplSnippetResolveExtension`. `getSnippetScope` reads the registered `FirReplHistoryProvider` to compose a scope over earlier snippets' top-level symbols.
- `Fir2IrReplSnippetConfiguratorExtensionImpl.kt` — implements `Fir2IrReplSnippetConfiguratorExtension`. Coordinates with the runtime evaluator's class layout.

All three are Apache-2.0 licensed; navigate by class name rather than line anchor since these files evolve across patches.
