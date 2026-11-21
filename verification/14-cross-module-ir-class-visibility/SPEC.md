# Verification 14 — Cross-module visibility of a whole IR-generated class (`registerClassAsMetadataVisible`)

## Goal

Determine whether an entire class synthesized by an `IrGenerationExtension` — with a
constructor, a `val` property, and a member function — and registered with **one**
call to `pluginContext.metadataDeclarationRegistrar.registerClassAsMetadataVisible(irClass)`
(new in Kotlin 2.4.20, KT-79565) can be referenced **from a downstream module's source
code** without that module applying the plugin.

The claim under test comes from `references/ir-synthetic-class-generation/guide.md`
section 6 ("Make the class visible to metadata (K2 only)") and its `CHANGES.md`: the
registrar "recursively registers the class's constructors, functions, properties
(through the new `registerPropertyAsMetadataVisible`), and nested/inner classes".
All three member kinds are therefore used from the downstream module.

Optional extra (documented in RESULT.md): a nested class generated inside the
source-declared `Foo`, registered with its own `registerClassAsMetadataVisible` call.

## Project layout

Three Gradle subprojects in one build (same shape as probe 12):

- `plugin/` — the compiler plugin.
  - `GenerateHolderIrGenerationExtension.generate(...)` visits every `IrFile`; for every
    top-level class annotated `@com.example.GenerateHolder` it builds a new top-level
    class next to it:
    ```kotlin
    class FooHolder(value: String) {
        val value: String = value          // backing field + default getter
        fun describe(): String = "holder:" + value
    }
    ```
    using `irFactory.buildClass`, `addProperty` + `addBackingField` + `addDefaultGetter`,
    `addConstructor` + `addValueParameter` (body: `Any()` delegation, instance initializer,
    `irSetField`), and `addFunction` (with an explicit dispatch receiver).
  - Appends the class to `file.declarations` and calls
    `pluginContext.metadataDeclarationRegistrar.registerClassAsMetadataVisible(holder)` once.
    No per-member `register*AsMetadataVisible` calls.
  - Extra: builds `class Nested { fun ping(): String }` inside `Foo` and registers it
    with a second `registerClassAsMetadataVisible(nested)` call.
- `module-a/` — defines `annotation class GenerateHolder` and `@GenerateHolder class Foo`.
  Applies the plugin via the `compilerPlugin` configuration + `-Xplugin=`.
  Neither `FooHolder` nor `Foo.Nested` exists in source.
- `module-b/` — depends on `:module-a` only. Does NOT apply the plugin.
  ```kotlin
  fun main() {
      val holder = FooHolder("x")                                        // constructor
      println("value=${holder.value} describe=${holder.describe()}")    // property + function
      println(Foo.Nested().ping())                                       // nested class
  }
  ```

## PASS criterion

From inside this directory, on a clean build:

```
../gradlew --no-daemon -q clean :module-b:run
```

compiles `module-b` and prints exactly:

```
value=x describe=holder:x
nested-in-Foo
```

## FAIL criterion

`:module-b:compileKotlin` fails with `Unresolved reference 'FooHolder'` (or
`'value'` / `'describe'` / `'Nested'`), or the run fails at runtime with an
`IncompatibleClassChangeError` / `NoSuchMethodError` (metadata and bytecode disagree).

## Skills consulted

- `references/ir-synthetic-class-generation/guide.md` — "IR-only pattern" steps 1–6 and
  the "Common gotchas" section (`addFunction` dispatch receiver).
- `references/ir-synthetic-class-generation/CHANGES.md` — the 2.4.10 → 2.4.20 entry.
- `references/ir-plugincontext-usage/guide.md` — `metadataDeclarationRegistrar`.
- `references/compiler-plugin-bootstrap/guide.md` — registrar / `-Xplugin=` wiring (via probe 12).

## Reference patterns from kotlin v2.4.20

- `plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/ir/GeneratedTopLevelClassIrGenerator.kt`
  (the upstream test generator for `registerClassAsMetadataVisible`; cases 1 "Plain" and
  6 "nested class inside a source-declared outer" are the shapes reproduced here).
- `compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrIrGeneratedDeclarationsRegistrar.kt`
  (`registerClassAsMetadataVisible`, the member walk, `toFirContainingDeclaration`).
- `compiler/ir/backend.common/src/org/jetbrains/kotlin/backend/common/extensions/IrGeneratedDeclarationsRegistrar.kt`
  (abstract API).
- `compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/builders/declarations/declarationBuilders.kt`
  (`addProperty`, `addBackingField`, `addDefaultGetter`, `addConstructor`, `addFunction`).
