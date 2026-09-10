# 12-cross-module-ir-visibility — Verification Result

**Status: PASS**

A member function added by an `IrGenerationExtension` and registered via
`IrGeneratedDeclarationsRegistrar.registerFunctionAsMetadataVisible(...)` IS
referenceable from a downstream module's source code. The downstream module
does not need to apply the compiler plugin.

## How verified

From a fully clean state:

```
$ ../gradlew :module-b:run
> Task :plugin:compileKotlin
> Task :plugin:jar
> Task :module-a:compileKotlin     <-- plugin runs here, adds Foo.greet, writes it to metadata
> Task :module-a:jar
> Task :module-b:compileKotlin     <-- module-b's source `Foo().greet()` resolves OK
> Task :module-b:run
ir-generated

BUILD SUCCESSFUL
```

`module-b/src/main/kotlin/com/example/Main.kt`:

```kotlin
package com.example

fun main() {
    val foo = Foo()
    println(foo.greet())
}
```

`module-b` does NOT apply the compiler plugin (no `-Xplugin=`,
no `compilerPlugin` configuration on its build script). It only declares
`implementation(project(":module-a"))`. Yet:

1. **Compile time**: K2 / FIR resolves `foo.greet()` against the
   `kotlin.Metadata` payload published by `module-a`'s `Foo.class`.
   The IR-side-generated function is present in that metadata because the
   plugin called `pluginContext.metadataDeclarationRegistrar
   .registerFunctionAsMetadataVisible(greetFun)` during `module-a`'s IR phase.
2. **Run time**: the JVM finds the actual `greet` method on `Foo` (also
   emitted by the same IR pass) and invokes it, printing `ir-generated`.

## Negative control

To rule out "module-b might be reading a cached old artefact", the
`registerFunctionAsMetadataVisible` call was temporarily commented out, the
build was cleaned, and `:module-b:run` was retried. The result was:

```
> Task :module-b:compileKotlin FAILED
e: .../module-b/src/main/kotlin/com/example/Main.kt:5:17 Unresolved reference 'greet'.
```

So registering the function in metadata is not just decoration — it is the
single switch that makes the IR-generated member visible to downstream FIR
resolution. The IR call itself succeeded the same way in both runs (the
method was emitted into `Foo.class` regardless), but without the metadata
entry FIR has no way to see it.

## Confirming the metadata-write happened

Inspecting the published `Foo.class`:

```
$ javap -p module-a/build/classes/kotlin/main/com/example/Foo.class
Compiled from "Foo.kt"
public final class com.example.Foo {
  public com.example.Foo();
  public final java.lang.String greet();
}
```

`greet` appears as a regular instance method. Comparing against the failing
negative-control build (where `greet` was emitted as a `public static final
java.lang.String greet()`, see "Implementation caveat" below), the
PASS run's bytecode signature matches what FIR believes the metadata says.

The metadata payload is the `RuntimeVisibleAnnotations` -> `kotlin.Metadata`
on `Foo` (visible in `javap -v Foo.class` — pre-pended `mv`/`k`/`d1`/`d2`
fields). The byte-encoded `d1`/`d2` string is read by `kotlinx-metadata-jvm`
or `kotlinp` to recover the function list. We did not have `kotlinp` on the
host, but the cross-module compile succeeding is sufficient empirical proof
that `greet` is in the metadata — the only path by which a downstream FIR
could learn about it is the metadata.

## Implications for the SKILL claim

The `fir-declaration-generation-extension` skill currently positions
"synthesise declarations visible to source" as a defining property of FIR.
This verification shows that **IR-only declaration generation, paired with
`registerFunctionAsMetadataVisible`, ALSO produces source-visible
declarations — but only across module boundaries**.

The fuller picture is:

| Where the call sits | Same module as the plugin run | A downstream module |
|---|---|---|
| FIR-generated declaration | source-visible | source-visible |
| IR-only declaration WITHOUT `registerFunctionAsMetadataVisible` | not source-visible (FIR doesn't see it; would need direct bytecode access) | not source-visible |
| IR-only declaration WITH `registerFunctionAsMetadataVisible` | **NOT source-visible** | source-visible |

The intra-module asymmetry was confirmed during this verification by
temporarily adding a `intraModuleCheck()` function inside `module-a` that
called `Foo().greet()`. From a clean build, `:module-a:compileKotlin` failed
with `Unresolved reference 'greet'` — confirming that the metadata
written by the plugin IS NOT consulted by the very FIR pass that runs in
the same compilation. (FIR sees the metadata of *external* module
artefacts only; its picture of types in the current module comes from
in-memory FIR trees.)

So the precise claim is:

- **FIR generation** is the only way to get a source-visible declaration
  inside the same module that runs the plugin.
- **IR generation + `registerFunctionAsMetadataVisible`** is sufficient for
  source-visibility from a downstream module.

The kotlinx-serialization plugin uses exactly this asymmetry on purpose:
`write$Self()` is generated at IR (so it cannot be called from source in
the same module that defines the `@Serializable` class — preventing user
code from misusing the serialiser internals), but it is registered as
metadata-visible so cross-module synthetic-serializer code can call it.

## Implementation caveats

### Dispatch receiver is required even though the function is logically static

The bare `IrClass.addFunction { builder }` overload (`declarationBuilders.kt`,
v2.3.21) does NOT add a dispatch receiver parameter. Without one:

- The bytecode is emitted as a JVM-static method (`public static final String greet()`).
- `registerFunctionAsMetadataVisible` writes the member into metadata
  **as if it were a regular member** (Kotlin metadata distinguishes by
  parameter shape, not JVM static-ness).
- Downstream module compiles, but at runtime fails with
  `IncompatibleClassChangeError: Expecting non-static method 'java.lang.String com.example.Foo.greet()'`.

The fix is to attach a dispatch receiver explicitly:

```kotlin
parameters = listOf(createDispatchReceiverParameterWithClassParent())
```

The kotlinx-serialization plugin documents the same trap inline:

```
// Despite this function should be static in bytecode, registerFunctionAsMetadataVisible needs
// a correct dispatch receiver to handle function metadata correctly.
// This dispatchReceiverParameter will be removed later.
```

(quoted from `IrPreGenerator.preGenerateWriteSelfMethodIfNeeded`).

If your generated function genuinely needs to be JVM-static (e.g. an
`@JvmStatic` factory method on a companion), the canonical fix is:

1. Add the dispatch receiver before calling `registerFunctionAsMetadataVisible`.
2. After the registrar call, set `parameters = nonDispatchParameters` so JVM
   codegen emits it as static.

This trap is currently NOT documented in the
`ir-synthetic-class-generation` SKILL beyond the stub note "register every
function and constructor individually". A follow-up edit should add a worked
example of the dispatch-receiver requirement.

### `pluginId` is required on `CompilerPluginRegistrar` in 2.3.21

Same as in 02-declaration-generation: the registrar must override
`pluginId: String`. Older guides omit it; without it 2.3.21 fails with
`'pluginId' is not abstract...`.

### `@OptIn(UnsafeDuringIrConstructionAPI::class)` is needed

`createDispatchReceiverParameterWithClassParent()` walks `parentAsClass`,
which goes through `IrSymbol.owner` — guarded by
`UnsafeDuringIrConstructionAPI`. File-level opt-in is sufficient.

## Plugin shape

- `WithIrMethodComponentRegistrar : CompilerPluginRegistrar` — registers
  the `IrGenerationExtension`.
- `WithIrMethodIrGenerationExtension : IrGenerationExtension`:
  - Walks the module fragment with `IrVisitorVoid`.
  - For every `IrClass` annotated `@com.example.WithIrMethod`, builds a
    member `fun greet(): String` via `addFunction { ... }`,
    assigns `parameters = listOf(createDispatchReceiverParameterWithClassParent())`,
    fills the body with `irReturn(irString("ir-generated"))`,
    then calls `registerFunctionAsMetadataVisible(greetFun)`.

No FIR extension is registered. The only frontend-visible signal that the
plugin participated in `module-a`'s compilation is the published metadata
on `Foo`.

## Files

- `plugin/src/main/kotlin/com/example/irgen/WithIrMethodComponentRegistrar.kt`
- `plugin/src/main/kotlin/com/example/irgen/WithIrMethodIrGenerationExtension.kt`
- `plugin/src/main/resources/META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar`
- `module-a/src/main/kotlin/com/example/Foo.kt` (`@WithIrMethod class Foo`,
  `annotation class WithIrMethod`)
- `module-b/src/main/kotlin/com/example/Main.kt` (calls `Foo().greet()`)

## Suggested SKILL updates

1. **`fir-declaration-generation-extension` SKILL**: qualify the
   "synthesise … visible to source" bullet — call out that source visibility
   ACROSS module boundaries is also achievable from IR-only generation via
   `registerFunctionAsMetadataVisible`. The unique FIR property is "visible
   to source within the same module".
2. **`ir-synthetic-class-generation` SKILL**: add a dispatch-receiver
   requirement note next to the `registerFunctionAsMetadataVisible` example.
   The current text mentions `registerFunctionAsMetadataVisible` but does
   not warn that the bare `IrClass.addFunction { builder }` overload omits
   the dispatch receiver and produces metadata that does not match the
   emitted bytecode.

## Re-run on Kotlin 2.4.20

**Status: PASS** (unchanged from the 2.3.21 result; no source changes were needed, only the version pins in `build.gradle.kts`).

```
$ ../gradlew --no-daemon -q clean :module-b:run
ir-generated
(exit code 0)
```

Validated with Kotlin 2.4.20, Gradle 9.5.0, JDK 21 on 2026-09-10.
