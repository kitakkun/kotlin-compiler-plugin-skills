# Verification: 13-status-transformer-inline

## Result: PASS

## What was verified

A K2 Kotlin compiler plugin built around `FirStatusTransformerExtension` that
flips `isInline = true` on top-level functions annotated with
`@com.example.MakeInline`. The plugin demonstrates that the status transformer
can modify the `isInline` slot of `FirDeclarationStatus` and that the change
propagates through the rest of the compiler pipeline — including the FIR
checker that validates non-local returns and the IR backend that performs the
actual inlining.

## Build & run

```
$ ../gradlew :sample:run
> Task :plugin:compileKotlin
> Task :plugin:jar
> Task :sample:compileKotlin
> Task :sample:run
found

BUILD SUCCESSFUL in 1s
```

`:sample:compileKotlin` succeeded. The sample's `main()` printed `found`.

Without the plugin, `Sample.kt` would fail to compile because the lambda passed
to the (non-`inline`-by-source) `runIt` performs a non-local return
(`return "found"`), which is rejected unless the receiving function is
effectively `inline`.

### Bytecode confirms actual inlining

`javap -p -c sample/build/classes/kotlin/main/com/example/MainKt.class` for
`findIt()`:

```
public static final java.lang.String findIt();
  Code:
     0: iconst_0
     1: istore_0
     2: iconst_0
     3: istore_1
     4: ldc           #30                 // String found
     6: areturn
```

The body of `findIt()` contains no `invokestatic runIt(...)` — the call has
been replaced by the inlined lambda body, which directly loads the constant
`"found"` and returns. This is the expected shape for an inlined call site
with a non-local return, and proves the `isInline` flip took effect through
the IR backend, not just the FIR frontend checker.

## Implications for the SKILL claim

The `fir-status-transformer-extension` SKILL claim that *"visibility, modality,
isOpen, isFinal, isInline, etc."* are modifiable holds for `isInline` on
top-level functions: setting `isInline = true` from a status transformer is
both honored by the FIR non-local-return checker and produces actual inlining
in the IR backend.

## Key findings during the verification

1. **`copyWithNewDefaults` does not expose `isInline`.**
   `compiler/fir/tree/src/.../Utils.kt:170-191` defines
   `copyWithNewDefaults(visibility, modality, defaultVisibility, defaultModality)` —
   only modality- and visibility-related parameters. It preserves all other
   flags (incl. `isInline`) via `copyStatusAttributes`, so it is fine for
   modality flips (verification 04) but does not by itself toggle `isInline`.

   The canonical helper for flipping arbitrary flags is the `transform` helper
   in `compiler/fir/resolve/src/.../FirStatusTransformerExtension.kt:134-160`:

   ```kotlin
   inline fun FirDeclarationStatus.transform(
       visibility: Visibility = this.visibility,
       modality: Modality? = this.modality,
       init: FirDeclarationStatusImpl.() -> Unit = {},
   ): FirDeclarationStatus
   ```

   The `init` block is invoked on a new `FirDeclarationStatusImpl` *after*
   visibility, modality, and the boolean-flag set are copied, so `init` can
   freely mutate any of `isInline`, `isOperator`, `isInfix`, `isTailRec`, etc.

   This verification uses:

   ```kotlin
   return status.transform { isInline = true }
   ```

   The SKILL.md "End-to-end example" focuses on `copyWithNewDefaults` (which is
   the right idiom for the `null`-modality default-FINAL case in allopen) and
   only mentions `transform` in passing. For non-modality flag flips, the
   `transform { ... }` helper is the canonical path. Recommend the SKILL grow a
   small "flipping a non-modality flag" example to make this less easy to miss.

2. **`isLocal` bail-out is still relevant for inline.**
   `runIt` here is top-level, so the bail-out is unused — but if `runIt` were
   defined inside another function, the predicate would still match the
   annotation and `isLocal = true` would be passed to the transformer. Marking
   a local function `inline` is meaningless (and rejected with `LOCAL_INLINE_FUNCTION_NOT_ALLOWED` 
   or similar), so the transformer keeps the same `if (isLocal) return status`
   guard as the modality-flip verification.

3. **No special FIR/IR plumbing was needed beyond the status flip.**
   This was the question worth verifying — once `isInline = true` lands in the
   `FirDeclarationStatus`, both the frontend non-local-return checker and the
   IR backend (which decides whether to lower a call as inlined) read the same
   flag. No additional `IrGenerationExtension` is required to "actually inline"
   the function. The whole inline machinery downstream is driven from this one
   bit.

## Files

- `plugin/src/main/kotlin/com/example/inlineplugin/InlinePluginComponentRegistrar.kt`
- `plugin/src/main/kotlin/com/example/inlineplugin/fir/InlineFirExtensionRegistrar.kt`
- `plugin/src/main/kotlin/com/example/inlineplugin/fir/MakeInlineStatusTransformer.kt`
- `plugin/src/main/resources/META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar`
- `sample/src/main/kotlin/Main.kt`

## Re-run on Kotlin 2.4.20

**Status: PASS** (unchanged from the 2.3.21 result; no source changes were needed, only the version pins in `build.gradle.kts`).

```
$ ../gradlew --no-daemon -q clean :sample:run
found
(exit code 0)
```

Validated with Kotlin 2.4.20, Gradle 9.5.0, JDK 21 on 2026-09-10.
