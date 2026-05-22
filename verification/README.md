# Verification

Each subdirectory is an **independent Gradle project** that verifies a row of the "How to choose" table in `skills/fir-extensions-overview/SKILL.md`. Every verification has:

- `SPEC.md` — the goal and PASS criterion for this verification
- `plugin/` — the compiler plugin implementing the relevant FIR extension
- `sample/` — a Kotlin module that loads the plugin via `-Xplugin=` and exercises the use case
- `RESULT.md` — outcome of the most recent verification run

The Gradle wrapper at `verification/gradlew` is shared. To run a saved verification:

```bash
cd verification/01-additional-checkers
../gradlew :sample:compileKotlin
```

To re-implement a verification from scratch (audit whether the relevant skill still suffices for an agent — e.g. after a Kotlin version bump), use the sandbox runner:

```bash
scripts/run-verification.sh 04-status-transformer
# Sandbox created at: /tmp/kotlin-skill-verify-04-status-transformer-<timestamp>
```

The sandbox contains only `SPEC.md` — the agent has no spatial access to the saved implementation, other verifications, the bootstrap example under `skills/compiler-plugin-bootstrap/example/`, or `evaluation/` answer keys.

PASS criterion differs per verification:
- **Diagnostic-style** (e.g. checkers): the sample compilation must FAIL with the expected diagnostic
- **Generation-style** (declarations / supertypes / call rewrites / status): the sample must COMPILE and its `Main.main()` must print the expected output

## Verifications

| # | Extension / claim | Use case under test |
|---|---|---|
| 01 | `FirAdditionalCheckersExtension` | Reject `@MustBeFinal` annotated class declared `open` |
| 02 | `FirDeclarationGenerationExtension` | Generate synthetic `companion object` on `@WithCompanion` class |
| 03 | `FirSupertypeGenerationExtension` | Inject `Marker` supertype onto companion of `@Tagged` class |
| 04 | `FirStatusTransformerExtension` | Make `@Open` annotated class and its members `open` |
| 05 | `FirExtensionSessionComponent` | Two extensions share a cached classId set via session component |
| 06 | `FirExpressionResolutionExtension` | Inject implicit `Dsl` receiver inside `myDsl { ... }` |
| 07 | `FirAssignExpressionAltererExtension` | Rewrite `prop = value` to `prop.assign(value)` |
| 08 | `FirSamConversionTransformerExtension` | Promote first SAM parameter to receiver |
| 09 | `FirTypeAttributeExtension` | Distinguish `@Positive Int` from `Int` |
| 10 | `FirFunctionTypeKindExtension` | Make `@MyKind () -> Unit` a distinct type |
| 11 | `FirFunctionCallRefinementExtension` | Refine call return type at call site |
| 12 | Cross-module IR-generated declaration visibility | Module A's IR-generated member is invisible to A's own source but visible to module B's source via `metadataDeclarationRegistrar` |
| 13 | `FirStatusTransformerExtension` (`isInline` slot) | Flip `isInline` on a function via `status.transform { isInline = true }` and confirm bytecode-level inlining |
