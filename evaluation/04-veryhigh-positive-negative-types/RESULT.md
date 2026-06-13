# Evaluation Result: 04-veryhigh-positive-negative-types — 2026-06-13 (Kotlin 2.4.0 re-run)

**Skills version**: kotlin-compiler-plugin@0.3.0 (branch `chore/evaluation-2.4.0` = PR #1 + #2)
**Kotlin version validated against**: 2.4.0
**Method**: fresh sub-agent implemented from `SPEC.md` in an isolated sandbox using only `skills/kotlin-compiler-plugin/`. The build+run path and the type-attribute machinery were **re-verified independently**; the negative-diagnostic cases were exercised by the agent in throwaway modules (removed after) and are corroborated by its captured `[ILLEGAL_NUMBER_SIGN]` transcripts.

## Final Score: 100 / 100 (12 / 12 mandatory criteria PASS; criterion 13 is an explicit optional bonus, not attempted)

## Functionality (60 / 60)

| # | Criterion | Result | Notes |
|---|---|---|---|
| 1 | Plugin builds | PASS | `:plugin:jar` → BUILD SUCCESSFUL (re-verified). |
| 2 | All 6 `ConeAttribute` overrides present | PASS | `union`/`intersect`/`add`/`isSubtypeOf`/`key`/`keepInInferredDeclarationType` (re-verified: 6/6). |
| 3 | `attributeAccessor` at top level | PASS | `val ConeAttributes.numberSign by ConeAttributes.attributeAccessor<…>()`. |
| 4 | `convertAttributeToAnnotation` type guard | PASS | `if (attribute !is ConeNumberSignAttribute) return null`. |
| 5 | Sample compiles | PASS | re-verified. |
| 6 | Sample runs, exit 0 | PASS | `:sample:run` BUILD SUCCESSFUL (re-verified). |
| 7 | Output `5, -7, 5, -7, 42` | PASS | re-verified exact order. |
| 8 | `takePositive(makeNegative())` → ILLEGAL_NUMBER_SIGN | PASS | `[ILLEGAL_NUMBER_SIGN] @Positive expected, but @Negative was passed`. |
| 9 | Symmetric mismatch → ILLEGAL_NUMBER_SIGN | PASS | factory name in brackets. |
| 10 | Mismatched local-var type → ILLEGAL_NUMBER_SIGN | PASS | via `SignedNumberPropertyChecker`. |
| 11 | `takeAny(makePositive())` no diagnostic | PASS | build successful. |
| 12 | Same-sign call no diagnostic | PASS | build successful. |
| 13 (opt) | Metadata round-trip | not attempted | optional/bonus; `convertAttributeToAnnotation` implemented so the plumbing exists. |

Independently re-verified: `:plugin:jar :sample:run --rerun-tasks` → exit 0, output `5 -7 5 -7 42`, 6/6 attribute overrides. 11 `.kt` files (final deliverable).

## Code Quality (20 / 20)

`fir-type-attribute-extension` covers this task almost verbatim (it uses the very `@Positive`/`@Negative` example), so `ConeNumberSignAttribute`, the extension, and both checkers came cleanly from the guide. Annotations placed in the plugin module with a normal `implementation` dependency from the sample.

## Skill Adherence (20 / 20)

No upstream source consulted. Recurring 2.4 note (already in 0.3.0 CHANGES): `-Xcontext-parameters` is redundant on 2.4 — harmless warning. JDK 25 → JDK 21 launcher pin required and worked.

## Anti-cheat findings

Clean — no forbidden directories; throwaway negative-test module removed.

## Skill-doc gaps encountered

None beyond the already-documented `-Xcontext-parameters`-on-2.4 note. `fir-type-attribute-extension` is a high-coverage guide for this task.

## Overall assessment

100% on all mandatory criteria. No regression from the 2.3.21 baseline.
