# Verification 12 — Cross-module IR-only declaration visibility

## Goal

Determine whether a member function added by an `IrGenerationExtension` (via
`IrGeneratedDeclarationsRegistrar.registerFunctionAsMetadataVisible(...)`) can
be referenced **from a downstream module's source code** — without that
downstream module applying the plugin.

The question matters because the
`fir-declaration-generation-extension` skill claims that synthesising
classes/functions/properties/constructors "visible to source" is a property of
the FIR side. If IR-only generation can produce equally source-visible
declarations across module boundaries, the FIR claim should be qualified.

## Project layout

Three Gradle subprojects in one composite build:

- `plugin/` — the compiler plugin.
  - `IrGenerationExtension.generate(...)` walks the module's IR, finds every
    class annotated `@com.example.WithIrMethod`, and adds a member function
    `fun greet(): String { return "ir-generated" }` to it via
    `IrFactory.addFunction { ... }` and `DeclarationIrBuilder.irBlockBody`.
  - After building the function, calls
    `pluginContext.metadataDeclarationRegistrar.registerFunctionAsMetadataVisible(generatedFunction)`.
- `module-a/` — defines `annotation class WithIrMethod` and `@WithIrMethod class Foo`.
  Applies the plugin via the `compilerPlugin` configuration + `-Xplugin=`.
  `Foo` does NOT declare `greet()` in source.
- `module-b/` — depends on `:module-a` only. Does NOT apply the plugin.
  `fun main() { println(Foo().greet()) }` references the IR-generated method.

## PASS criterion

`./gradlew :module-b:run` (from a clean state) compiles `module-b` and prints
`ir-generated`. This proves that an IR-only declaration registered through
`registerFunctionAsMetadataVisible` is visible to a downstream module's FIR
resolution.

## FAIL criterion

`module-b:compileKotlin` fails with `Unresolved reference: greet`. That would
support the original SKILL claim that source-visibility is exclusively a FIR
property.

## Skills consulted

- `compiler-plugin-bootstrap`
- `ir-plugincontext-usage` (the `metadataDeclarationRegistrar` section)
- `ir-synthetic-class-generation` (Section 6: "Make members visible to metadata")
- `ir-body-modification`

## Reference patterns from kotlin-lang v2.3.21

- `plugins/plugin-sandbox/src/.../AllPropertiesConstructorIrGenerator.kt`
  (the canonical small example of `registerFunctionAsMetadataVisible`).
- `plugins/kotlinx-serialization/.../IrPreGenerator.kt`
  (production pattern; comments explain that a dispatch receiver must be set
  for metadata to be correct even when the function is JVM-static in bytecode).
