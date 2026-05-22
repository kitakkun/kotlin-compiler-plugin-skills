# 06-expression-resolution — verification result

## Status: PASS

`../gradlew :sample:run` compiles cleanly and prints:

```
@DslContext entered
from Dsl
```

The string `from Dsl` is the result of the top-level extension `fun Dsl.greet(): String`
called as `greet()` (no explicit receiver) inside the `@DslContext`-annotated function.
The plugin's `FirExpressionResolutionExtension` injected `Dsl` as an implicit
extension receiver for that call site.

## Plugin layout

- `plugin/src/main/kotlin/com/example/dslcontext/DslContextComponentRegistrar.kt` —
  registers both the FIR receiver injector and an IR generation extension.
- `plugin/src/main/kotlin/com/example/dslcontext/DslContextExtensionRegistrar.kt` —
  the `FirExtensionRegistrar` that wires `DslContextReceiverInjector`.
- `plugin/src/main/kotlin/com/example/dslcontext/DslContextReceiverInjector.kt` —
  the `FirExpressionResolutionExtension`. Closely follows the
  `AlgebraReceiverInjector` reference: builds a synthetic
  `FirReceiverParameter` of type `com.example.Dsl` (looked up via
  `session.symbolProvider.getClassLikeSymbolByClassId`) and returns an
  `ImplicitExtensionReceiverValue`. Filters by the
  `@com.example.DslContext` annotation through the predicate system
  (`session.predicateBasedProvider.matches`) and registers the FQN in
  `registerPredicates`.
- `plugin/src/main/kotlin/com/example/dslcontext/DslContextIrGenerationExtension.kt` —
  IR pass that fixes up the synthetic receiver at runtime (see "Why an IR pass
  is needed" below).

## Sample

```kotlin
// sample/src/main/kotlin/Dsl.kt
package com.example

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class DslContext

class Dsl

fun Dsl.greet(): String = "from Dsl"
```

```kotlin
// sample/src/main/kotlin/Main.kt
import com.example.Dsl
import com.example.DslContext
import com.example.greet

@DslContext
fun runDsl(d: Dsl) {
    println("@DslContext entered")   // "trigger" call (see below)
    println(greet())                 // resolves as Dsl.greet() via plugin
}

fun main() {
    runDsl(Dsl())
}
```

## Two non-obvious points discovered while building this

### 1. Receivers are added AFTER the current call resolves, not before it

Looking at the call-site of the extension hook
(`FirExpressionsResolveTransformer.kt:722` in 2.3.21):

```kotlin
context.addReceiversFromExtensions(result, components)
```

This runs *after* `completeCall(...)` returns, which means
`addNewImplicitReceivers` is consulted *per call*, but the receivers it
returns only become visible to **subsequent** calls in the same body.

That mirrors the canonical Algebra test (`receiverInjection.kt`):
```kotlin
a1 + a2          // error: no receiver yet
injectAlgebra<A>()
a1 + a2          // ok: receiver was just installed
```

The first attempt at the sample was the literal task example
`@DslContext fun runDsl(d: Dsl) { println(greet()) }`. With `greet()` as the
**first and only** call inside `runDsl`, the plugin had no earlier call to
piggyback on, so the receiver was never installed and `greet()` stayed
unresolved (`Unresolved reference 'greet'`). Adding *any* prior call (here
`println("@DslContext entered")`) fixed resolution because the plugin returns
the receiver from that earlier `println` call, and the receiver is then in
scope when `greet()` is resolved.

### 2. The synthetic receiver has no runtime value — needed an IR pass

`buildReceiverParameter { ... symbol = FirReceiverParameterSymbol() }` produces a
purely synthetic receiver parameter — no real argument, no real `this`. After
FIR resolution succeeds, `fir2ir` emits an `IrErrorCallExpression`
(`Unresolved reference: this@runDsl`, type `com.example.Dsl`) where the
extension receiver expression should go. JVM codegen then fails:

```
java.lang.AssertionError: Unexpected IR element found during code generation.
ERROR_CALL 'Unresolved reference: this@R|/runDsl|' type=com.example.Dsl
```

So a small IR lowering (`DslContextIrGenerationExtension`) walks
`@DslContext`-annotated functions, finds the first `Dsl`-typed value parameter,
and rewrites every `IrErrorCallExpression` of type `com.example.Dsl` inside
the body into an `IrGetValueImpl` of that parameter. After this rewrite,
codegen succeeds and the program prints `from Dsl`.

This pairing (FIR injects the receiver in resolution; IR substitutes a real
value at lowering) is the same shape real plugins use — kotlin-dataframe's
`ReturnTypeBasedReceiverInjector` is the FIR side, and the dataframe plugin
ships its own IR lowering for the runtime side. The Algebra test plugin in
`plugin-sandbox` only has the FIR half because its tests run
`RUN_PIPELINE_TILL: FRONTEND` and never reach codegen.
