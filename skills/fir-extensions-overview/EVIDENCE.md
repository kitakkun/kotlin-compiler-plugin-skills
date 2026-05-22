# Evidence for SKILL.md

Primary-source citations against `/Users/kitakkun/Documents/GitHub/kotlin-lang/`. All paths are given in `kotlin/<path>` form (relative to the repo root).

## `FirExtensionRegistrar.AVAILABLE_EXTENSIONS` — 18 entries

### Claim: "The authoritative list lives at `FirExtensionRegistrar.AVAILABLE_EXTENSIONS` (line 23 of `compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt`); 18 entries total."

- **File**: [`kotlin/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt:29-50`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt#L29-L50)
- **Snippet**:
  ```kotlin
  internal val AVAILABLE_EXTENSIONS = listOf(
      FirStatusTransformerExtension::class,             // 1
      FirDeclarationGenerationExtension::class,         // 2
      FirAdditionalCheckersExtension::class,            // 3
      FirSupertypeGenerationExtension::class,           // 4
      FirTypeAttributeExtension::class,                 // 5
      FirExpressionResolutionExtension::class,          // 6
      FirExtensionSessionComponent::class,              // 7
      FirSamConversionTransformerExtension::class,      // 8
      FirAssignExpressionAltererExtension::class,       // 9
      FirScriptConfiguratorExtension::class,            // 10
      FirScriptResolutionConfigurationExtension::class, // 11
      Fir2IrScriptConfiguratorExtension::class,         // 12
      Fir2IrReplSnippetConfiguratorExtension::class,    // 13
      FirReplSnippetConfiguratorExtension::class,       // 14
      FirReplSnippetResolveExtension::class,            // 15  (added in 2.1 cycle)
      FirFunctionTypeKindExtension::class,              // 16
      @OptIn(FirExtensionApiInternals::class)
      FirMetadataSerializerPlugin::class,               // 17
      @OptIn(FirExtensionApiInternals::class)
      FirFunctionCallRefinementExtension::class,        // 18
  )
  ```

## `FirExtensionRegistrarAdapter` location

### Claim: "`FirExtensionRegistrarAdapter` is the bridge between the plugin-API world (where `ExtensionStorage` lives) and the FIR world."

- **File**: [`kotlin/compiler/frontend.common/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrarAdapter.kt:21-26`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/frontend.common/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrarAdapter.kt#L21-L26)
- **Snippet**:
  ```kotlin
  abstract class FirExtensionRegistrarAdapter {
      companion object : ProjectExtensionDescriptor<FirExtensionRegistrarAdapter>(
          name = "org.jetbrains.kotlin.fir.extensions.firExtensionRegistrar",
          extensionClass = FirExtensionRegistrarAdapter::class.java,
      )
  }
  ```
  Note: lives in `frontend.common` (not `fir/entrypoint`). `FirExtensionRegistrar` extends it (`FirExtensionRegistrar.kt:21`).

## `+::Constructor` DSL via `unaryPlus` overloads

### Claim: "A constructor reference: `+::MyDeclarationGenerator` where the constructor takes a `FirSession`."

- **File**: [`kotlin/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt:60-230`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt#L60-L230)
- **Snippet** (representative pair: factory + reference overload):
  ```kotlin
  // Factory overload (around line 68-71 at v2.3.21)
  @JvmName("plusClassGenerationExtension")
  operator fun (FirDeclarationGenerationExtension.Factory).unaryPlus() {
      registerExtension(FirDeclarationGenerationExtension::class, this)
  }

  // Reference / lambda overload (around line 162-165 at v2.3.21)
  @JvmName("plusClassGenerationExtension")
  operator fun ((FirSession) -> FirDeclarationGenerationExtension).unaryPlus() {
      FirDeclarationGenerationExtension.Factory { this.invoke(it) }.unaryPlus()
  }
  ```
  The reference overload accepts `(FirSession) -> FirExtension`, matching `::MyExtension` constructor references whose primary constructor takes a single `FirSession`.

## `registerDiagnosticContainers(vararg)` signature

### Claim: "The `FirExtensionRegistrar` exposes `registerDiagnosticContainers(...)` for this."

- **File**: [`kotlin/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt:251-253`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt#L251-L253)
- **Snippet**:
  ```kotlin
  fun registerDiagnosticContainers(vararg diagnosticContainers: KtDiagnosticsContainer) {
      this@FirExtensionRegistrar.diagnosticsContainers += diagnosticContainers
  }
  ```

### Claim: "The call is silently a no-op for Library sessions."

- **File**: [`kotlin/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt:341-343`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt#L341-L343)
- **Snippet**:
  ```kotlin
  if (session.kind == FirSession.Kind.Source) {
      session.registeredDiagnosticFactoriesStorage.registerDiagnosticContainers(registeredExtensions.diagnosticsContainers)
  }
  ```

## `@FirExtensionApiInternals` markings

### Claim: "`FirFunctionCallRefinementExtension` and `FirMetadataSerializerPlugin` are gated behind `@FirExtensionApiInternals`."

- **Definition**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirExtension.kt:47-48`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirExtension.kt#L47-L48)
  ```kotlin
  @RequiresOptIn
  annotation class FirExtensionApiInternals
  ```
- **`FirFunctionCallRefinementExtension`**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirFunctionCallRefinementExtension.kt:34-35`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirFunctionCallRefinementExtension.kt#L34-L35)
  ```kotlin
  @FirExtensionApiInternals
  abstract class FirFunctionCallRefinementExtension(session: FirSession) : FirExtension(session) {
  ```
- **`FirMetadataSerializerPlugin`**: [`kotlin/compiler/fir/fir-serialization/src/org/jetbrains/kotlin/fir/serialization/FirMetadataSerializerPlugin.kt:25-26`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/fir-serialization/src/org/jetbrains/kotlin/fir/serialization/FirMetadataSerializerPlugin.kt#L25-L26)
  ```kotlin
  @FirExtensionApiInternals
  abstract class FirMetadataSerializerPlugin(session: FirSession) : FirExtension(session) {
  ```

## `ALLOWED_EXTENSIONS_FOR_LIBRARY_SESSION` list

### Claim: "Only `FirTypeAttributeExtension` and `FirFunctionTypeKindExtension` run in library sessions."

- **File**: [`kotlin/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt:52-55`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt#L52-L55)
- **Snippet**:
  ```kotlin
  internal val ALLOWED_EXTENSIONS_FOR_LIBRARY_SESSION = listOf(
      FirTypeAttributeExtension::class,
      FirFunctionTypeKindExtension::class,
  )
  ```

## FIR extension constructor pattern (single `FirSession`)

### Claim: "Every FIR extension class has a constructor taking exactly one `FirSession`."

- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirExtension.kt:25-32`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirExtension.kt#L25-L32)
- **Snippet**:
  ```kotlin
  abstract class FirExtension(val session: FirSession) {
      abstract val name: FirExtensionPointName
      abstract val extensionType: KClass<out FirExtension>

      fun interface Factory<out P : FirExtension> {
          fun create(session: FirSession): P
      }
  }
  ```
  All concrete bases (e.g. `FirMetadataSerializerPlugin(session: FirSession) : FirExtension(session)`, `FirFunctionCallRefinementExtension(session: FirSession) : FirExtension(session)`) propagate this single-argument constructor.

## `FirExtensionSessionComponent` shape

### Claim: "Every non-trivial plugin uses `FirExtensionSessionComponent` for state sharing."

- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirExtensionSessionComponent.kt:12-29`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirExtensionSessionComponent.kt#L12-L29)
- **Snippet**:
  ```kotlin
  abstract class FirExtensionSessionComponent(session: FirSession) : FirExtension(session), FirSessionComponent {
      companion object {
          val NAME: FirExtensionPointName = FirExtensionPointName("ExtensionSessionComponent")
      }

      final override val name: FirExtensionPointName get() = NAME
      final override val extensionType: KClass<out FirExtension> get() = FirExtensionSessionComponent::class

      open val componentClass: KClass<out FirExtensionSessionComponent>
          get() = this::class

      fun interface Factory : FirExtension.Factory<FirExtensionSessionComponent>
  }

  val FirExtensionService.extensionSessionComponents: List<FirExtensionSessionComponent> by FirExtensionService.registeredExtensions()
  ```

## `FirSession.sessionComponentAccessor()` shape

### Claim: "The `by FirSession.sessionComponentAccessor()` pattern at file top level is canonical."

- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/FirSession.kt:19-22`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/tree/src/org/jetbrains/kotlin/fir/FirSession.kt#L19-L22)
- **Snippet**:
  ```kotlin
  companion object : ConeTypeRegistry<FirSessionComponent, FirSessionComponent>() {
      inline fun <reified T : FirSessionComponent> sessionComponentAccessor(): ArrayMapAccessor<FirSessionComponent, FirSessionComponent, T> {
          return generateAccessor(T::class)
      }
      // ... with-default and id-based variants follow at lines 25-37
  }
  ```
  Real-world usage example — [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirRegisteredPluginAnnotations.kt:148`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirRegisteredPluginAnnotations.kt#L148):
  ```kotlin
  val FirSession.registeredPluginAnnotations: FirRegisteredPluginAnnotations by FirSession.sessionComponentAccessor()
  ```

## Predicates aggregation: `FirRegisteredPluginAnnotations.initialize()` iterates all extensions

### Claim: "Predicates registered by *any* extension share a session-wide index" / "the pipeline collects matching declarations through `FirPredicateBasedProvider`."

- **File**: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirRegisteredPluginAnnotations.kt:97-120`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/extensions/FirRegisteredPluginAnnotations.kt#L97-L120)
- **Snippet**:
  ```kotlin
  @PluginServicesInitialization
  final override fun initialize() {
      val registrar = object : FirDeclarationPredicateRegistrar() {
          val predicates = mutableListOf<AbstractPredicate<*>>()
          override fun register(vararg predicates: AbstractPredicate<*>) {
              this.predicates += predicates
          }
          override fun register(predicates: Collection<AbstractPredicate<*>>) {
              this.predicates += predicates
          }
      }

      for (extension in session.extensionService.getAllExtensions()) {
          with(extension) {
              registrar.registerPredicates()
          }
      }

      for (predicate in registrar.predicates) {
          saveAnnotationsFromPlugin(predicate.annotations)
          metaAnnotations += predicate.metaAnnotations
      }
  }
  ```
  Called from the session bootstrap at [`kotlin/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt:340`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/entrypoint/src/org/jetbrains/kotlin/fir/extensions/FirExtensionRegistrar.kt#L340):
  ```kotlin
  session.registeredPluginAnnotations.initialize()
  ```
  This call sits inside `FirExtensionService.registerExtensions(...)` (line 316), so every session aggregates predicates across **all** registered extensions into one shared annotation set before any extension runs.
