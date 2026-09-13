# Changes affecting this skill

API migrations relevant to writing `FirDeclarationGenerationExtension` and using the `compiler/fir/plugin-utils/` `create*` helpers. This skill targets the **current stable Kotlin** (2.4.20). Only changes that are visible to plugin authors are listed; pure line drift and internal refactors are not.

## Kotlin 2.4.10 → 2.4.20

### `GeneratedDeclarationKey.toString()` now defaults to the key's simple class name

Commit `7726f1f61897` ("[Plugins] Stabilize plugin key `toString()` output and remove its redundant overrides") gave the abstract base class a `toString()` override that returns `this::class.simpleName!!`, so FIR dumps print `Plugin[MyKey]` deterministically instead of an identity hash.

Before (2.4.10) — every plugin key needed its own override to get a readable `Plugin[...]` origin:

```kotlin
object MyGeneratedDeclarationKey : GeneratedDeclarationKey() {
    override fun toString() = "MyGeneratedDeclarationKey"
}
```

After (2.4.20) — the override is redundant:

```kotlin
object MyGeneratedDeclarationKey : GeneratedDeclarationKey()
```

**Migration**: nothing breaks; an existing override still wins. Drop it unless you want a label that differs from the class name. If your tests compare FIR dumps that contain `Plugin[...]`, expect the text to change from the identity hash to the class name for keys that never overrode `toString()`.

Source: [`kotlin/core/compiler.common/src/org/jetbrains/kotlin/GeneratedDeclarationKey.kt:8-13`](https://github.com/JetBrains/kotlin/blob/v2.4.20/core/compiler.common/src/org/jetbrains/kotlin/GeneratedDeclarationKey.kt#L8-L13)

### `createConstructor(generateDelegatedNoArgConstructorCall = true)` no longer throws when no no-arg super constructor exists

Commits `7024ae7a7c44`, `f089ccb1c1bb`, and `432d71d5b709` (Lombok `@NoArgsConstructor` work) reworked the private helper behind `generateDelegatedNoArgConstructorCall`. At 2.4.10 it raised `error("No arguments constructor for class ... not found")` (and `error("... has more than one class supertypes")`). At 2.4.20 it resolves the superclass via `getSuperClassSymbolOrAny` and, if there is no zero-parameter constructor, leaves the constructor's delegated call `null` and returns normally.

The building block is now public, so you can check ahead of time:

```kotlin
import org.jetbrains.kotlin.fir.plugin.tryGeneratingNoArgDelegatingConstructorCall

// FirClassSymbol<*>.tryGeneratingNoArgDelegatingConstructorCall(session): FirDelegatedConstructorCall?
val delegatedCall = owner.tryGeneratingNoArgDelegatingConstructorCall(session)
```

**Migration**: if you relied on the exception to surface misconfigured supertypes, that signal is gone — the failure now moves to the backend (`not generated yet` / missing delegating call at codegen). Guard with `tryGeneratingNoArgDelegatingConstructorCall(...) != null` before generating, or build the delegating call yourself in `config`.

Sources: [`kotlin/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ConstructorBuildingContext.kt:163-188`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/plugin-utils/src/org/jetbrains/kotlin/fir/plugin/ConstructorBuildingContext.kt#L163-L188), [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/resolve/SupertypeUtils.kt:363-369`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/resolve/SupertypeUtils.kt#L363-L369)

### New `generateFields` callback, gated by `@UnsafePluginApi`

Commit `fc642c5cae9b` added a new open member to `FirDeclarationGenerationExtension`:

```kotlin
@UnsafePluginApi
open fun generateFields(callableId: CallableId, context: MemberGenerationContext?): List<FirFieldSymbol> = emptyList()
```

Its KDoc states it is "designed for Java interop (to generate Java fields) and not useful for general plugins". `@UnsafePluginApi` is a new `@RequiresOptIn("This API is unsafe to use")` annotation in `org.jetbrains.kotlin.fir.extensions`.

**Migration**: none required — the method has a default body, so existing subclasses compile unchanged. Do not override it for ordinary Kotlin properties; keep using `generateProperties`.

Sources: [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt:58-62`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/FirDeclarationGenerationExtension.kt#L58-L62), [`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/UnsafePluginApi.kt:8-9`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/providers/src/org/jetbrains/kotlin/fir/extensions/UnsafePluginApi.kt#L8-L9)

### Not affecting this extension

- `FirDeclarationOrigin.Synthetic.ValueClassMember` was split into `InlineClassMember` / `FullValueClassMember`, and `IrDeclarationOrigin.GENERATED_SINGLE_FIELD_VALUE_CLASS_MEMBER` / `GENERATED_MULTI_FIELD_VALUE_CLASS_MEMBER` were renamed to `GENERATED_INLINE_CLASS_MEMBER` / `GENERATED_FULL_VALUE_CLASS_MEMBER` (plus a new `LAMBDA_EXTENSION_RECEIVER`). `FirDeclarationOrigin.Plugin`, `GeneratedDeclarationKey.origin`, and `IrDeclarationOrigin.GeneratedByPlugin` are unchanged apart from line drift.
