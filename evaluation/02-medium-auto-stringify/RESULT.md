# Evaluation Result: 02-medium-auto-stringify — 2026-06-13 (Kotlin 2.4.0 re-run)

**Skills version**: kotlin-compiler-plugin@0.3.0 (branch `chore/evaluation-2.4.0` = PR #1 + #2)
**Kotlin version validated against**: 2.4.0
**Method**: fresh sub-agent implemented from `SPEC.md` in an isolated sandbox using only `skills/kotlin-compiler-plugin/`; scores **re-verified independently** by re-running the build/run + `javap`.

## Final Score: 100 / 100

## Functionality (60 / 60)

| # | Criterion | Result | Notes |
|---|---|---|---|
| 1 | Plugin builds | PASS | `:plugin:jar` → BUILD SUCCESSFUL. |
| 2 | Sample compiles with plugin | PASS | `:sample:compileKotlin` clean. |
| 3 | `Person(...).toAutoString()` resolves | PASS | sample compiles with the call present. |
| 4 | Method in IR/bytecode | PASS | `javap -p Person.class` → `public final String toAutoString()` (count 1). |
| 5 | Person output | PASS | `Person(name=Alice, age=30)`. |
| 6 | Box output | PASS | `Box(width=10, height=20, opaque=true)`. |
| 7 | Untagged lacks the method | PASS | `javap -p Untagged.class` → 0 `toAutoString`. |
| 8 | Not on unannotated classes | PASS | same as 7. |
| 9 | Visibility `public` | PASS | `javap` shows `public final`. |
| 10 | No `IrValidation` errors | PASS | `grep -c IrValidation` = 0. |

Independently re-verified: `./gradlew :plugin:jar :sample:run --rerun-tasks` → exit 0, both expected lines printed, `javap` confirms method presence/absence. 7 `.kt` files; no `SKILL.md`/`plugin.json`.

## Code Quality (20 / 20)

7 files, clean FIR-generate + IR-fill split (`createMemberFunction` + `getCallableNamesForClass` + `GeneratedDeclarationKey`; IR side filters `origin is IrDeclarationOrigin.GeneratedByPlugin` and matches `pluginKey`). Uses `arguments[0] = irGet(dispatchReceiverParameter!!)` (KT-68003 unified arguments) and `primaryConstructor.parameters.filter { it.kind == IrParameterKind.Regular }` — all 2.4-correct.

## Skill Adherence (18 / 20)

No upstream source consulted. **−2: one real doc gap.** The agent initially missed the `org.jetbrains.kotlin.ir.builders.irBlockBody` import — the `ir-body-modification` "DeclarationIrBuilder helpers" cheat-sheet lists the *inner* DSL helpers (`irCall`, `irConcat`, `irReturn`, …) but not the entry-point builders (`irBlockBody`/`irBlock`/`irExprBody`) with their package, producing misleading "receiver type mismatch" errors. Self-corrected from the guide. (Candidate one-line follow-up fix.)

## Anti-cheat findings

Clean — no `verification/`, other `evaluation/*`, or bootstrap `example/`. Wrapper generated via `gradle wrapper`.

## Skill-doc gaps encountered

`ir-body-modification` cheat-sheet should list `irBlockBody`/`irBlock`/`irExprBody` (package `org.jetbrains.kotlin.ir.builders`) among importable builders.

## Overall assessment

100% functionality, no regression from the 2.3.21 baseline. One minor import-completeness gap noted for a future patch.
