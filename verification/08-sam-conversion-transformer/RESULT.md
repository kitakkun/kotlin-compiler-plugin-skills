# 08-sam-conversion-transformer

## Result: PASS

`../gradlew :sample:run` compiles cleanly and prints `got: hello`.

## What this verifies

`FirSamConversionTransformerExtension` can rewrite the function-type used during
Kotlin's SAM conversion so that the **first abstract-method parameter is promoted
to the lambda's extension receiver**. This is the same pattern as the official
`sam-with-receiver` plugin used by Gradle's Kotlin DSL.

## Plugin design

- Marker annotation: `com.example.WithReceiver`.
- Single FIR extension: `WithReceiverSamConversionTransformer`
  (`plugin/src/main/kotlin/com/example/samreceiver/fir/WithReceiverSamConversionTransformer.kt`)
  overrides `getCustomFunctionTypeForSamConversion(function)` to:
  1. Look up the SAM's containing class via `function.containingClassLookupTag()?.toRegularClassSymbol(session)`.
  2. Check whether that class is annotated `@WithReceiver` via `resolvedAnnotationClassIds`.
  3. If yes, build a `ConeLookupTagBasedType` with `createFunctionType(...)` where
     `parameterTypes[0]` becomes the `receiverType` and the remaining parameters
     become the lambda's value parameters.
  4. Return `null` otherwise (default SAM conversion applies).
- Wiring: `SamReceiverFirExtensionRegistrar` registers the transformer factory;
  `SamReceiverComponentRegistrar` adds it via `FirExtensionRegistrarAdapter`.

The implementation mirrors `FirSamWithReceiverConventionTransformer.kt` from
Kotlin v2.3.21, except the marker annotation is hard-coded (a single `ClassId`)
instead of being passed in via a CLI option.

## Sample under test

```kotlin
package com.example

annotation class WithReceiver

@WithReceiver
fun interface Action<T> {
    fun run(receiver: T)
}

fun execute(action: Action<String>) {
    action.run("hello")
}

fun main() {
    execute { println("got: " + this) }
}
```

The lambda body uses `this` (a `String`) where ordinarily there would be no
implicit receiver — that compiles only because the plugin rewrote the SAM
conversion type from `(String) -> Unit` to `String.() -> Unit`.

## Run output

```
> Task :sample:run
got: hello

BUILD SUCCESSFUL
```

## Notes / gotchas hit during build-up

- In Kotlin 2.3.21 `CompilerPluginRegistrar.pluginId` is an abstract `val`, so
  it must be overridden (the original 01-hello-plugin template already does
  this; the 04-status-transformer sample does not because it predates the API
  change). Forgetting the override causes
  `Class 'SamReceiverComponentRegistrar' is not abstract and does not implement abstract base class member: val pluginId: String`.
- `FirSamConversionTransformerExtension` lives in
  `org.jetbrains.kotlin.fir.resolve` (not `…fir.extensions`).
- `createFunctionType` and `functionTypeService` extension are also in
  `org.jetbrains.kotlin.fir.resolve` / `org.jetbrains.kotlin.fir.types`.
- `containingClassLookupTag` is the top-level extension on `FirCallableDeclaration`
  in `org.jetbrains.kotlin.fir`.
