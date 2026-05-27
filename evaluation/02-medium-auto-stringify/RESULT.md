# Evaluation Result: 02-medium-auto-stringify — 2026-05-27 (post-consolidation re-run)
**Skills version**: kotlin-compiler-plugin@0.1.1 (single-skill consolidated layout, commit 463287a on real repo)
**Kotlin version validated against**: 2.3.21

**Agent**: Claude Opus 4.7 (claude-opus-4-7)
**Gradle launcher**: 9.5.0 (Homebrew openjdk 21.0.10)

## Acceptance Criteria

| # | Criterion | Result | Notes |
|---|---|---|---|
| 1 | Plugin builds | PASS | `./gradlew :plugin:jar` BUILD SUCCESSFUL on first compile after one tiny import fix (see Notes). |
| 2 | Sample compiles | PASS | `./gradlew :sample:compileKotlin` succeeds; the plugin loads via `-Xplugin=` and synthesises the member. |
| 3 | `Person(...).toAutoString()` resolves at compile time | PASS | The sample call site compiles without a cast. |
| 4 | Synthesised method appears in IR | PASS | `javap -p .../Person.class` shows `public final java.lang.String toAutoString();`. |
| 5 | Output for Person | PASS | `:sample:run` printed `Person(name=Alice, age=30)` (grep -F confirmed). |
| 6 | Output for Box | PASS | Same run printed `Box(width=10, height=20, opaque=true)`. |
| 7 | Untagged class lacks the method | PASS | Uncommented `Untagged("x").toAutoString()` then ran `:sample:compileKotlin`: failed with `Unresolved reference 'toAutoString'`. Reverted after confirming. |
| 8 | Method not added to unannotated classes | PASS | `javap -p .../Untagged.class` shows only `getFoo()` and the constructor — no `toAutoString`. |
| 9 | Method visibility is `public` | PASS | `javap` line is `public final java.lang.String toAutoString();`. |
| 10 | No `IrValidation` errors | PASS | `grep -c 'IrValidation:' /tmp/02-out.txt` = 0 over the full `:sample:run --rerun-tasks` log. |

## Score: 10 / 10 (100%)

## Implementation notes

Plugin source files (≈ 130 lines of Kotlin, exclusive of `META-INF/services`):

- `plugin/src/main/kotlin/com/example/autostringify/AutoStringifyNames.kt` — `PLUGIN_ID`, annotation `FqName`, `toAutoString` `Name`, and the `GeneratedDeclarationKey`.
- `AutoStringifyFirDeclarationGenerator.kt` — `FirDeclarationGenerationExtension`. Predicate `annotated(AutoStringify)`, `getCallableNamesForClass` returns `{toAutoString}` only when matched, `generateFunctions` uses `createMemberFunction` with `returnType = session.builtinTypes.stringType.coneType`.
- `AutoStringifyFirExtensionRegistrar.kt` — `+::AutoStringifyFirDeclarationGenerator`.
- `AutoStringifyIrGenerationExtension.kt` — `IrElementTransformerVoidWithContext.visitFunctionNew`. Identifies the synthetic via `origin is IrDeclarationOrigin.GeneratedByPlugin && origin.pluginKey == AutoStringifyGeneratedDeclarationKey`. Fills the body with `irBlockBody { +irReturn(irConcat().apply { addArgument(...) }) }`, walking `parentClass.declarations.filterIsInstance<IrProperty>()` in declaration order and emitting `<ClassName>(name=...value..., ...)`. Property values are read via `irCall(property.getter!!.symbol).apply { arguments[0] = irGet(processed.dispatchReceiverParameter!!) }` — the unified-arguments form required by KT-68003 (Kotlin 2.2+).
- `AutoStringifyCompilerPluginRegistrar.kt` — registers both `FirExtensionRegistrarAdapter` and `IrGenerationExtension`. Required `override val pluginId` for Kotlin 2.3+.
- `AutoStringifyCommandLineProcessor.kt` — minimal (`pluginOptions = emptyList()`).
- `META-INF/services/...CompilerPluginRegistrar` and `...CommandLineProcessor` — both files registered.

The first compile failed with one error — `Unresolved reference 'transformChildrenVoid'`. Added `import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid` and the rest built cleanly. No IR-validation regression, no opt-in warning. Total iteration: one round of edits.

## Skill effectiveness assessment

The consolidated `kotlin-compiler-plugin` skill was sufficient to complete this task end-to-end. Concretely:

- The router `SKILL.md` made it immediate which sub-guides to load for FIR generation + IR body fill (the topic index points directly at the six guides this task needs).
- `compiler-plugin-bootstrap/guide.md` gave the full Gradle layout, both `META-INF/services` files, the `compileOnly` warning, the `pluginId` requirement, and the `-Xplugin=` wiring. The example dir provided gradle wrapper + working build files to copy verbatim.
- `fir-declaration-generation-extension/guide.md` showed the exact `createMemberFunction` signature, the `getCallableNamesForClass`/`generateFunctions` pair contract, and emphasised that the discovery method must list the name or generation is silently skipped. The `GeneratedDeclarationKey` companion pattern was demonstrated.
- `ir-body-modification/guide.md` was the most load-bearing guide for this task. It documented:
  - The `origin is IrDeclarationOrigin.GeneratedByPlugin && origin.pluginKey == ...` discriminator for matching the FIR-synthesised function.
  - The `irConcat()` + `addArgument()` pattern, including the non-obvious import of `addArgument` from `org.jetbrains.kotlin.ir.expressions` (not `ir.builders`).
  - The `arguments[0] = irGet(dispatchReceiverParameter!!)` form for getter calls (KT-68003).
  - `parentAsClass` for navigating to the enclosing class and iterating `IrProperty` declarations.

## Skill-doc gaps (minor)

1. **`transformChildrenVoid` import was not surfaced.** The `ir-body-modification` guide's "prepend a `println`" example calls `moduleFragment.transformChildrenVoid(...)` but the imports listed above the snippet don't include it. I had to discover `import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid` myself (the file's first build failed on this). Suggestion: add that import to the example's import block alongside the other `org.jetbrains.kotlin.ir.builders.*` imports.

2. **No worked example of declaration-order property iteration.** The task needed properties "in declaration order, covering every constructor-property". The IR body guide mentions `irClass.declarations.filterIsInstance<IrProperty>()` in a single bullet, but doesn't explicitly note that this list mirrors the declaration order (which it does), nor that synthesised properties from data-class/serialization would also be included. For this benchmark that was fine; for real plugins authors may want a one-liner reassurance.

3. **`irConcat()` documentation could clarify what `addArgument` of a non-string IrExpression does.** The guide states `irConcat()` produces `IrStringConcatenation` and that `addArgument` is from `ir.expressions`, but it didn't spell out that adding an `IrCall` whose return type is `Int`/`Boolean`/etc. is fine — Kotlin's string template machinery handles `.toString()` conversion on each fragment at codegen. I had to take this on faith from the kotlinx-serialization analogy; the runtime output `Box(width=10, height=20, opaque=true)` confirmed it works for `Int` and `Boolean`. Suggestion: a single sentence "non-`String` arguments are converted via their `toString()` at codegen, so you can `addArgument(irCall(property.getter!!.symbol)...)` regardless of the property's static type" would have saved a moment of doubt.

None of these gaps blocked completion; they are polish items.

## Files of interest

- `/tmp/kotlin-skill-eval-02-medium-auto-stringify-20260527-204058/work/plugin/src/main/kotlin/com/example/autostringify/AutoStringifyIrGenerationExtension.kt`
- `/tmp/kotlin-skill-eval-02-medium-auto-stringify-20260527-204058/work/plugin/src/main/kotlin/com/example/autostringify/AutoStringifyFirDeclarationGenerator.kt`
- `/tmp/kotlin-skill-eval-02-medium-auto-stringify-20260527-204058/work/sample/src/main/kotlin/com/example/app/Main.kt`
- `/tmp/02-out.txt` — captured `:sample:run --rerun-tasks` output
