---
name: fir-function-type-kind-extension
description: Declare a new family of function types — a parallel of `kotlin.FunctionN` / `kotlin.coroutines.SuspendFunctionN` — so that lambdas annotated with your marker (e.g. `@MyInlineable () -> Unit`) become a distinct type the compiler tracks through inference, overload resolution, and metadata. Used by Compose for `@Composable`. Covers FirFunctionTypeKindExtension, the FunctionTypeKind base class (packageFqName, classNamePrefix, annotationOnInvokeClassId, isInlineable, maxArity, prefixForTypeRender, serializeAsFunctionWithAnnotationUntil), the mandatory non-reflect/reflect kind pairing, and the IrGenerationExtension responsibility on the backend side. Read fir-extensions-overview first. NOT for changing modifiers (see fir-status-transformer-extension) or call resolution (see fir-function-call-refinement-extension).
---

# FirFunctionTypeKindExtension

This is the K2 extension point that **declares new function-type families**. The compiler ships with `kotlin.Function0..N` and `kotlin.coroutines.SuspendFunction0..N` as built-in kinds, plus their reflect-mode siblings `kotlin.reflect.KFunction0..N` and `kotlin.reflect.KSuspendFunction0..N`. A plugin can add its own pair: `some.MyInlineableFunctionN` + `some.KMyInlineableFunctionN`, gated by an annotation (`@MyInlineable`). The user then writes `@MyInlineable () -> Unit` and the compiler treats that as a separate type from `() -> Unit` for overload resolution, inference, and metadata.

This is exactly how Compose's `@Composable () -> Unit` works (Compose's compiler plugin uses this extension to add the `ComposableFunction` family).

Source: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirFunctionTypeKindExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirFunctionTypeKindExtension.kt). Reference impl: [`kotlin/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/types/SandboxFunctionTypeKindExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/types/SandboxFunctionTypeKindExtension.kt) (declares `MyInlineable*` and `MyNotInlineable*` families). Built-in kinds template: [`kotlin/core/compiler.common/src/org/jetbrains/kotlin/builtins/functions/FunctionTypeKind.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.10/core/compiler.common/src/org/jetbrains/kotlin/builtins/functions/FunctionTypeKind.kt) (`Function`, `SuspendFunction`, `KFunction`, `KSuspendFunction`).

## What you get

```kotlin
abstract class FirFunctionTypeKindExtension(session: FirSession) : FirExtension(session) {

    interface FunctionTypeKindRegistrar {
        fun registerKind(nonReflectKind: FunctionTypeKind, reflectKind: FunctionTypeKind)
    }

    abstract fun FunctionTypeKindRegistrar.registerKinds()

    fun interface Factory : FirExtension.Factory<FirFunctionTypeKindExtension>
}
```

One method to override (with the registrar as receiver). Inside it, call `registerKind(non, reflect)` once per family. Each `registerKind` call is two paired `FunctionTypeKind` objects — non-reflect (the lambda type) and reflect (the `KFunction`-equivalent).

## The `FunctionTypeKind` contract

```kotlin
abstract class FunctionTypeKind(
    val packageFqName: FqName,                     // e.g. FqName("some")
    val classNamePrefix: String,                   // e.g. "MyInlineableFunction" → produces classes some.MyInlineableFunction0..N
    val annotationOnInvokeClassId: ClassId,        // mandatory for plugins; the @-annotation that marks lambdas of this kind
    val isReflectType: Boolean,                    // true for KFunction-equivalent, false for Function-equivalent
    val isInlineable: Boolean,                     // can functions of this type be inlined?
    val maxArity: Int = DEFAULT_MAX_ARITY,         // 254 by default (JVM 255-param limit minus 1 for receiver)
)
```

Required overrides:

```kotlin
abstract fun nonReflectKind(): FunctionTypeKind   // self if isReflectType=false, else the paired non-reflect
abstract fun reflectKind(): FunctionTypeKind     // self if isReflectType=true, else the paired reflect
```

Optional but commonly overridden:

```kotlin
open val prefixForTypeRender: String? = null
// Renders @MyInlineable (Int) -> String instead of some.MyInlineableFunction1<Int, String>
// in error messages and IDE display.

open val serializeAsFunctionWithAnnotationUntil: String? = null
// Backwards compat: emit metadata as plain FunctionN + @-annotation for older language versions.
// Set to `LanguageVersion.KOTLIN_2_1.versionString` so consumers on Kotlin 2.0 still work.

open val supportsConversionFromSimpleFunctionType: Boolean = true
// Can a lambda of plain (Int) -> Unit be coerced to MyInlineableFunction1<Int, Unit>?
// Default true; false if your kind requires the marker annotation explicitly.
```

The pair contract is enforced at registration: the registrar asserts `nonReflect.reflectKind() === reflect && reflect.nonReflectKind() === nonReflect`. Mismatched pairs throw `IllegalArgumentException` at session init.

## End-to-end example: `@Stable () -> T` family

Goal: `@Stable () -> Int` is a distinct type from `() -> Int`. Useful for marking lambdas that the runtime treats as memoisable.

### 1. The annotation (user-facing)

```kotlin
package com.example.stable

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.PROPERTY_GETTER)
annotation class Stable
```

Without `AnnotationTarget.TYPE`, the user can't write `@Stable () -> Unit` (annotation positioning is a Kotlin source-level concern, not the extension's). Always include `TYPE`.

### 2. The kind objects

```kotlin
package com.example.stable.fir

import org.jetbrains.kotlin.builtins.functions.FunctionTypeKind
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val PKG = FqName("com.example.stable")
private val STABLE_CLASS_ID = ClassId.topLevel(PKG.child(Name.identifier("Stable")))

object StableFunction : FunctionTypeKind(
    packageFqName = PKG,
    classNamePrefix = "StableFunction",
    annotationOnInvokeClassId = STABLE_CLASS_ID,
    isReflectType = false,
    isInlineable = false,
) {
    override val prefixForTypeRender: String = "@Stable"
    override val serializeAsFunctionWithAnnotationUntil: String = LanguageVersion.KOTLIN_2_1.versionString
    override fun reflectKind(): FunctionTypeKind = KStableFunction
}

object KStableFunction : FunctionTypeKind(
    packageFqName = PKG,
    classNamePrefix = "KStableFunction",
    annotationOnInvokeClassId = STABLE_CLASS_ID,
    isReflectType = true,
    isInlineable = false,
) {
    override val serializeAsFunctionWithAnnotationUntil: String = LanguageVersion.KOTLIN_2_1.versionString
    override fun nonReflectKind(): FunctionTypeKind = StableFunction
}
```

### 3. The extension that registers the pair

```kotlin
package com.example.stable.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirFunctionTypeKindExtension

class StableFunctionTypeKindExtension(session: FirSession) : FirFunctionTypeKindExtension(session) {
    override fun FunctionTypeKindRegistrar.registerKinds() {
        registerKind(nonReflectKind = StableFunction, reflectKind = KStableFunction)
    }
}
```

### 4. Wire it up

```kotlin
class StableFirRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::StableFunctionTypeKindExtension
    }
}
```

After this, the compiler synthesises `com.example.stable.StableFunction0`, `StableFunction1`, ..., `KStableFunction0`, ... on demand for every arity the source code uses. The `invoke` method on each gets `@Stable` automatically (compiler-driven by `annotationOnInvokeClassId`).

### 5. The IR backend (mandatory)

The KDoc on `FunctionTypeKind` is unambiguous: **"if you provide some new functional type kind it's your responsibility to handle all references to it in backend with IrGenerationExtension implementation"**. The reason: the compiler doesn't know how to emit bytecode for `com.example.stable.StableFunction0` — there's no real class file. Your IR transformer must rewrite usages to a real type (typically `kotlin.Function0`) plus an annotation:

```kotlin
class StableIrTransformer(val pluginContext: IrPluginContext) : IrVisitorVoid() {

    override fun visitFunctionExpression(expression: IrFunctionExpression) {
        if (expression.type.isStableFunctionType()) {
            // 1. Rewrite the type to kotlin.FunctionN
            // 2. Add @Stable to the IrFunction's annotations
        }
        super.visitFunctionExpression(expression)
    }

    override fun visitFunctionReference(expression: IrFunctionReference) { /* same idea */ }

    // Map any IrType referencing some.StableFunction* to kotlin.Function* / kotlin.reflect.KFunction*
    private fun calculateUpdatedClassifier(classifier: IrClassifierSymbol): IrClassifierSymbol? { /* ... */ }
}
```

The reference impl `PluginFunctionKindsTransformer` in `plugin-sandbox/src/.../ir/` is ~150 lines and is the practical template. Without this transformer, the bytecode generator fails with "class not found" at the JVM bytecode emission phase.

**Retarget `IrCall.symbol` on `invoke()` calls**, not just type references. If your transformer only remaps `IrType`s, `block.invoke()` inside `acceptStable { ... }` will still call `some.StableFunction0.invoke` (whose owner class doesn't exist in bytecode) and runtime fails with `NoClassDefFoundError: some/StableFunction0`. For each `IrCall`:

```kotlin
override fun visitCall(expression: IrCall): IrExpression {
    val callee = expression.symbol.owner
    val ownerClass = callee.parentClassOrNull
    if (ownerClass != null && ownerClass.classId in myKindClassIds) {
        // Look up the corresponding member of the real Function/KFunction class
        val arity = (ownerClass.classId!!.shortClassName.asString().removePrefix("StableFunction")).toInt()
        val realClass = pluginContext.referenceClass(ClassId.fromString("kotlin/Function$arity"))!!
        val realInvoke = realClass.owner.functions.single { it.name == OperatorNameConventions.INVOKE }
        expression.symbol = realInvoke.symbol
    }
    return super.visitCall(expression)
}
```

**Constructing rewritten types**: at v2.3.21 the simplest working factory is `IrSimpleTypeImpl(classifier, nullability, arguments, annotations)`. The longer factory `IrSimpleTypeImpl(classifier, nullability, arguments, annotations, originalKotlinType = null)` still exists (the parameter was renamed from `kotlinType` to `originalKotlinType` and made nullable-defaulting), and the `abbreviation` parameter is fully gone. For 99% of plugin rewrites, omit `originalKotlinType` and let it default to `null`.

**Use `pluginContext.finderForBuiltins()`** rather than `pluginContext.referenceClass(ClassId)` for built-in types: `referenceClass` is `@Deprecated(WARNING)` in 2.3.21 with the message *"Please use `finderForBuiltins()` or `finderForSource(fromFile)` instead."*

**Annotation `EXPRESSION` retention**: if your `@MyKind` annotation has `AnnotationTarget.EXPRESSION` (so users can write `myFn @MyKind { ... }`), the annotation **must** use `AnnotationRetention.SOURCE`. Kotlin rejects expression-target annotations with `BINARY`/`RUNTIME` retention. The built-in `@Stable` example targets `FUNCTION`/`TYPE`/`PROPERTY_GETTER` only, so it doesn't trip this — your dialect may need to.

**`IrFunction.parameters` and `IrCall.arguments` are unified** (KT-68003). The legacy accessors `valueParameters` / `extensionReceiver` / `getValueArgument` / `valueArgumentsCount` were `@DeprecatedForRemovalCompilerApi` (error-level, non-`@Suppress`-able) through 2.3.x and were **removed in Kotlin 2.4.0**; only `dispatchReceiver` survives (as an `@UnsafeDuringIrConstructionAPI` convenience over `arguments[0]`). Use the unified `arguments` list (indexed slots) and `parameters` list (with `IrParameterKind`) instead. See [`ir-call-rewriting`](../ir-call-rewriting/guide.md) for the migration patterns.

## How resolution finds your kind

The resolver consults `FirFunctionTypeKindServiceImpl.extractor` (`compiler/fir/providers/src/.../FirFunctionTypeKindServiceImpl.kt`) which collects:

- All built-in kinds (`Function`, `SuspendFunction`, `KFunction`, `KSuspendFunction`).
- All kinds returned by registered `FirFunctionTypeKindExtension`s.

When the user writes `@MyAnnotation () -> Unit`, the resolver:

1. Sees the annotation `@MyAnnotation` on a function-type usage.
2. Finds the `FunctionTypeKind` whose `annotationOnInvokeClassId` matches `@MyAnnotation`.
3. Constructs `some.MyKind0<Unit>` instead of `kotlin.Function0<Unit>`.

The annotation is the discriminator. Two kinds with the same `annotationOnInvokeClassId` produce `ConeAmbiguousFunctionTypeKinds` on every function annotated with that class — pick a unique `ClassId` per kind.

## Common gotchas

### Forgetting the IR backend transformer

Symptoms: FIR resolves cleanly (the lambda has type `some.MyFunction0<Unit>`), but the build fails at JVM codegen with `IndexOutOfBoundsException` or `NoSuchClassException` referencing `some.MyFunction0`. The compiler will not auto-lower your custom kind. You must ship an `IrGenerationExtension` that rewrites every usage to a real built-in type + the annotation.

### `annotationOnInvokeClassId` collision with another plugin

Two plugins registering kinds that both target `@CustomFn` produce `ConeAmbiguousFunctionTypeKinds` on every site — and the diagnostic is opaque to users. Use a plugin-unique annotation namespace (`com.yourplugin.YourMarker`) and document conflicts in the plugin README.

### `maxArity` and JVM limits

JVM caps method parameter count at 255. `Function` defaults to `maxArity = 254` (one slot for the implicit `this`). `SuspendFunction` is `253` (additional slot for the `Continuation`). If your kind also injects a hidden parameter (continuation-style frameworks sometimes do), reduce `maxArity` accordingly — exceeding it produces `ClassFormatError: Too many arguments in method signature` at runtime.

### `serializeAsFunctionWithAnnotationUntil` is for cross-version compatibility

Setting this to a Kotlin version string makes the metadata writer emit the type as `kotlin.FunctionN + @YourAnnotation` for any **language-version target lower than the specified version**. Consumers on the older version see plain `FunctionN` (with the annotation if they care to read it); consumers on the new version see your synthetic kind. Use it during plugin migrations; remove once you've dropped support for the old language version. Without it, downstream modules on Kotlin 2.0 reading metadata produced by Kotlin 2.3 + your plugin choke on the unknown class.

### Reflect-kind without backend support

The reflect kind (`KMyFunction`) is what the user writes when they take `KFunction0`-equivalent reflection objects (rare in user code). If you don't ship reflection support in your runtime library, leave `isInlineable = false` and don't expose any synthetic API around it — but you still need to *register* the reflect kind (the registrar asserts a pair).

### `prefixForTypeRender` vs `prefixForTypeName`

The "name" property doesn't exist on `FunctionTypeKind` for plugin extensions; only `prefixForTypeRender`. The latter is for human-readable display (errors, hover). The actual class name is derived from `classNamePrefix + arity`. Don't confuse the two.

### `supportsConversionFromSimpleFunctionType = true` (default) lets users skip the annotation

If `true`, a plain `() -> Unit` lambda passed where `@Stable () -> Unit` is expected gets coerced — the user doesn't need to write `@Stable` at the call site. Set to `false` if you want the annotation to be mandatory at every site (Compose does this for `@Composable`). The default is `true` for ergonomics.

## Wiring caveats

- Like all FIR extensions, registration is `+::ExtensionConstructor`. No special opt-in required (unlike `FirFunctionCallRefinementExtension`).
- The extension is consulted **once per session** to populate the kind extractor, not per type usage. Adding/removing kinds at runtime isn't supported.
- The kinds you register are also enabled for **library FIR sessions** (the resolver running over compiled deps), so plugins downstream of you can write `@MyAnnotation` on lambda types they pass to your library functions.

## Test data shape

A representative box test (paraphrased from `plugin-sandbox/testData/box/inlineableFunction.kt`):

```
import com.example.stable.Stable

fun runStable(block: @Stable () -> Unit) { block() }

fun box(): String {
    var counter = 0
    val l1: @Stable (() -> Unit) = { counter++ }
    val l2: some.StableFunction0<Unit> = { counter++ }   // explicit synthetic name
    val l3 = @Stable { counter++ }

    runStable(l1)
    runStable(l2)
    runStable(l3)
    runStable { counter++ }                              // implicit conversion (default)

    return if (counter == 4) "OK" else "FAIL: $counter"
}
```

If everything is wired (FIR extension + IR transformer), `box()` returns `"OK"`. Without the IR transformer, the test fails at codegen.

## Relation to other extensions

- **Foundation** → [`fir-extensions-overview`](../fir-extensions-overview/guide.md), [`compiler-plugin-bootstrap`](../compiler-plugin-bootstrap/guide.md)
- **For an annotation that gates membership in this kind** — declared in user code, not via the predicate system; the discriminator is `annotationOnInvokeClassId` on the `FunctionTypeKind`.
- **For diagnostics on misuse of the new kind** (e.g. forbidding `@Stable` on suspend lambdas) → [`fir-additional-checkers-extension`](../fir-additional-checkers-extension/guide.md).
- **For backend handling** → an `IrGenerationExtension` is **mandatory** (not optional). Use [`ir-call-rewriting`](../ir-call-rewriting/guide.md) or [`ir-body-modification`](../ir-body-modification/guide.md) patterns to find and rewrite usages.
- **Compare with SAM-with-receiver** — that customises the function type *for one specific SAM interface*; this customises the function type *family for the whole language*.

## What this skill does NOT cover

- Reflection-runtime support — if your reflect kind should be queryable via `kotlin.reflect`, you need a runtime library that implements the reflection API. The compiler extension only declares the type.
- Cross-platform (KMP) considerations — the `packageFqName` and `classNamePrefix` must resolve on every target; if your runtime is JVM-only, your compiler plugin must restrict to JVM via configuration.
- IDE integration beyond what FIR gives you — type rendering, inspections, and quick-fixes around your kind require an IntelliJ plugin separately from this compiler extension.
- Suspend-style lifecycle (the `Continuation` parameter is unique to `SuspendFunction`; you can't replicate it without intrusive backend work in `kotlin/compiler/ir/` itself, which is out of plugin scope).
