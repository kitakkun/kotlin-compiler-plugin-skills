---
name: fir-scripting-extensions
description: Customise how Kotlin script files (`.kts` and custom script dialects) are compiled by registering one or more of three FIR/Fir2Ir extensions — `FirScriptConfiguratorExtension` (decide which sources are scripts and configure the `FirScript`/`FirFile` AST at raw-FIR time), `FirScriptResolutionConfigurationExtension` (inject default imports visible to the script body), and `Fir2IrScriptConfiguratorExtension` (mutate the generated `IrScript` after FIR-to-IR conversion). These extensions are how the `kotlin-scripting-compiler` plugin implements `.kts`, `.main.kts`, Gradle script kernels, and Jupyter cells. Read fir-extensions-overview first. NOT for ordinary `.kt` source — see [`fir-declaration-generation-extension`](../fir-declaration-generation-extension/guide.md) for that. NOT for REPL snippets — see [`fir-repl-snippet-extensions`](../fir-repl-snippet-extensions/guide.md).
---

# FIR Scripting Extensions

Kotlin compiles three kinds of source: ordinary `.kt` files, **script files** (`.kts`, `.main.kts`, Gradle build scripts), and **REPL snippets**. The three extensions in this skill control the script path. They are how JetBrains' own scripting infrastructure (`kotlin-scripting-compiler`) builds dialects like `.gradle.kts` and `.main.kts`; standalone plugins use them to define new script dialects.

If you only need to add ordinary classes / functions visible to source code, this skill is the wrong one — use [`fir-declaration-generation-extension`](../fir-declaration-generation-extension/guide.md). If you're targeting the IDE-driven REPL or Jupyter-style snippet evaluation, use [`fir-repl-snippet-extensions`](../fir-repl-snippet-extensions/guide.md).

## Prerequisite: register a `KotlinScriptDefinition` for new dialects

These extensions configure scripts the compiler **already recognises as scripts**. For a brand-new dialect like `.my.kts`, the compiler doesn't see your sources as scripts until you register a `KotlinScriptDefinition` (or `ScriptDefinitionTemplate`) via the scripting SPI. Without that registration step, `FirScriptConfiguratorExtension.accepts(...)` is never called — the source is parsed as an ordinary `.kt` file and your extensions never fire.

The registration paths:

- **In-process compilation**: declare a `@KotlinScript`-annotated template class and ship a service file `META-INF/kotlin/script/templates/<your.template.Fqn>` next to it. The Kotlin compiler scans the classpath for these at startup.
- **Gradle integration**: depend on `org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable` (or `kotlin-scripting-compiler` for the un-shaded compiler), provide the template, and set `-script-templates=<your.template.Fqn>` via `freeCompilerArgs` (note: no `-X` prefix — the CLI flag is `K2JVMCompilerArguments.scriptTemplates`, declared as `-script-templates`). The Kotlin Gradle plugin also exposes `kotlin.script.compilation.configuration` keys for richer dialects.
- **Defaulting the extension**: `-Xdefault-script-extension=.my.kts` tells the compiler your dialect's file extension; it still requires the template registration.

Once the template is wired, `accepts(...)` is invoked for every script source the compiler considers — that's when this skill's extensions apply.

**Cooperation seam caveat.** The default `kotlin-scripting-compiler` plugin's own `FirScriptConfiguratorExtensionImpl` uses `accepts(sourceFile != null) = true` as a catch-all and configures any source it touches. Custom `FirScriptConfiguratorExtension`s **do** still get their `accepts(...)` queried in parallel (the resolver picks the first matching configurator), but for **implicit receivers** specifically there is a simpler, more reliable path: declare them in your `@KotlinScript`-annotated template's `ScriptCompilationConfiguration.implicitReceivers(...)` block rather than mutating the `FirScript` builder from your extension. The scripting infrastructure reads those receivers during the configuration phase and wires them into the script's `FirScript.receivers` list before resolution. Reach for `FirScriptConfiguratorExtension` only when you need to mutate other parts of the AST (synthetic statements, parameters, file-level imports) that aren't exposed by `ScriptCompilationConfiguration`.

This prerequisite was the load-bearing missing piece in earlier drafts of this skill. The extension APIs themselves are reachable only after `KotlinScriptDefinition` registration completes.

## The three extensions

| Extension | Where it runs | What it does |
|---|---|---|
| `FirScriptConfiguratorExtension` | Raw-FIR builder (very early, before resolution) | Decide if a given source file is a script your dialect recognizes (`accepts(...)`), then mutate the `FirScript` / `FirFile` builder (add implicit imports, receivers, parameters, initialiser blocks) |
| `FirScriptResolutionConfigurationExtension` | Resolve phase | Return additional default imports visible to the script body |
| `Fir2IrScriptConfiguratorExtension` | FIR → IR conversion | Mutate the generated `IrScript` (rename, reorganise statements, register extra dispatch info for the IR backend) |

All three live under `org.jetbrains.kotlin.fir.builder`, `org.jetbrains.kotlin.fir.extensions`, and `org.jetbrains.kotlin.fir.backend` respectively. All three are registered through `FirExtensionRegistrar.configurePlugin()` using `+::` constructor references in the usual way.

## 1. `FirScriptConfiguratorExtension`

```kotlin
package com.example.myscript.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.KtSourceFile
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.builder.Context
import org.jetbrains.kotlin.fir.builder.FirScriptConfiguratorExtension
import org.jetbrains.kotlin.fir.declarations.builder.FirFileBuilder
import org.jetbrains.kotlin.fir.declarations.builder.FirScriptBuilder

class MyScriptConfigurator(session: FirSession) : FirScriptConfiguratorExtension(session) {

    override fun accepts(sourceFile: KtSourceFile?, scriptSource: KtSourceElement): Boolean =
        // Decide which sources are "yours". The official scripting impl simply checks
        // `sourceFile != null`; for a custom dialect you'd typically pattern-match the name.
        sourceFile?.name?.endsWith(".my.kts") == true

    override fun FirScriptBuilder.configureContainingFile(fileBuilder: FirFileBuilder) {
        // Mutate the surrounding FirFile builder if your dialect needs file-level changes
        // (extra package directives, file annotations, etc.). Most dialects leave this empty.
    }

    override fun FirScriptBuilder.configure(sourceFile: KtSourceFile?, context: Context<*>) {
        // Mutate the FirScript itself — add implicit parameters, receivers, initialiser blocks
        // that should appear before the user's code. The builder's mutable lists at v2.3.21:
        //   - parameters: MutableList<FirProperty>             (script-as-top-level-fn parameters)
        //   - receivers : MutableList<FirScriptReceiverParameter>  (implicit `this` receivers)
        //   - declarations: MutableList<FirDeclaration>        (script body declarations)
        // Build entries with `buildProperty { ... }` / `buildScriptReceiverParameter { ... }`
        // from `org.jetbrains.kotlin.fir.declarations.builder`.
    }
}
```

`accepts(...)` is called for every source; return early when it isn't yours. Inside `configure(...)`, you have full mutating access to the `FirScriptBuilder` — `declarations`, `parameters`, `receivers`, `source` are all on it.

### `accepts(...)` is the cooperation seam

Multiple `FirScriptConfiguratorExtension`s can be registered in the same session (the scripting plugin always registers at least one, then any plugin can add more). The first `accepts(...)` returning `true` wins for a given source; design `accepts(...)` narrowly so your plugin doesn't capture sources another extension expects.

The reference implementation (`FirScriptConfigurationExtensionImpl` in `plugins/scripting/scripting-compiler/`) uses `accepts(sourceFile, _) = sourceFile != null`, which is a catch-all — it intentionally claims every script source because the scripting infrastructure is the default. Your custom dialect must `accepts(...)` more restrictively.

## 2. `FirScriptResolutionConfigurationExtension`

```kotlin
package com.example.myscript.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirImport
import org.jetbrains.kotlin.fir.declarations.FirScript
import org.jetbrains.kotlin.fir.declarations.builder.buildImport
import org.jetbrains.kotlin.fir.extensions.FirScriptResolutionConfigurationExtension
import org.jetbrains.kotlin.name.FqName

class MyScriptImportInjector(session: FirSession) : FirScriptResolutionConfigurationExtension(session) {
    override fun getScriptDefaultImports(script: FirScript): List<FirImport>? {
        if (!script.isMine()) return null   // null = "I have nothing to add for this script"
        return listOf(
            buildImport {
                importedFqName = FqName("com.example.myscript.dsl")
                isAllUnder = true   // import com.example.myscript.dsl.*
            },
            buildImport {
                importedFqName = FqName("kotlinx.coroutines.runBlocking")
                isAllUnder = false  // single-symbol import
            },
        )
    }

    private fun FirScript.isMine(): Boolean =
        // typically based on the script's source-file name, definition annotation, or
        // a marker your `FirScriptConfiguratorExtension` left on the FirScript above
        true
}
```

These imports are added to the resolution scope of the script body **without appearing in the source**. The user can write `runBlocking { ... }` without an explicit `import kotlinx.coroutines.runBlocking`. Return `null` (not `emptyList()`) to defer to other extensions; `emptyList()` declares "I considered this script and want no extra imports", which may suppress other extensions' contributions depending on the merge policy.

## 3. `Fir2IrScriptConfiguratorExtension`

```kotlin
package com.example.myscript.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.backend.Fir2IrScriptConfiguratorExtension
import org.jetbrains.kotlin.fir.declarations.FirScript
import org.jetbrains.kotlin.fir.symbols.impl.FirScriptSymbol
import org.jetbrains.kotlin.ir.declarations.IrScript
import org.jetbrains.kotlin.ir.symbols.IrScriptSymbol

class MyScriptIrConfigurator(session: FirSession) : Fir2IrScriptConfiguratorExtension(session) {
    override fun IrScript.configure(
        script: FirScript,
        getIrScriptByFirSymbol: (FirScriptSymbol) -> IrScriptSymbol?,
    ) {
        // The receiver `this: IrScript` is the freshly-generated IR. You may:
        //   - rename `this.name` for class-file shape control
        //   - reorder `this.statements`
        //   - mark certain `IrFunction`s / `IrProperty`s as visible-to-other-snippets
        //     by looking up other scripts via `getIrScriptByFirSymbol(otherFirScriptSymbol)`
        // Use the provided `getIrScriptByFirSymbol` lookup to address cross-script
        // dispatch — never cache IrScriptSymbol references between sessions.
    }
}
```

Use this when you need to alter the *generated bytecode shape* of the script — e.g., to make Gradle-style multi-script projects link to each other, or to inline a custom main function around the script body. Most dialects don't need it.

## Wiring up

```kotlin
package com.example.myscript

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import com.example.myscript.fir.MyScriptConfigurator
import com.example.myscript.fir.MyScriptImportInjector
import com.example.myscript.fir.MyScriptIrConfigurator

class MyScriptFirRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::MyScriptConfigurator
        +::MyScriptImportInjector
        +::MyScriptIrConfigurator
    }
}
```

The `+::` DSL has overloads for each extension type (via `unaryPlus` on `(FirSession) -> Ext`). Register only the extensions you implement.

## What this skill enables you to build

- A custom `.kts`-style file format with its own implicit receiver, parameter, or initialisation block (the way `build.gradle.kts` makes the `Project` object the implicit receiver of the script body).
- A dialect that auto-imports a curated set of DSL functions and types without forcing every script to repeat the imports.
- A bytecode-level adjustment for cross-script visibility (the way Gradle's project / settings scripts can reference each other).

You typically combine all three extensions in one plugin: `FirScriptConfiguratorExtension` declares "this file is my dialect" and adds the receiver shape; `FirScriptResolutionConfigurationExtension` adds the default imports; `Fir2IrScriptConfiguratorExtension` adjusts the IR for the runtime model.

## Common gotchas

### `accepts(...)` not narrow enough

If your `accepts(...)` returns `true` for every source the resolver passes you, you steal scripts the platform scripting plugin expected. The user sees diagnostics like "the script source does not match any known definition" or, worse, an empty `FirScript` that compiles to a dead-on-arrival `.class`. Always start `accepts(...)` with a hard filter — typically `sourceFile?.name?.endsWith(<your suffix>) == true`.

### `getScriptDefaultImports(...)` running on the wrong scripts

The extension is called for *every* script in the session, including scripts your dialect doesn't recognize. Mirror your `accepts(...)` filter in `getScriptDefaultImports(...)` and `IrScript.configure(...)` so you don't accidentally rewrite another dialect's scripts.

### Forgetting to register one of the three

If your `FirScriptConfiguratorExtension` adds an implicit `Project` receiver but your `FirScriptResolutionConfigurationExtension` doesn't import `org.gradle.api.Project`, code that uses the receiver fails resolution. The three extensions cover overlapping concerns; treat them as a triple that must agree on the script's runtime shape.

### Conflating `FirScriptResolutionConfigurationExtension` with `FirDeclarationGenerationExtension`

`getScriptDefaultImports(...)` adds **imports only** — it does not add declarations. To synthesise a class, function, or property *visible to the script body*, register a `FirDeclarationGenerationExtension` (see [`fir-declaration-generation-extension`](../fir-declaration-generation-extension/guide.md)). The two often appear together: declaration-generation creates the symbol; resolution-config imports it.

### `Fir2IrScriptConfiguratorExtension` mutating after backend phases run

`IrScript.configure(...)` is called *during* FIR→IR conversion. Reading `this` is fine; mutating `this.statements` is fine. **Don't** cache the `IrScript` reference and mutate it later — the backend phases that follow assume the IR shape is stable after Fir2Ir completes.

### `FirScript.name` is a synthetic bracketed `Name` — use `asString()`, not `identifier`

`FirScript.name` is constructed by the raw-FIR builder as `Name.special("<script-<filename>>")` (e.g. `<script-hello.my.kts>`). Calling `.identifier` on it throws `IllegalStateException: not identifier` because special-bracketed names refuse that accessor. Use `name.asString()` if you need a printable form, or compare `name == SpecialNames.NO_NAME_PROVIDED` etc. against the constants in `org.jetbrains.kotlin.name.SpecialNames`.

### `ConeClassLikeTypeImpl` / `ConeClassLikeLookupTagImpl` are not public

If you need a `ConeKotlinType` for a receiver / parameter type inside `configure(...)`, the public path at v2.3.21 is:

```kotlin
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.ClassId

val receiverTypeRef = buildResolvedTypeRef {
    coneType = ClassId.fromString("com/example/ScriptCtx").constructClassLikeType()
}
```

The `Impl`-suffixed classes (`ConeClassLikeTypeImpl`, `ConeClassLikeLookupTagImpl`) live in non-public packages and are not stable API for plugin authors. Stick to `ClassId.constructClassLikeType(...)` and the `buildResolvedTypeRef { coneType = ... }` DSL.

## Reference implementation

The authoritative live reference is `kotlin/plugins/scripting/scripting-compiler/src/.../services/`:

- `FirScriptConfigurationExtensionImpl.kt` — `FirScriptConfiguratorExtension` impl, ~500 lines, handles `.kts`, `.main.kts`, Gradle scripts, and the host-configuration glue.
- `FirScriptResolutionConfigurationExtensionImpl.kt` — `FirScriptResolutionConfigurationExtension` impl, reads from the host's compilation configuration.
- `Fir2IrScriptConfiguratorExtensionImpl.kt` — `Fir2IrScriptConfiguratorExtension` impl, propagates the script's import/receiver shape into IR.

Reading these in order is the fastest way to understand the API in practice. They are Apache-2.0 licensed; see `NOTICE.md` for attribution.

## Relation to other skills

- **Overview first** → [`fir-extensions-overview`](../fir-extensions-overview/guide.md)
- **REPL snippets, not scripts** → [`fir-repl-snippet-extensions`](../fir-repl-snippet-extensions/guide.md)
- **Adding ordinary declarations to source** → [`fir-declaration-generation-extension`](../fir-declaration-generation-extension/guide.md)
- **Custom diagnostics on script bodies** → [`fir-additional-checkers-extension`](../fir-additional-checkers-extension/guide.md)
- **Mutating the generated bytecode beyond what `IrScript.configure` allows** → [`ir-body-modification`](../ir-body-modification/guide.md) or [`ir-call-rewriting`](../ir-call-rewriting/guide.md)

## What this skill does NOT cover

- Defining a `KotlinScriptDefinition` and registering it with `kotlin-scripting-compiler` (that's an SPI concern, not a compiler-plugin concern; see Kotlin's scripting documentation).
- Hosting the compiler inside an application to evaluate scripts at runtime (use `kotlin-scripting-jvm` / `BasicJvmScriptingHost` directly).
- REPL evaluation state and snippet ordering — [`fir-repl-snippet-extensions`](../fir-repl-snippet-extensions/guide.md) covers that.
