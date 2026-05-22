# 02-declaration-generation — Verification Result

**Status: PASS**

## Goal

Verify that `FirDeclarationGenerationExtension` can synthesise a `companion object` with a member function (`fun greet(): String`) on classes annotated with `@com.example.WithCompanion`, and that an `IrGenerationExtension` can fill in the body of the synthesised function so the runtime call works.

## How verified

```
$ ../gradlew :sample:run
> Task :plugin:compileKotlin
> Task :sample:compileKotlin
> Task :sample:run
hello

BUILD SUCCESSFUL
```

The sample's `fun main() { println(Foo.greet()) }` compiled (referencing `Foo.greet()` which only exists because of the plugin) and at runtime printed `hello`.

## Plugin shape

- `WithCompanionComponentRegistrar` (`CompilerPluginRegistrar`, K2-only): registers the FIR extension registrar via `FirExtensionRegistrarAdapter` and the `IrGenerationExtension`.
- `WithCompanionFirExtensionRegistrar` (`FirExtensionRegistrar`): wires up `WithCompanionGenerator` via `+::WithCompanionGenerator`.
- `WithCompanionGenerator` (`FirDeclarationGenerationExtension`):
  - Registers `DeclarationPredicate.create { annotated(FqName("com.example.WithCompanion")) }`.
  - `getNestedClassifiersNames(...)` returns `SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT` for marker-annotated classes.
  - `generateNestedClassLikeDeclaration(...)` synthesises the companion via `createCompanionObject(owner, key)`.
  - `getCallableNamesForClass(...)` on the synthesised companion returns `{INIT, greet}` (matched by checking origin is `FirDeclarationOrigin.Plugin` with our key).
  - `generateConstructors(...)` returns `createDefaultPrivateConstructor(...)` for the companion.
  - `generateFunctions(...)` returns `createMemberFunction(owner, key, "greet", returnType = String)`.
- `WithCompanionGeneratedDeclarationKey` (`GeneratedDeclarationKey`): bridges FIR `origin` to IR `IrDeclarationOrigin.GeneratedByPlugin.pluginKey`.
- `GreetIrGenerationExtension` (`IrGenerationExtension`): walks every `IrFunction` via `IrElementTransformerVoidWithContext`; when it sees a function whose `origin` is `IrDeclarationOrigin.GeneratedByPlugin` with `pluginKey == WithCompanionGeneratedDeclarationKey` and name `greet`, it sets `processed.body = builder.irBlockBody { +irReturn(irString("hello")) }`.

## Sample shape

- `annotation class WithCompanion`
- `@WithCompanion class Foo`
- `fun main() { println(Foo.greet()) }`

## Notes / lessons learned

1. **`pluginId` is abstract on `CompilerPluginRegistrar` in 2.3.21**: the build initially failed with "Class 'WithCompanionComponentRegistrar' is not abstract and does not implement abstract base class member: val pluginId: String". The 01-additional-checkers verification project still compiles only because it predates the change or relies on a different path; new plugins must override `pluginId`.
2. **The companion-object three-override unit** (as the SKILL.md highlights) is required: `getNestedClassifiersNames` + `generateNestedClassLikeDeclaration` plus `getCallableNamesForClass` returning `INIT` for the synthesised companion + `generateConstructors`. Without `INIT`, IR generation crashes because the companion has no constructor.
3. **`callableId.callableName`** matched against `Name.identifier("greet")` in `generateFunctions`; the same `Name` is used by the IR extension to filter the function whose body is filled, avoiding accidental rewrites of any other plugin-generated callable.
4. **The IR side recognises declarations purely by `IrDeclarationOrigin.GeneratedByPlugin.pluginKey`** — no need to look up the FIR symbol from IR. Same `GeneratedDeclarationKey` instance bridges both sides.
