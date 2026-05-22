# Evaluation Result: 02-medium-auto-stringify — 2026-04-30

**Skills version**: HEAD of `main` at evaluation time
**Kotlin version validated against**: 2.3.20

## Final Score: 100 / 100

| Category | Score | Max |
|---|---|---|
| Functionality | 60 | 60 |
| Code Quality | 20 | 20 |
| Skill Adherence | 20 | 20 |

## Functionality breakdown

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Plugin builds (`./gradlew :plugin:jar`) | PASS | `BUILD SUCCESSFUL in 445ms`, `:plugin:jar` task succeeded. |
| 2 | Sample compiles (`./gradlew :sample:compileKotlin`) | PASS | `:sample:compileKotlin` succeeded as part of `:sample:run` (`BUILD SUCCESSFUL in 7s`). |
| 3 | `Person(...).toAutoString()` resolves at compile time | PASS | Sample compiles with the call present at `sample/src/main/kotlin/com/example/app/Main.kt:12`. |
| 4 | Synthesised method appears in IR | PASS | `javap -p Person.class` shows `public final java.lang.String toAutoString();`. |
| 5 | Output for Person | PASS | `grep -F 'Person(name=Alice, age=30)' /tmp/02-out.txt` matched (line 15). |
| 6 | Output for Box | PASS | `grep -F 'Box(width=10, height=20, opaque=true)' /tmp/02-out.txt` matched (line 16). |
| 7 | Untagged class lacks the method | PASS | After temporarily replacing the commented line with `println(Untagged("x").toAutoString())`, build failed with `e: ...Main.kt:14:27 Unresolved reference 'toAutoString'.`; Main.kt restored. |
| 8 | Method not added to unannotated classes | PASS | `javap -p Untagged.class` shows only `<init>` and `getFoo()` — no `toAutoString`. |
| 9 | Method visibility is `public` | PASS | `javap` shows `public final java.lang.String toAutoString();` on Person.class and Box.class. |
| 10 | No `IrValidation:` errors | PASS | `grep -c 'IrValidation:' /tmp/02-out.txt` returned `0`. |

10/10 PASS → `(10/10) × 60 = 60`.

## Code Quality breakdown

| Sub-axis | Score | Notes |
|---|---|---|
| File organization | 5 | Clean package layout: `com.example.autostringify` (registrar/CLI/names) with `fir/` and `ir/` sub-packages. Plugin/sample modules separated per the bootstrap convention. `META-INF/services/` contains both `CompilerPluginRegistrar` and `CommandLineProcessor` registrations. |
| Idiomatic Kotlin | 5 | `object AutoStringifyPluginNames` (constants), `object AutoStringifyGeneratedDeclarationKey`. `companion object` holds the predicate. No Java-style getters/setters; nullability used only where needed (`getter` null-check). Named arguments used in `createMemberFunction`. |
| Readability | 5 | Names are descriptive (`AutoStringifyDeclarationGenerator`, `AutoStringifyBodyFiller`, `AUTO_STRINGIFY_PREDICATE`, `TO_AUTO_STRING_NAME`); no `tmp`/`xx`/`data1`. No commented-out code blocks (the single comment in Main.kt is the SPEC-mandated hint). |
| No anti-patterns | 5 | `grep -rn 'Thread.sleep|@Suppress("ALL")|catch (e: Exception) {}'` returns no hits across the plugin sources. No copy-pasted long blocks. |

## Skill Adherence breakdown

| Sub-axis | Score | Notes |
|---|---|---|
| Recommended patterns | 5 | `AutoStringifyPluginNames.PLUGIN_ID` shared (`AutoStringifyPluginNames.kt:8`); `compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.3.20")` in `plugin/build.gradle.kts:10`; `@OptIn(ExperimentalCompilerApi::class)` on the registrar (`AutoStringifyComponentRegistrar.kt:11`) and CLI processor (`AutoStringifyCommandLineProcessor.kt:7`); `supportsK2 = true` (`AutoStringifyComponentRegistrar.kt:14`); `GeneratedDeclarationKey` is shared between FIR (passed to `createMemberFunction`) and IR (origin filter). |
| Modern APIs | 5 | Uses `IrElementTransformerVoidWithContext` (`AutoStringifyIrGenerationExtension.kt:33`). DSL builders only (`irConcat`, `irBlockBody`, `irReturn`, `irString`, `irCall`, `irGet` from `org.jetbrains.kotlin.ir.builders`). `session.builtinTypes.stringType.coneType` for the return type — no deprecated `referenceClass`. `predicateBasedProvider.matches(PREDICATE, classSymbol)` for predicate evaluation. |
| No invented/deprecated APIs | 5 | No `createParameterDeclarations`, no `dispatchReceiver = ...` setter, no `referenceClass`/`referenceFunctions`, no `getPluginArtifactForNative`, no `registerClassAsMetadataVisible`, no `KtDiagnosticFactoryToRendererMap` direct constructor. Property access uses `arguments[0] = irGet(processed.dispatchReceiverParameter!!)` (`AutoStringifyIrGenerationExtension.kt:55`) per KT-68003 unified arguments. `@file:OptIn(UnsafeDuringIrConstructionAPI::class)` correctly declared at line 1. |
| Correct API forms | 5 | Predicate registered on `FirDeclarationPredicateRegistrar.registerPredicates()` of the extension (`AutoStringifyDeclarationGenerator.kt:24-26`), not on the registrar. `createMemberFunction(owner, key, name, returnType)` invoked with named args (`AutoStringifyDeclarationGenerator.kt:47-52`). `IrStringConcatenation` built via `irConcat()` and `addArgument(...)`. Origin filter checks `IrDeclarationOrigin.GeneratedByPlugin` then `origin.pluginKey == AutoStringifyGeneratedDeclarationKey` (lines 38-39). `getCallableNamesForClass` returns `setOf(TO_AUTO_STRING_NAME)` only when the predicate matches (lines 31-37). |

## Anti-cheat findings

None of the SPEC's "Common failure modes" triggered:

1. `getCallableNamesForClass` IS implemented and predicate-gated (`AutoStringifyDeclarationGenerator.kt:31-37`). PASS.
2. `GeneratedDeclarationKey` IS used and matched on the IR side via `IrDeclarationOrigin.GeneratedByPlugin.pluginKey` (`AutoStringifyIrGenerationExtension.kt:38-39`). PASS.
3. `createMemberFunction(...)` IS used (`AutoStringifyDeclarationGenerator.kt:47`). PASS.
4. IR body IS filled via `irBlockBody { ... +irReturn(concat) }` (`AutoStringifyIrGenerationExtension.kt:46-62`). PASS.
5. Property values are read via property getter call (`irCall(getter.symbol)`), not raw `IrField` access. PASS.
6. `IrStringConcatenation` produced via `irConcat()` plus `addArgument(...)`, not `+` chaining. PASS.
7. `arguments[0] = irGet(dispatchReceiverParameter)` used, not deprecated `dispatchReceiver = ...` setter. PASS.
8. `@file:OptIn(UnsafeDuringIrConstructionAPI::class)` declared at `AutoStringifyIrGenerationExtension.kt:1`. PASS.

## Overall assessment

A reference-quality submission. All 10 acceptance criteria pass, runtime output matches exactly, no IR validation errors. The implementation closely follows the documented patterns in `fir-declaration-generation-extension`, `ir-body-modification`, and `ir-plugincontext-usage` — including the unified-arguments form (`arguments[0] = irGet(...)`) and the `GeneratedByPlugin` origin check.

## Suggested skill fixes

None — no failures occurred. The agent's implementation matches the skill recommendations one-to-one.
