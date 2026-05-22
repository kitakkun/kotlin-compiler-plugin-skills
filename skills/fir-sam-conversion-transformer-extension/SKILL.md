---
name: fir-sam-conversion-transformer-extension
description: Customize the function type used during Kotlin's SAM (Single Abstract Method) conversion — typically promoting the first parameter of a SAM-interface method to a receiver, so lambdas can use `this` instead of an explicit parameter (the `sam-with-receiver` plugin pattern). Covers FirSamConversionTransformerExtension, the single `getCustomFunctionTypeForSamConversion` hook, where the FIR resolver invokes it (firstNotNullOfOrNull, no priority), and the standard "annotation-on-containing-class" gating idiom. Read fir-extensions-overview first. NOT for changing whether a class is SAM-eligible (that's `fun interface` declaration) or for transforming arbitrary calls (see fir-function-call-refinement-extension).
---

# FirSamConversionTransformerExtension

This is the K2 extension point that lets a plugin **rewrite the function-type signature used when converting a lambda to a SAM interface**. The most common use is the "SAM with receiver" pattern: given `interface Sam { void run(String a) }`, instead of expecting a `(String) -> Unit` lambda, the plugin makes the resolver expect `String.() -> Unit` so the user can write `{ this.length }` rather than `{ a -> a.length }`. This is exactly what the official `sam-with-receiver` plugin does (used by Gradle's Kotlin DSL among others).

Source: [`kotlin/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/FirSamConversionTransformerExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.3.21/compiler/fir/resolve/src/org/jetbrains/kotlin/fir/resolve/FirSamConversionTransformerExtension.kt). Reference impls: [`kotlin/plugins/sam-with-receiver/sam-with-receiver.k2/src/org/jetbrains/kotlin/samWithReceiver/k2/FirSamWithReceiverConventionTransformer.kt`](https://github.com/JetBrains/kotlin/blob/v2.3.21/plugins/sam-with-receiver/sam-with-receiver.k2/src/org/jetbrains/kotlin/samWithReceiver/k2/FirSamWithReceiverConventionTransformer.kt) and the scripting plugin's mirror.

## What you get

```kotlin
abstract class FirSamConversionTransformerExtension(session: FirSession) : FirExtension(session) {
    abstract fun getCustomFunctionTypeForSamConversion(function: FirNamedFunction): ConeLookupTagBasedType?

    fun interface Factory : FirExtension.Factory<FirSamConversionTransformerExtension>
}
```

Single method, single decision. The resolver hands you the **abstract method of a `fun interface`** (already resolved); you return:

- A `ConeLookupTagBasedType` (the function type the resolver should use for SAM conversion) — typically built via `createFunctionType(kind, parameters, receiverType, rawReturnType)`.
- `null` if you don't recognise this SAM interface and want default conversion to apply.

## When the resolver calls this extension

`FirSamResolver.resolveFunctionTypeIfSamInterface` (`compiler/fir/resolve/src/.../FirSamResolver.kt:287-289` at v2.3.21):

```kotlin
val typeFromExtension = samConversionTransformers.firstNotNullOfOrNull {
    it.getCustomFunctionTypeForSamConversion(abstractMethod)
}
SAMInfo(abstractMethod.symbol, typeFromExtension ?: abstractMethod.getFunctionTypeForAbstractMethod(session))
```

Three things to notice:

1. **Only `fun interface` triggers it** — the resolver already filters `if (!firRegularClass.status.isFun) return@getOrPut null` before getting to your extension. Plain `interface`s don't go through SAM conversion at all.
2. **`firstNotNullOfOrNull` semantics** — the first extension that returns non-null wins; subsequent ones are not consulted. There is **no priority mechanism** and no ambiguity diagnostic. Two plugins that both want to claim the same SAM interface silently race.
3. **Result is cached per SAM class** (`resolvedFunctionType.getOrPut(firRegularClass)`), so your method is called once per `fun interface` per session — not per call site.

## End-to-end example: `@FirstParamAsReceiver` SAM convention

Goal: any `fun interface` whose containing class is annotated `@FirstParamAsReceiver` should treat its first abstract-method parameter as a receiver.

```kotlin
// User code:
package com.example.receiver

annotation class FirstParamAsReceiver

@FirstParamAsReceiver
fun interface Handler<T> {
    fun handle(receiver: T, message: String)
}

fun runHandler(value: Int, handler: Handler<Int>) {
    handler.handle(value, "go")
}

fun main() {
    runHandler(42) { msg ->
        // `this` here is Int (the first param promoted)
        println("$this got $msg")
    }
}
```

### 1. The transformer

```kotlin
package com.example.receiver.fir

import org.jetbrains.kotlin.builtins.functions.FunctionTypeKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.containingClassLookupTag
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.resolve.FirSamConversionTransformerExtension
import org.jetbrains.kotlin.fir.resolve.createFunctionType
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.functionTypeService
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.runIf

class FirstParamAsReceiverTransformer(session: FirSession) : FirSamConversionTransformerExtension(session) {

    override fun getCustomFunctionTypeForSamConversion(function: FirNamedFunction): ConeLookupTagBasedType? {
        // Look at the SAM's containing class annotations.
        val containingClassSymbol = function.containingClassLookupTag()?.toRegularClassSymbol(session) ?: return null

        return runIf(containingClassSymbol.resolvedAnnotationClassIds.any { it == MARKER_CLASS_ID }) {
            val parameterTypes = function.valueParameters.map { it.returnTypeRef.coneType }
            // Need at least one parameter to promote to receiver.
            if (parameterTypes.isEmpty()) return@runIf null

            val kind = session.functionTypeService
                .extractSingleSpecialKindForFunction(function.symbol)
                ?: FunctionTypeKind.Function

            createFunctionType(
                kind,
                parameters = parameterTypes.drop(1),
                receiverType = parameterTypes[0],
                rawReturnType = function.returnTypeRef.coneType,
            )
        }
    }

    companion object {
        private val MARKER_CLASS_ID = ClassId(FqName("com.example.receiver"), Name.identifier("FirstParamAsReceiver"))
    }
}
```

### 2. Wire it up

```kotlin
class ReceiverFirRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::FirstParamAsReceiverTransformer
    }
}
```

If your plugin takes the marker class IDs from a CLI option (as `sam-with-receiver` does), use the `.bind(...)` helper:

```kotlin
class ReceiverFirRegistrar(private val annotations: List<String>) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::FirstParamAsReceiverTransformer.bind(annotations)
    }
}
```

`bind` is a partial-application helper provided by `FirExtensionRegistrar` for constructors taking parameters beyond `FirSession`.

## The function-type construction

The return type is built with `createFunctionType` (from `kotlin/compiler/fir/types/.../FunctionTypeUtils.kt` or similar — exact location varies by Kotlin version, but the function is published API). Its parameters:

- `kind: FunctionTypeKind` — `Function`, `SuspendFunction`, or a plugin-provided kind from `FirFunctionTypeKindService`. Both reference impls call `extractSingleSpecialKindForFunction(function.symbol)` to honour `suspend` and other custom kinds; if the SAM method itself is annotated with a custom function-type-kind annotation, the resulting lambda type must use the matching kind.
- `parameters` — the value-parameter types **after** dropping the one that becomes the receiver.
- `receiverType` — the type that becomes the lambda's extension receiver (so users can write `this`).
- `rawReturnType` — copy from `function.returnTypeRef.coneType`.

## Common gotchas

### Empty-parameter SAMs need an explicit `null`

If the abstract method has zero parameters, you can't promote anything to a receiver — there's nothing to take. Both reference impls handle this with an explicit `if (parameterTypes.isEmpty()) return null`. Skipping this guard either crashes (out-of-bounds on `parameters[0]`) or produces a nonsensical function type with the wrong arity.

### Annotation lookup is on the **containing class**, not the function

`@SamWithReceiver` is meant to be applied to the `fun interface` itself, not to its abstract method. Look up via `function.containingClassLookupTag()?.toRegularClassSymbol(session)`. Annotations on the abstract method itself are a different concept (e.g. `@MyInlineable` for function-type-kind extensions) — don't conflate them.

### No ambiguity diagnostic when two extensions both claim a SAM

`firstNotNullOfOrNull` returns the first hit. If you ship two plugins (one for `@SamWithReceiver`, one for `@FirstParamAsReceiver`), and an interface is annotated with both, only the **first registered** extension's transformation applies. The user never sees a warning. This is by design but easy to forget — pick a unique marker annotation per plugin.

### The transformation cache is per-session

`resolvedFunctionType.getOrPut(firRegularClass)` caches the result for the entire FIR session. If you compute the function type from session-level state that may change later (rare, but possible with plugin scopes), the cache holds the first value. In practice this is never a problem because annotations on classes don't change mid-compilation.

### Reflect-kind handling

For SAM conversion, the resolver always wants the **non-reflect** kind (lambdas aren't `KFunction`s). `extractSingleSpecialKindForFunction` returns the non-reflect counterpart if the function symbol has a reflect-kind annotation, but in practice the SAM's abstract method won't be annotated with a reflect-kind annotation — it's a real method on a real interface. You can default to `FunctionTypeKind.Function` and the result is correct.

### The extension is consulted only for `fun interface`

If you want to opt a plain `interface` into receiver-style lambdas, this extension is the wrong tool — the user has to declare `fun interface`. There's no extension that promotes `interface` to `fun interface`; the `fun` modifier is a source-level concept the resolver requires.

## Test data shape

A box test verifying the transformation:

```
// FILE: lib.kt
package com.example.receiver
annotation class FirstParamAsReceiver

@FirstParamAsReceiver
fun interface Handler {
    fun handle(receiver: String, message: String): String
}

fun runHandler(value: String, handler: Handler): String = handler.handle(value, "K")

// FILE: main.kt
fun box(): String = runHandler("O") { msg -> this + msg }
```

If the transformer is wired correctly, `box()` returns `"OK"`. Without the transformer, the body `this + msg` would be a compile error because there's no implicit receiver.

## Relation to other extensions

- **Foundation** → `fir-extensions-overview`, `compiler-plugin-bootstrap`
- **Filtering by annotation** — same predicate-style approach as `fir-predicate-system`, but you don't need a `LookupPredicate` here; just check `containingClass.resolvedAnnotationClassIds`.
- **For changing the function type kind itself** (e.g. introducing `@Composable () -> Unit` as a distinct type) → `fir-function-type-kind-extension` is what you want, not this extension.
- **For altering the call expression** rather than the SAM type → `fir-function-call-refinement-extension`.

## What this skill does NOT cover

- Making non-`fun` interfaces SAM-eligible — there's no such extension; `fun interface` is the user-facing opt-in.
- Multi-receiver SAMs (Kotlin's `context(...)` parameters) — the API only supports a single receiver promotion.
- Java-side SAM types compiled from `@FunctionalInterface` — same transformer applies, but Java sources don't carry Kotlin annotations directly; the marker annotation must be Java-readable (`@Retention(RUNTIME)` etc.) and the lookup goes through the Java symbol table.
