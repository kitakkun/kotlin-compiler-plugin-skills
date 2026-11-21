# 10-function-type-kind — RESULT

**Status:** PASS (primary goal + bonus cross-kind rejection)

## Summary

The plugin declares a new function-type family `MyKindFunctionN` /
`KMyKindFunctionN` (package `com.example.mykind.synthetic`) by implementing
`FirFunctionTypeKindExtension` and registering the non-reflect/reflect pair
via `registerKind(MyKind, KMyKind)`. The discriminator annotation is
`com.example.MyKind`. An `IrGenerationExtension` (a direct adaptation of
`PluginFunctionKindsTransformer` from the kotlin sandbox plugin) lowers every
synthetic kind reference back to `kotlin.FunctionN` / `kotlin.reflect.KFunctionN`
at IR time and re-attaches `@MyKind` to the lambda's `IrFunction.annotations`,
so the JVM backend has a real class to emit and the marker survives in
metadata.

## Observed compiler/runtime output

`../gradlew :sample:run` (from a clean state):

```
> Task :sample:compileKotlin
> Task :sample:run
MyKind

BUILD SUCCESSFUL
```

The lambda passed to `acceptMyKind(block: @MyKind () -> Unit)` is resolved as
`MyKindFunction0<Unit>`, lowered to `kotlin.Function0<Unit>` at IR, and
`println("MyKind")` runs at runtime — no `ClassNotFoundException`.

## Bonus: cross-kind rejection (distinct types)

Temporarily moving `sample/src/test/kotlin/CrossKindRejection.kt` into
`sample/src/main/kotlin/` and running `:sample:compileKotlin` produces:

```
e: .../CrossKindRejection.kt:20:18 Argument type mismatch: actual type is
   'MyKindFunction0<Unit>', but '() -> Unit' was expected.
```

i.e. `expectsPlain(myKindLambda)` is rejected at compile time. This proves
the FIR resolver tracks the distinct `MyKindFunction0` class and refuses to
coerce *back* to a plain `Function0`. (The forward direction —
`acceptMyKind { ... }` with a plain lambda literal — *is* accepted because
`supportsConversionFromSimpleFunctionType` defaults to `true`, the same
ergonomics built-in `Function` kinds have.) The file is kept in
`src/test/kotlin` (not on the main compile path) so the standard build stays
green; reproduce by moving it into `src/main/kotlin`.

## Implementation notes

- **Two `FunctionTypeKind` objects**, paired:
  - `MyKind` (`isReflectType = false`, `prefixForTypeRender = "@MyKind"`).
  - `KMyKind` (`isReflectType = true`).
  - Both share `annotationOnInvokeClassId = ClassId.topLevel(FqName("com.example.MyKind"))`.
  - Both declare `serializeAsFunctionWithAnnotationUntil = LanguageVersion.KOTLIN_2_1.versionString`
    so older consumers see the type as plain `FunctionN + @MyKind`.
- **`MyKindFunctionTypeKindExtension`** overrides `registerKinds()` and calls
  `registerKind(MyKind, KMyKind)`. The pair contract
  (`nonReflect.reflectKind() === reflect && reverse`) is asserted by the
  registrar at session init.
- **`MyKindFirExtensionRegistrar`** registers the extension factory via
  `+::MyKindFunctionTypeKindExtension`. No predicates needed (the
  discriminator is the annotation's `ClassId`, not a predicate).
- **`MyKindFunctionKindsTransformer`** (IR side):
  - Walks `IrValueParameter`, `IrFunction.returnType`, `IrVariable.type`,
    `IrExpression.type` and remaps any classifier whose FQN starts with
    `com.example.mykind.synthetic.MyKindFunction` (or
    `…KMyKindFunction`) to `kotlin.FunctionN` /
    `kotlin.reflect.KFunctionN` of the same arity.
  - Rewrites `invoke()` calls on the synthetic types so `IrCall.symbol`
    points at the lowered class's `invoke`.
  - On `IrFunctionExpression` / `IrFunctionReference` whose declared type
    was a `MyKind*Function*`, adds `@MyKind` to the underlying
    `IrFunction.annotations` (idempotent — `hasAnnotation` guards re-adding).
  - Uses `IrPluginContext.finderForBuiltins()` (the IC-compatible reference
    API) — no deprecated `referenceClass` calls.
- **`MyKindIrGenerationExtension`** plugs the transformer into the IR
  pipeline; **`MyKindComponentRegistrar`** registers both the FIR registrar
  (`FirExtensionRegistrarAdapter.registerExtension(...)`) and the IR
  extension. `supportsK2 = true`, `pluginId = "com.example.my-kind"`.
- **Service file**:
  `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar`
  → `com.example.mykind.MyKindComponentRegistrar`.

## Sample

- `com.example.MyKind` annotation declared with
  `AnnotationTarget.TYPE, FUNCTION, PROPERTY_GETTER` and
  `AnnotationRetention.BINARY` (TYPE is mandatory — without it the user can't
  write `@MyKind () -> Unit`).
- `acceptMyKind(block: @MyKind () -> Unit)` and
  `acceptPlain(block: () -> Unit)` are the two parameter shapes used in the
  cross-kind comparison.
- `main()` calls `acceptMyKind @MyKind { println("MyKind") }`.

## Issues encountered

1. **Initial compile**: `Unresolved reference 'defaultType'` /
   `'constructors'` on `IrClass` / `IrClassSymbol`. Fixed by importing the
   extension properties from `org.jetbrains.kotlin.ir.util` (`defaultType`,
   `constructors`).
2. **`@OptIn` warnings**: `IrClassSymbol.owner` and the
   `IrClassSymbol.constructors` extension are now flagged with
   `@UnsafeDuringIrConstructionAPI`. Annotated the transformer class with
   `@OptIn(UnsafeDuringIrConstructionAPI::class)` to silence the chatter
   (the use is correct because `IrGenerationExtension` runs after IR
   construction is finished).

## Reproduce

```bash
cd verification/10-function-type-kind
../gradlew :sample:run                       # primary PASS ("MyKind")
# Bonus: move sample/src/test/kotlin/CrossKindRejection.kt into
# sample/src/main/kotlin/ and run ../gradlew :sample:compileKotlin to see the
# `Argument type mismatch: ... MyKindFunction0<Unit>, but () -> Unit` error.
```

## Re-run on Kotlin 2.4.20

**Status: PASS** (unchanged from the 2.3.21 result; no source changes were needed, only the version pins in `build.gradle.kts`).

```
$ ../gradlew --no-daemon -q clean :sample:run
MyKind
(exit code 0)
```

Validated with Kotlin 2.4.20, Gradle 9.5.0, JDK 21 on 2026-09-10.
