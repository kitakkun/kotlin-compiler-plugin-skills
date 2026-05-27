# Evaluation Result: 06-extreme-json-serialize — 2026-05-27 (post-consolidation re-run)
**Skills version**: kotlin-compiler-plugin@0.1.1 (single-skill consolidated layout, commit 463287a on real repo)
**Kotlin version validated against**: 2.3.21

## Acceptance Criteria

| # | Criterion | Result | Notes |
|---|---|---|---|
| 1 | Plugin builds | ✅ | `./gradlew :plugin:jar` succeeded after iterating on imports / API shape |
| 2 | `module-A` compiles | ✅ | clean build succeeded |
| 3 | `User.class` declares `toJson()` | ✅ | `javap` shows `public final java.lang.String toJson()` |
| 4 | `User$Companion.class` declares `parse(String)` | ✅ | `javap` shows `public final com.example.model.User parse(java.lang.String)` |
| 5 | `module-B` compiles | ✅ | metadata visibility round-trip works — module-B sees `User.parse` from compiled metadata |
| 6 | module-B run prints expected user JSON | ✅ | output line `{"user_name":"Alice","age":30,"active":true}` matches exactly (passwordHash omitted, name renamed) |
| 7 | `User.parse` round-trip succeeds | ✅ | output `parsed ok: Alice` |
| 8 | Order serialization handles nested + list | ✅ | output `{"id":7,"items":[{"sku":"SKU-1","qty":2},{"sku":"SKU-2","qty":1}],"customer":{...},"paid_amount":99.95}` |
| 8b | `Order.parse` round-trip succeeds | ✅ | output `order ok: SKU-1` (balanced-brace nested parsing + `},{` split for list elements work) |
| 9 | `JSON_REQUIRED_WITH_DEFAULT` fires | ✅ | verified on temp `class Bad(@JsonRequired val email: String = "anon")` |
| 10 | `JSON_REQUIRED_ON_NULLABLE` fires | ✅ | verified on temp `class Bad(@JsonRequired val email: String?)` |
| 11 | No diagnostic on valid `@JsonRequired val email: String` | ✅ | verified — compiled clean |
| 12 | `JSON_IGNORE_AND_REQUIRED_CONFLICT` fires | ✅ | verified on `@JsonIgnore @JsonRequired val email: String` |
| 13 | `JSON_RENAME_EMPTY` fires | ✅ | verified on `@JsonRename("") val email: String` |
| 14 | `module-A` source cannot resolve `parse()` | ✅ | adding `_probe.kt` calling `User.parse("")` then `--rerun-tasks` yields `[UNRESOLVED_REFERENCE] Unresolved reference 'parse'` |
| 15 | Companion synthesis: absent → created, existing → preserved | ✅ | `User` (no source companion) got a synthesised `Companion` with `parse`; `WithCompanion` (existing companion with `MARKER`) kept its `MARKER` field AND received `parse` |
| 16 | Diagnostic factory name rendered | ✅ | with `-Xrender-internal-diagnostic-names`, output contains `[JSON_REQUIRED_WITH_DEFAULT]`, `[JSON_REQUIRED_ON_NULLABLE]`, `[JSON_IGNORE_AND_REQUIRED_CONFLICT]`, `[JSON_RENAME_EMPTY]` |
| 17 *(bonus)* | `parse` returns null on malformed JSON | ✅ | `User.parse("{not json}")` returns null at runtime — output `malformed handled: parse returned null` |
| 18 *(bonus)* | Cross-module nesting compiles and runs | ✅ partial | `module-B/NestedDemo` nests `User` from module-A; `toJson()` works end-to-end. `NestedDemo.parse()` from module-B source is unresolvable (same-module IR-only constraint — expected per design) so the round-trip can't be tested in the same source set |

## Score: 18 / 18 mandatory criteria PASS (criteria 1–16 including 8b). 2 / 2 bonus criteria PASS (with the noted same-module nuance on 18).

## What was built

- **Plugin** (`plugin/`): single Gradle module, ~750 LoC of Kotlin across:
  - `JsonPluginNames.kt` — shared FQN/CallableId/ClassId constants.
  - `JsonComponentRegistrar.kt` + `JsonCommandLineProcessor.kt` + META-INF services — standard bootstrap.
  - `fir/JsonDeclarationGenerator.kt` — synthesises `toJson()` signature and a companion object (only when absent).
  - `fir/JsonAdditionalCheckers.kt` + `fir/JsonChecker.kt` + `fir/JsonDiagnostics.kt` — four diagnostics + factory→message renderer + registrar.
  - `fir/JsonFirExtensionRegistrar.kt` — wires the two FIR extensions and registers the diagnostic container.
  - `ir/JsonIrGenerationExtension.kt` — top-level IR pass: walk classes annotated `@JsonSerialize`, generate IR-only `parse` and register as metadata-visible, then fill the FIR-declared `toJson` bodies.
  - `ir/JsonIrHelpers.kt` — symbol lookup cache (StringBuilder, ArrayList ctors, list element type extraction).
  - `ir/ToJsonBodyBuilder.kt` — builds `toJson` body via `StringBuilder.append` loops including a while-based list emitter that handles primitive AND nested-`@JsonSerialize` lists.
  - `ir/ParseBodyBuilder.kt` — builds `parse` body inside `try { … } catch (e: Throwable) { null }`. Per-property extraction via `String.substringAfter("\"key\":", json)`. Per type: stripped-quote string, primitive-token extraction via repeated `substringBefore`+`trim`+`toX()`, balanced-brace walker for nested objects, `"},{"` split for nested-`@JsonSerialize` lists with brace re-wrapping via `irConcat`.
- **module-A**: declares the four annotations and `User`/`LineItem`/`Order` + a `WithCompanion` class used for criterion 15.
- **module-B**: depends on module-A; `Main.kt` exercises `User.toJson`, `User.parse`, `Order.parse` round-trip, runtime null on malformed input, and `NestedDemo` cross-module nesting.

## Notable implementation choices

- **Constructor-param annotation discovery** — in Kotlin 2.3 a bare annotation on a `val` constructor parameter lands on the `FirValueParameter` (and `IrValueParameter`) only, not the `FirProperty` / `IrProperty`, with a deprecation warning that this default will change later. Both the checker and the IR body builders look at the property AND the constructor parameter for `@JsonRename` / `@JsonIgnore` / `@JsonRequired`. Without this fallback, the first build silently emitted `"name":"Alice","passwordHash":"secret"` instead of `"user_name":"Alice"` (omitting `passwordHash`).
- **IR-only `parse` generation order** — `JsonIrGenerationExtension.generate` runs pass 1 (walk module, generate `parse` on every annotated class's companion and call `metadataDeclarationRegistrar.registerFunctionAsMetadataVisible`) before pass 2 (fill `toJson` bodies). Two-pass is needed because list-of-nested parsing in pass 2 looks up the nested class's `parse` symbol — that symbol must exist before any nested class's `parse` body is built.
- **Balanced-brace walker** — the nested-object parser walks `raw_b` char-by-char, tracking `depth` until the first balanced `}`, then `substring(start, end+1)`. Implemented with `IrWhileLoopImpl` + `IrSetValueImpl` + nested `IrWhenImpl` ifs because `irIfThenElse` plus break/continue are non-obvious from the IR builder DSL.
- **Plugin only ships generated members** — no runtime helper class. Every byte of parsing IR is inlined per generated `parse` body.
- **Existing companion preservation** — `JsonDeclarationGenerator.getNestedClassifiersNames` skips the companion-synthesis branch when `classSymbol.companionObjectSymbol != null`. The IR side just calls `target.companionObject()` so it attaches to whichever companion ended up there.

## Skill-doc gaps and friction observed

These are real friction points encountered during the run; each is a candidate edit to the consolidated skill docs.

1. **`fir-additional-checkers-extension/guide.md`** — does not flag the **Kotlin 2.3 `ANNOTATION_WILL_BE_APPLIED_ALSO_TO_PROPERTY_OR_FIELD` default-target migration**. A naive reading of "annotations on properties land on FirProperty" caused a multi-round bug (toJson missed `@JsonRename`, `@JsonIgnore`). Worth adding a paragraph in the "Looking up annotations" subsection: "for plugin annotations applied to constructor `val` parameters, also check the matching `FirValueParameter` / `IrValueParameter` until the default-target flip in a future Kotlin version. The same caveat applies to IR-side reads."

2. **`ir-plugincontext-usage/guide.md`** — covers `metadataDeclarationRegistrar.registerFunctionAsMetadataVisible(...)` but doesn't show the **idiomatic `IrFactory.buildFun` shape with an explicit DispatchReceiver parameter on a companion**. The skill's "anti-cheat" note about static-vs-member methods exists in `ir-synthetic-class-generation/guide.md` but not cross-linked from here. I had to construct the `IrValueParameter` for the companion `this` by hand (`pluginContext.irFactory.createValueParameter(... kind = IrParameterKind.DispatchReceiver ...)`) — a worked snippet under "Adding metadata to generated declarations" would save a round.

3. **`ir-body-modification/guide.md`** — the **DSL builders for `irIfThenElse`, `irWhile`, `irNot`** are not collectively shown. `irIfThenElse` works in some contexts but not others (the builder is missing on some `IrStatementsBuilder<*>` paths); I ended up using `IrWhenImpl` + `IrBranchImpl` directly with no compiler-symbol-side support. Worth adding a "Loops and conditionals" subsection that names the `IrWhileLoopImpl` / `IrWhenImpl` constructors and lists the package paths, since the builder DSL doesn't cover everything cleanly.

4. **`ir-body-modification/guide.md`** — **`irConcat()` and `addArgument(IrExpression)`** are mentioned in the cheat sheet but the `addArgument` extension lives in `org.jetbrains.kotlin.ir.expressions` (not `ir.builders`) and the cheat sheet's import comment is easy to miss. After one bad-import round my qualified `org.jetbrains.kotlin.ir.builders.irConcat()` reference failed to resolve because the function actually lives in `org.jetbrains.kotlin.ir.builders` but only as an unqualified import. Worth a stronger "import these together" note.

5. **`ir-synthetic-class-generation/guide.md`** — Section 7 "FIR-generated companion + IR-only metadata-visible factory" was extremely useful and matches what this task needs. One missing detail: when the IR-only function takes `Companion` as its dispatch receiver, simply doing `addValueParameter("json", stringType)` on `IrFactory.buildFun { ... }` produces a function with no DispatchReceiver, hence static codegen. The "anti-cheat" gotcha for `addFunction` on `IrClass` does call this out, but for `buildFun` (used here because the function is attached after-the-fact rather than via `addFunction { … }`), the same fix isn't documented. Worth saying "the same DispatchReceiver-parameter requirement applies to `buildFun(...)` followed by manual attachment — synthesise the receiver param yourself before adding the function to the companion's declarations."

6. **Boolean negation in IR** — `pluginContext.irBuiltIns.booleanNotSymbol` is the canonical way to get `Boolean.not`. The skill docs reference `irNot` (a builder) without saying the underlying symbol path. When `irNot` is unavailable (e.g. because the receiver doesn't carry `IrStatementsBuilder<*>` directly) the symbol-call form is the fallback. A one-liner in the body-modification cheat sheet would help.

7. **`fir-declaration-generation-extension/guide.md`** — the section on "advertising INIT on the synthesised companion" is correct and I followed it. The detail that took a re-read: `getCallableNamesForClass` is called on **both** the owner class AND the synthesised companion, so the body must dispatch by `origin`. The guide does say this, but a clearer table of "for each callable-name discovery query, which symbol is `classSymbol`" would prevent the early bug I had where the companion received no `INIT` and the IR side then crashed.

8. **Multi-module + `metadataDeclarationRegistrar` interaction** — the skill says "registered functions become visible to downstream modules". What it does not say explicitly: **a `@JsonSerialize` class in module-B that nests a `@JsonSerialize` class from module-A can serialise it (because `toJson()` is FIR-declared so cross-module visible) but cannot call its own `parse()` from module-B source**, because module-B's `parse` is generated in the same compilation pass as module-B sources. The asymmetry (toJson is cross-module, parse is downstream-only) is the entire point of criterion 14 but a worked-example noting "your nested-cross-module call site behaves differently from your same-module call site" would be helpful.

## Failure analysis

None at the criterion level. The build artefacts at `/tmp/kotlin-skill-eval-06-extreme-json-serialize-20260527-204102/work/` are reproducible: `./gradlew clean :module-B:run` produces:

```
{"user_name":"Alice","age":30,"active":true}
parsed ok: Alice
{"id":7,"items":[{"sku":"SKU-1","qty":2},{"sku":"SKU-2","qty":1}],"customer":{"user_name":"Alice","age":30,"active":true},"paid_amount":99.95}
order ok: SKU-1
{"title":"demo","owner":{"user_name":"Alice","age":30,"active":true}}
cross-module nest ok: demo
malformed handled: parse returned null
```

## Rounds consumed

Approximate count, including re-reads and per-criterion experimentation:

1. SPEC + skill router read, project skeleton, root/plugin/module Gradle wiring.
2. FIR declaration generator + diagnostics + checker scaffold.
3. IR generation extension scaffold (toJson body, stub parse).
4. First build cycle: ~30 compile errors (mostly wrong import paths for IR builders / FIR symbol-internals opt-in).
5. Second build cycle: 1 compile error (IrVararg construction), fixed by using the `elements = mutableListOf(...)` constructor parameter.
6. First runtime cycle: round-trip works for toJson but `@JsonRename` / `@JsonIgnore` ignored — discovered the param-vs-property annotation target mismatch.
7. Per-criterion verification — diagnostics, criterion 14 negative test, criterion 15 with `WithCompanion`, bonus 17 (malformed input), bonus 18 (cross-module nesting).

So roughly 7 effective iterations. The SPEC's "5–8 rounds" estimate matches.

## Skills exercised

| Skill | Used for | Surface coverage |
|---|---|---|
| `compiler-plugin-bootstrap` | project layout, META-INF/services, plugin/module Gradle config | full |
| `fir-extensions-overview` | choosing `FirDeclarationGenerationExtension` + `FirAdditionalCheckersExtension`; `FirExtensionRegistrar` + `FirExtensionRegistrarAdapter` wiring | full |
| `fir-predicate-system` | `DeclarationPredicate.create { annotated(JSON_SERIALIZE_FQN) }`, dual register of `LookupPredicate` for completeness | full |
| `fir-declaration-generation-extension` | `getCallableNamesForClass`, `getNestedClassifiersNames`, `createCompanionObject`, `createDefaultPrivateConstructor`, `createMemberFunction`; the "INIT-on-companion" advertisement pattern | full |
| `fir-additional-checkers-extension` | `FirRegularClassChecker(MppCheckerKind.Common)` + `context(...)` form; `KtDiagnosticsContainer` + `KtDiagnosticFactoryToRendererMap` + `registerDiagnosticContainers`; per-property cross-annotation-and-type checks | full |
| `ir-plugincontext-usage` | `finderForBuiltins`, `findFunctions`/`findClass`, `metadataDeclarationRegistrar.registerFunctionAsMetadataVisible` | full |
| `ir-body-modification` | `irBlockBody`, `irBlock`, `IrWhileLoopImpl`, `IrWhenImpl` constructions; `transformChildrenVoid` + `IrElementTransformerVoidWithContext.visitFunctionNew`; filling FIR-declared empty bodies via `IrDeclarationOrigin.GeneratedByPlugin` filter | full |
| `ir-synthetic-class-generation` | the IR-only-on-companion pattern; `buildFun` + manual DispatchReceiver creation + `companion.declarations += parseFun` + `metadataDeclarationRegistrar` | full |
| `compiler-plugin-debugging` | not formally used (no IR dumps), but the `MessageCollector` warning trick from `compiler-plugin-bootstrap` would have been used if the plugin had silently failed |
| `gradle-plugin-integration` | not used (manual `-Xplugin=` wiring sufficient) |

`fir-status-transformer-extension`, `fir-supertype-generation-extension`, `fir-session-components`, `multi-version-kotlin-support`, `ir-call-rewriting` — not needed.
