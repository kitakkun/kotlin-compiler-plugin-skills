# Evidence for guide.md

All citations against `kotlin-lang/` (the Kotlin compiler source tree).

## `generate*` method signatures

### Claim: `generateTopLevelClassLikeDeclaration(classId)` — experimental, returns `FirClassLikeSymbol<*>?`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:46-47`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L46-L47)
- **Snippet**:
  ```kotlin
  @ExperimentalTopLevelDeclarationsGenerationApi
  open fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? = null
  ```

### Claim: `generateNestedClassLikeDeclaration(owner, name, context)` returns `FirClassLikeSymbol<*>?`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:49-53`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L49-L53)
- **Snippet**:
  ```kotlin
  open fun generateNestedClassLikeDeclaration(
      owner: FirClassSymbol<*>,
      name: Name,
      context: NestedClassGenerationContext
  ): FirClassLikeSymbol<*>? = null
  ```

### Claim: `generateFunctions(callableId, context: MemberGenerationContext?)` returns `List<FirNamedFunctionSymbol>`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:56`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L56)
- **Snippet**:
  ```kotlin
  open fun generateFunctions(callableId: CallableId, context: MemberGenerationContext?): List<FirNamedFunctionSymbol> = emptyList()
  ```

### Claim: `generateFields(callableId, context: MemberGenerationContext?)` exists since 2.4.20, returns `List<FirFieldSymbol>`, gated by `@UnsafePluginApi`, documented as Java-interop only
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:58-62`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L58-L62)
- **Snippet**:
  ```kotlin
  /**
   * Special function designed for Java interop (to generate Java fields) and not useful for general plugins.
   */
  @UnsafePluginApi
  open fun generateFields(callableId: CallableId, context: MemberGenerationContext?): List<FirFieldSymbol> = emptyList()
  ```
- **Note**: added by upstream commit `fc642c5cae9b` ("[Plugin API] Introduce `generateFields` and annotate it with `@UnsafePluginApi`"); absent at v2.4.10.

### Claim: `@UnsafePluginApi` is a `@RequiresOptIn` annotation in `org.jetbrains.kotlin.fir.extensions`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/UnsafePluginApi.kt:6-9`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/UnsafePluginApi.kt#L6-L9)
- **Snippet**:
  ```kotlin
  package org.jetbrains.kotlin.fir.extensions

  @RequiresOptIn("This API is unsafe to use")
  annotation class UnsafePluginApi
  ```

### Claim: `generateProperties(callableId, context: MemberGenerationContext?)` returns `List<FirPropertySymbol>`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:64`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L64)
- **Snippet**:
  ```kotlin
  open fun generateProperties(callableId: CallableId, context: MemberGenerationContext?): List<FirPropertySymbol> = emptyList()
  ```

### Claim: `generateConstructors(context: MemberGenerationContext)` returns `List<FirConstructorSymbol>` (context non-nullable)
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:66`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L66)
- **Snippet**:
  ```kotlin
  open fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> = emptyList()
  ```

## Discovery method signatures

### Claim: `getCallableNamesForClass(classSymbol, context)` returns `Set<Name>`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:80`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L80)
- **Snippet**:
  ```kotlin
  open fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> = emptySet()
  ```

### Claim: `getNestedClassifiersNames(classSymbol, context)` returns `Set<Name>`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:81`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L81)
- **Snippet**:
  ```kotlin
  open fun getNestedClassifiersNames(classSymbol: FirClassSymbol<*>, context: NestedClassGenerationContext): Set<Name> = emptySet()
  ```

### Claim: `getTopLevelCallableIds()` returns `Set<CallableId>` (experimental)
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:83-84`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L83-L84)
- **Snippet**:
  ```kotlin
  @ExperimentalTopLevelDeclarationsGenerationApi
  open fun getTopLevelCallableIds(): Set<CallableId> = emptySet()
  ```

### Claim: `getTopLevelClassIds()` returns `Set<ClassId>` (experimental)
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:86-87`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L86-L87)
- **Snippet**:
  ```kotlin
  @ExperimentalTopLevelDeclarationsGenerationApi
  open fun getTopLevelClassIds(): Set<ClassId> = emptySet()
  ```

### Claim: `hasPackage(packageFqName)` returns `Boolean`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:69`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L69)
- **Snippet**:
  ```kotlin
  open fun hasPackage(packageFqName: FqName): Boolean = false
  ```

### Claim: discovery gates generation — generation only runs when discovery returned the name
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:71-79`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L71-L79)
- **Snippet**:
  ```kotlin
  /*
   * `generate...` methods will be called only if `get...Names/ClassIds/CallableIds` returned corresponding
   *   declaration name
   *
   * If you want to generate constructor for some class, then you need to return `SpecialNames.INIT` in
   *   set of callable names for this class
   */
  ```

## `MemberGenerationContext` / `NestedClassGenerationContext` typealiases

### Claim: typealiases for `DeclarationGenerationContext.Member` and `DeclarationGenerationContext.Nested`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:105-106`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L105-L106)
- **Snippet**:
  ```kotlin
  typealias MemberGenerationContext = DeclarationGenerationContext.Member
  typealias NestedClassGenerationContext = DeclarationGenerationContext.Nested
  ```

### Claim: contexts expose `owner: FirClassSymbol<*>` (and a `declaredScope`)
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:108-111`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L108-L111)
- **Snippet**:
  ```kotlin
  sealed class DeclarationGenerationContext<T : FirContainingNamesAwareScope>(
      val owner: FirClassSymbol<*>,
      val declaredScope: T?,
  ) {
  ```

## `*BuildingContext` helpers in `compiler/fir/plugin-utils/`

### Claim: `createMemberFunction(owner, key, name, returnType, …)` exists
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/SimpleFunctionBuildingContext.kt:131-139`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/SimpleFunctionBuildingContext.kt#L131-L139)
- **Snippet**:
  ```kotlin
  public fun FirExtension.createMemberFunction(
      owner: FirClassSymbol<*>,
      key: GeneratedDeclarationKey,
      name: Name,
      returnType: ConeKotlinType,
      config: SimpleFunctionBuildingContext.() -> Unit = {}
  ): FirNamedFunction { ... }
  ```

### Claim: `createTopLevelFunction(key, callableId, returnType, …)` exists and is gated by experimental opt-in
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/SimpleFunctionBuildingContext.kt:173-182`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/SimpleFunctionBuildingContext.kt#L173-L182)
- **Snippet**:
  ```kotlin
  @ExperimentalTopLevelDeclarationsGenerationApi
  public fun FirExtension.createTopLevelFunction(
      key: GeneratedDeclarationKey,
      callableId: CallableId,
      returnType: ConeKotlinType,
      containingFileName: String? = null,
      config: SimpleFunctionBuildingContext.() -> Unit = {}
  ): FirNamedFunction { ... }
  ```

### Claim: `createMemberProperty(owner, key, name, returnType, isVal, hasBackingField, …)` exists
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/PropertyBuildingContext.kt:173-183`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/PropertyBuildingContext.kt#L173-L183)

### Claim: `createTopLevelProperty(key, callableId, returnType, …)` exists, experimental
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/PropertyBuildingContext.kt:219-230`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/PropertyBuildingContext.kt#L219-L230)

### Claim: `createConstructor(owner, key, isPrimary, generateDelegatedNoArgConstructorCall, …)` exists
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ConstructorBuildingContext.kt:126-142`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ConstructorBuildingContext.kt#L126-L142)
- **Snippet**:
  ```kotlin
  public fun FirExtension.createConstructor(
      owner: FirClassSymbol<*>,
      key: GeneratedDeclarationKey,
      isPrimary: Boolean = false,
      generateDelegatedNoArgConstructorCall: Boolean = false,
      config: ConstructorBuildingContext.() -> Unit = {}
  ): FirConstructor { ... }
  ```

### Claim: with `generateDelegatedNoArgConstructorCall = true`, `createConstructor` populates the delegated call via `tryPopulatingNoArgDelegatingConstructorCall`, which leaves it `null` (no longer throws) when no no-arg super constructor exists
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ConstructorBuildingContext.kt:163-167`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ConstructorBuildingContext.kt#L163-L167)
- **Snippet**:
  ```kotlin
  private fun FirConstructor.tryPopulatingNoArgDelegatingConstructorCall(session: FirSession) {
      val owner = returnTypeRef.coneType.toClassSymbol(session)
      requireNotNull(owner)
      replaceDelegatedConstructor(owner.tryGeneratingNoArgDelegatingConstructorCall(session))
  }
  ```
- **Note**: at v2.4.10 the equivalent helper (`generateNoArgDelegatingConstructorCall`) called `error("No arguments constructor for class ... not found")` / `error("Object ... has more than one class supertypes")` instead of returning `null`. Changed by upstream commits `7024ae7a7c44`, `f089ccb1c1bb`, `432d71d5b709`.

### Claim: public `FirClassSymbol<*>.tryGeneratingNoArgDelegatingConstructorCall(session)` returns `FirDelegatedConstructorCall?` (new in 2.4.20)
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ConstructorBuildingContext.kt:169-188`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ConstructorBuildingContext.kt#L169-L188)
- **Snippet**:
  ```kotlin
  /**
   * Attempts to generate a no-argument delegating constructor call for the given class symbol.
   *
   * It returns `null` if the correct delegated constructor call can't be generated for some reason.
   */
  public fun FirClassSymbol<*>.tryGeneratingNoArgDelegatingConstructorCall(session: FirSession): FirDelegatedConstructorCall? {
      return buildDelegatedConstructorCall {
          val superClassSymbol = getSuperClassSymbolOrAny(session) ?: return null
          constructedTypeRef = superClassSymbol.defaultType().toFirResolvedTypeRef()
          val superConstructorSymbol = superClassSymbol.declaredMemberScope(session, memberRequiredPhase = null)
              .getDeclaredConstructors()
              .firstOrNull { it.valueParameterSymbols.isEmpty() } ?: return null
          ...
  ```
- **Related**: `getSuperClassSymbolOrAny` picks the first `ClassKind.CLASS` supertype, falling back to `kotlin.Any` — [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/resolve/SupertypeUtils.kt:363-369`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/resolve/SupertypeUtils.kt#L363-L369)

### Claim: `createNestedClass(owner, name, key, classKind, …)` exists
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ClassBuildingContext.kt:136-155`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ClassBuildingContext.kt#L136-L155)

### Claim: `createTopLevelClass(classId, key, classKind, …)` exists, experimental
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ClassBuildingContext.kt:113-121`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ClassBuildingContext.kt#L113-L121)
- **Snippet**:
  ```kotlin
  @ExperimentalTopLevelDeclarationsGenerationApi
  public fun FirExtension.createTopLevelClass(
      classId: ClassId,
      key: GeneratedDeclarationKey,
      classKind: ClassKind = ClassKind.CLASS,
      config: ClassBuildingContext.() -> Unit = {}
  ): FirRegularClass { ... }
  ```

### Claim: `createCompanionObject(owner, key, …)` exists; uses `SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT` internally
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ClassBuildingContext.kt:168-188`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ClassBuildingContext.kt#L168-L188)
- **Snippet**:
  ```kotlin
  public fun FirExtension.createCompanionObject(
      owner: FirClassSymbol<*>,
      key: GeneratedDeclarationKey,
      config: ClassBuildingContext.() -> Unit = {}
  ): FirRegularClass {
      val classId = owner.classId.createNestedClassId(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)
      ...
  }
  ```

### Claim: builders set `origin = key.origin` on the produced FIR (e.g. SimpleFunctionBuildingContext)
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/SimpleFunctionBuildingContext.kt:75`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/SimpleFunctionBuildingContext.kt#L75)
- **Snippet**:
  ```kotlin
  origin = key.origin
  ```

## `SpecialNames.INIT`, `SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT`

### Claim: `SpecialNames.INIT = Name.special("<init>")`
- **File**: [`kotlin/core/names/src/org/jetbrains/kotlin/name/SpecialNames.kt:49-50`](https://github.com/JetBrains/kotlin/blob/v2.4.20/core/names/src/org/jetbrains/kotlin/name/SpecialNames.kt#L49-L50)
- **Snippet**:
  ```kotlin
  @JvmField
  val INIT = Name.special("<init>")
  ```

### Claim: `SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT = Name.identifier("Companion")`
- **File**: [`kotlin/core/names/src/org/jetbrains/kotlin/name/SpecialNames.kt:25-26`](https://github.com/JetBrains/kotlin/blob/v2.4.20/core/names/src/org/jetbrains/kotlin/name/SpecialNames.kt#L25-L26)
- **Snippet**:
  ```kotlin
  @JvmField
  val DEFAULT_NAME_FOR_COMPANION_OBJECT = Name.identifier("Companion")
  ```

## `GeneratedDeclarationKey` location

### Claim: `GeneratedDeclarationKey` is an abstract class in package `org.jetbrains.kotlin`; since 2.4.20 its `toString()` defaults to the key's simple class name
- **File**: [`kotlin/core/compiler.common/src/org/jetbrains/kotlin/GeneratedDeclarationKey.kt:6-13`](https://github.com/JetBrains/kotlin/blob/v2.4.20/core/compiler.common/src/org/jetbrains/kotlin/GeneratedDeclarationKey.kt#L6-L13)
- **Snippet**:
  ```kotlin
  package org.jetbrains.kotlin

  abstract class GeneratedDeclarationKey {
      override fun toString(): String {
          // Stabilize the string so the FIR dump is deterministic regardless of object identity.
          return this::class.simpleName!!
      }
  }
  ```
- **Note**: at v2.4.10 the class had no body (`abstract class GeneratedDeclarationKey`), so `toString()` fell back to `Object.toString()` (identity hash) unless overridden. Upstream commit `7726f1f61897` added the override and dropped the now-redundant overrides in the serialization, no-arg, parcelize, and js-plain-objects plugin keys. The `Plugin[$key]` display name of `FirDeclarationOrigin.Plugin` (below) therefore renders as `Plugin[MyKey]` for an un-overridden key.

## `@ExperimentalTopLevelDeclarationsGenerationApi`

### Claim: `@RequiresOptIn` annotation declared in `org.jetbrains.kotlin.fir.extensions`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/ExperimentalTopLevelDeclarationsGenerationApi.kt:6-9`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/ExperimentalTopLevelDeclarationsGenerationApi.kt#L6-L9)
- **Snippet**:
  ```kotlin
  package org.jetbrains.kotlin.fir.extensions

  @RequiresOptIn("This API is experimental and is currently not supported by incremental compilation. Follow https://youtrack.jetbrains.com/issue/KT-66735 for the updates")
  annotation class ExperimentalTopLevelDeclarationsGenerationApi
  ```

## `IrDeclarationOrigin.GeneratedByPlugin(key)` constructor

### Claim: convenience constructor that takes a `GeneratedDeclarationKey` and stores `pluginId = key::class.qualifiedName!!`
- **File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/declarations/IrDeclarationOrigin.kt:133-134`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/declarations/IrDeclarationOrigin.kt#L133-L134)
- **Snippet**:
  ```kotlin
  class GeneratedByPlugin private constructor(val pluginId: String, val pluginKey: GeneratedDeclarationKey?) : IrDeclarationOrigin {
      constructor(pluginKey: GeneratedDeclarationKey) : this(pluginKey::class.qualifiedName!!, pluginKey)
  ```

## `key.origin` → `FirDeclarationOrigin.Plugin` mapping

### Claim: extension property converts a key to `FirDeclarationOrigin.Plugin(this)`
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/declarations/FirDeclarationOrigin.kt:89-90`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/tree/src/org/jetbrains/kotlin/fir/declarations/FirDeclarationOrigin.kt#L89-L90)
- **Snippet**:
  ```kotlin
  val GeneratedDeclarationKey.origin: FirDeclarationOrigin
      get() = FirDeclarationOrigin.Plugin(this)
  ```

### Claim: `FirDeclarationOrigin.Plugin` carries the key in a `val key` field
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/declarations/FirDeclarationOrigin.kt:74`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/tree/src/org/jetbrains/kotlin/fir/declarations/FirDeclarationOrigin.kt#L74)
- **Snippet**:
  ```kotlin
  class Plugin(val key: GeneratedDeclarationKey) : FirDeclarationOrigin(displayName = "Plugin[$key]", generated = true) {
  ```

## "Side-effect-free" KDoc claim

### Claim: KDoc on `FirDeclarationGenerationExtension` mandates `generate*` be side-effect-free; IDE retries computation
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:26-30`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L26-L30)
- **Snippet**:
  ```kotlin
  /**
   * All `generate*` members have the contract that the computation should be side-effect-free.
   * That means that all `generate*` function implementations should not modify any state or leak the generated `FirElement` or `FirBasedSymbol` (e.g., by putting it to some cache).
   * This restriction is imposed by the corresponding IDE cache implementation, which might retry the computation several times.
   */
  ```
