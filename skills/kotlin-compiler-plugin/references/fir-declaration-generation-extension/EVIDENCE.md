# Evidence for guide.md

All citations against `kotlin-lang/` (the Kotlin compiler source tree).

## `generate*` method signatures

### Claim: `generateTopLevelClassLikeDeclaration(classId)` — experimental, returns `FirClassLikeSymbol<*>?`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:46-47`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L46-L47)
- **Snippet**:
  ```kotlin
  @ExperimentalTopLevelDeclarationsGenerationApi
  open fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? = null
  ```

### Claim: `generateNestedClassLikeDeclaration(owner, name, context)` returns `FirClassLikeSymbol<*>?`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:49-53`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L49-L53)
- **Snippet**:
  ```kotlin
  open fun generateNestedClassLikeDeclaration(
      owner: FirClassSymbol<*>,
      name: Name,
      context: NestedClassGenerationContext
  ): FirClassLikeSymbol<*>? = null
  ```

### Claim: `generateFunctions(callableId, context: MemberGenerationContext?)` returns `List<FirNamedFunctionSymbol>`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:56`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L56)
- **Snippet**:
  ```kotlin
  open fun generateFunctions(callableId: CallableId, context: MemberGenerationContext?): List<FirNamedFunctionSymbol> = emptyList()
  ```

### Claim: `generateProperties(callableId, context: MemberGenerationContext?)` returns `List<FirPropertySymbol>`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:57`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L57)
- **Snippet**:
  ```kotlin
  open fun generateProperties(callableId: CallableId, context: MemberGenerationContext?): List<FirPropertySymbol> = emptyList()
  ```

### Claim: `generateConstructors(context: MemberGenerationContext)` returns `List<FirConstructorSymbol>` (context non-nullable)
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:58`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L58)
- **Snippet**:
  ```kotlin
  open fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> = emptyList()
  ```

## Discovery method signatures

### Claim: `getCallableNamesForClass(classSymbol, context)` returns `Set<Name>`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:72`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L72)
- **Snippet**:
  ```kotlin
  open fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> = emptySet()
  ```

### Claim: `getNestedClassifiersNames(classSymbol, context)` returns `Set<Name>`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:73`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L73)
- **Snippet**:
  ```kotlin
  open fun getNestedClassifiersNames(classSymbol: FirClassSymbol<*>, context: NestedClassGenerationContext): Set<Name> = emptySet()
  ```

### Claim: `getTopLevelCallableIds()` returns `Set<CallableId>` (experimental)
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:75-76`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L75-L76)
- **Snippet**:
  ```kotlin
  @ExperimentalTopLevelDeclarationsGenerationApi
  open fun getTopLevelCallableIds(): Set<CallableId> = emptySet()
  ```

### Claim: `getTopLevelClassIds()` returns `Set<ClassId>` (experimental)
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:78-79`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L78-L79)
- **Snippet**:
  ```kotlin
  @ExperimentalTopLevelDeclarationsGenerationApi
  open fun getTopLevelClassIds(): Set<ClassId> = emptySet()
  ```

### Claim: `hasPackage(packageFqName)` returns `Boolean`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:61`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L61)
- **Snippet**:
  ```kotlin
  open fun hasPackage(packageFqName: FqName): Boolean = false
  ```

### Claim: discovery gates generation — generation only runs when discovery returned the name
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:63-71`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L63-L71)
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
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:97-98`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L97-L98)
- **Snippet**:
  ```kotlin
  typealias MemberGenerationContext = DeclarationGenerationContext.Member
  typealias NestedClassGenerationContext = DeclarationGenerationContext.Nested
  ```

### Claim: contexts expose `owner: FirClassSymbol<*>` (and a `declaredScope`)
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:100-103`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L100-L103)
- **Snippet**:
  ```kotlin
  sealed class DeclarationGenerationContext<T : FirContainingNamesAwareScope>(
      val owner: FirClassSymbol<*>,
      val declaredScope: T?,
  ) {
  ```

## `*BuildingContext` helpers in `compiler/fir/plugin-utils/`

### Claim: `createMemberFunction(owner, key, name, returnType, …)` exists
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/SimpleFunctionBuildingContext.kt:131-139`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/SimpleFunctionBuildingContext.kt#L131-L139)
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
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/SimpleFunctionBuildingContext.kt:173-182`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/SimpleFunctionBuildingContext.kt#L173-L182)
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
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/PropertyBuildingContext.kt:173-183`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/PropertyBuildingContext.kt#L173-L183)

### Claim: `createTopLevelProperty(key, callableId, returnType, …)` exists, experimental
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/PropertyBuildingContext.kt:219-230`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/PropertyBuildingContext.kt#L219-L230)

### Claim: `createConstructor(owner, key, isPrimary, generateDelegatedNoArgConstructorCall, …)` exists
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ConstructorBuildingContext.kt:125-141`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ConstructorBuildingContext.kt#L125-L141)
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

### Claim: `createNestedClass(owner, name, key, classKind, …)` exists
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ClassBuildingContext.kt:136-155`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ClassBuildingContext.kt#L136-L155)

### Claim: `createTopLevelClass(classId, key, classKind, …)` exists, experimental
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ClassBuildingContext.kt:113-121`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ClassBuildingContext.kt#L113-L121)
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
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ClassBuildingContext.kt:168-188`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ClassBuildingContext.kt#L168-L188)
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
- **File**: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/SimpleFunctionBuildingContext.kt:75`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/SimpleFunctionBuildingContext.kt#L75)
- **Snippet**:
  ```kotlin
  origin = key.origin
  ```

## `SpecialNames.INIT`, `SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT`

### Claim: `SpecialNames.INIT = Name.special("<init>")`
- **File**: [`kotlin/core/names/src/org/jetbrains/kotlin/name/SpecialNames.kt:49-50`](https://github.com/JetBrains/kotlin/blob/v2.4.0/core/names/src/org/jetbrains/kotlin/name/SpecialNames.kt#L49-L50)
- **Snippet**:
  ```kotlin
  @JvmField
  val INIT = Name.special("<init>")
  ```

### Claim: `SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT = Name.identifier("Companion")`
- **File**: [`kotlin/core/names/src/org/jetbrains/kotlin/name/SpecialNames.kt:25-26`](https://github.com/JetBrains/kotlin/blob/v2.4.0/core/names/src/org/jetbrains/kotlin/name/SpecialNames.kt#L25-L26)
- **Snippet**:
  ```kotlin
  @JvmField
  val DEFAULT_NAME_FOR_COMPANION_OBJECT = Name.identifier("Companion")
  ```

## `GeneratedDeclarationKey` location

### Claim: `GeneratedDeclarationKey` is an abstract class in package `org.jetbrains.kotlin`
- **File**: [`kotlin/core/compiler.common/src/org/jetbrains/kotlin/GeneratedDeclarationKey.kt:6-8`](https://github.com/JetBrains/kotlin/blob/v2.4.0/core/compiler.common/src/org/jetbrains/kotlin/GeneratedDeclarationKey.kt#L6-L8)
- **Snippet**:
  ```kotlin
  package org.jetbrains.kotlin

  abstract class GeneratedDeclarationKey
  ```

## `@ExperimentalTopLevelDeclarationsGenerationApi`

### Claim: `@RequiresOptIn` annotation declared in `org.jetbrains.kotlin.fir.extensions`
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/ExperimentalTopLevelDeclarationsGenerationApi.kt:6-9`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/ExperimentalTopLevelDeclarationsGenerationApi.kt#L6-L9)
- **Snippet**:
  ```kotlin
  package org.jetbrains.kotlin.fir.extensions

  @RequiresOptIn("This API is experimental and is currently not supported by incremental compilation. Follow https://youtrack.jetbrains.com/issue/KT-66735 for the updates")
  annotation class ExperimentalTopLevelDeclarationsGenerationApi
  ```

## `IrDeclarationOrigin.GeneratedByPlugin(key)` constructor

### Claim: convenience constructor that takes a `GeneratedDeclarationKey` and stores `pluginId = key::class.qualifiedName!!`
- **File**: [`kotlin/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/declarations/IrDeclarationOrigin.kt:131-132`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/declarations/IrDeclarationOrigin.kt#L131-L132)
- **Snippet**:
  ```kotlin
  class GeneratedByPlugin private constructor(val pluginId: String, val pluginKey: GeneratedDeclarationKey?) : IrDeclarationOrigin {
      constructor(pluginKey: GeneratedDeclarationKey) : this(pluginKey::class.qualifiedName!!, pluginKey)
  ```

## `key.origin` → `FirDeclarationOrigin.Plugin` mapping

### Claim: extension property converts a key to `FirDeclarationOrigin.Plugin(this)`
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/declarations/FirDeclarationOrigin.kt:88-89`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/declarations/FirDeclarationOrigin.kt#L88-L89)
- **Snippet**:
  ```kotlin
  val GeneratedDeclarationKey.origin: FirDeclarationOrigin
      get() = FirDeclarationOrigin.Plugin(this)
  ```

### Claim: `FirDeclarationOrigin.Plugin` carries the key in a `val key` field
- **File**: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/declarations/FirDeclarationOrigin.kt:73`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/tree/src/org/jetbrains/kotlin/fir/declarations/FirDeclarationOrigin.kt#L73)
- **Snippet**:
  ```kotlin
  class Plugin(val key: GeneratedDeclarationKey) : FirDeclarationOrigin(displayName = "Plugin[$key]", generated = true) {
  ```

## "Side-effect-free" KDoc claim

### Claim: KDoc on `FirDeclarationGenerationExtension` mandates `generate*` be side-effect-free; IDE retries computation
- **File**: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:26-30`](https://github.com/JetBrains/kotlin/blob/v2.4.0/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L26-L30)
- **Snippet**:
  ```kotlin
  /**
   * All `generate*` members have the contract that the computation should be side-effect-free.
   * That means that all `generate*` function implementations should not modify any state or leak the generated `FirElement` or `FirBasedSymbol` (e.g., by putting it to some cache).
   * This restriction is imposed by the corresponding IDE cache implementation, which might retry the computation several times.
   */
  ```
