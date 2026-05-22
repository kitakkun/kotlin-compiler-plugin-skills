# Verification Result: 07-assign-expression-alterer

## Status: PASS

## Goal
Verify that `FirAssignExpressionAltererExtension` rewrites `prop = value` as
`prop.assign(value)` for properties whose declared type is
`com.example.Property<T>`.

## Outcome
With the plugin enabled, the sample `t.input = "OK"` (which would normally be a
type error because `t.input` is `Property<String>`, not `String`) compiled
cleanly and printed:

```
assigned: OK
get: OK
```

This proves the alterer ran during FIR resolution, replaced the variable
assignment with a `t.input.assign("OK")` function call, and the rewritten call
re-resolved correctly through the regular FIR pipeline.

## Plugin design
- `AssignComponentRegistrar` (`CompilerPluginRegistrar`) registers a
  `FirExtensionRegistrarAdapter` pointing at `AssignFirExtensionRegistrar`.
- `AssignFirExtensionRegistrar` registers the single extension
  `PropertyAssignAlterer`.
- `PropertyAssignAlterer.transformVariableAssignment(...)`:
  1. Bails out unless the LHS callee resolves to a `FirRegularPropertySymbol`.
  2. Resolves the property's return type to a regular class symbol; bails out
     unless its `ClassId` is exactly `com/example/Property`.
  3. Builds an unresolved `FirFunctionCall` whose:
     - `explicitReceiver` is a `FirPropertyAccessExpression` reusing the
       original `calleeReference`, `dispatchReceiver`, `explicitReceiver`,
       `extensionReceiver`, and `contextArguments`;
     - `argumentList` is `buildUnaryArgumentList(rValue)`;
     - `calleeReference` is a `FirSimpleNamedReference("assign")`.
  4. Returns it for the regular resolver to bind.

## Layout
```
verification/07-assign-expression-alterer/
  build.gradle.kts
  settings.gradle.kts          # rootProject.name = "07-assign-expression-alterer"
  gradle.properties
  plugin/
    build.gradle.kts
    src/main/kotlin/com/example/assign/
      AssignComponentRegistrar.kt
      AssignFirExtensionRegistrar.kt
      PropertyAssignAlterer.kt
    src/main/resources/META-INF/services/
      org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
  sample/
    build.gradle.kts
    src/main/kotlin/com/example/Main.kt
```

## Reproduce
```
../gradlew :sample:run 2>&1
```

## Notes / gotchas hit
- In Kotlin 2.3.21 the public `FirVariableAssignment` API only exposes
  `lValue` and `rValue`. The convenience accessors used by the alterer
  (`calleeReference`, `explicitReceiver`, `dispatchReceiver`,
  `extensionReceiver`, `contextArguments`) and `buildUnaryArgumentList` live
  in package `org.jetbrains.kotlin.fir.expressions` (NOT
  `org.jetbrains.kotlin.fir.expressions.builder` as the skill snippet
  suggests). Importing them from the wrong package caused the first
  compilation failure.
- The replacement `FirFunctionCall` is intentionally left unresolved (no
  `calleeReferenceSymbol`); the FIR resolver re-resolves `assign` after the
  alterer returns.
- We narrow on the property's class ID (`com.example.Property`) instead of an
  annotation, matching the task's spec ("any property whose type is
  `com.example.Property<T>`").
