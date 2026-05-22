# Verification: 04-status-transformer

## Result: PASS

## What was verified

A K2 Kotlin compiler plugin built around `FirStatusTransformerExtension` that
flips `Modality.FINAL` to `Modality.OPEN` on user declarations annotated with
`@com.example.Open` (and the members of such classes), enabling subclassing
and override of declarations that would otherwise be `final` by default.

## Build & run

```
$ ../gradlew :sample:run
> Task :sample:run
from Sub

BUILD SUCCESSFUL
```

The sample compiles AND prints `from Sub`. Without the plugin, compilation
would fail with `'greet' in 'Base' is final and cannot be overridden`.

## Key findings during the verification

1. **`status.modality` is often `null` at status-transform time.**
   The end-to-end example in `skills/fir-status-transformer-extension/SKILL.md`
   compares against `Modality.FINAL`, but in practice the modality is **not yet
   resolved** when the status transformer runs — it is `null` for the user's
   `class Base { fun greet(): String = ... }` because no explicit modifier was
   written. Comparing `status.modality == Modality.FINAL` therefore never
   matched and the transform was a no-op.

   The fix that the production allopen plugin uses is `copyWithNewDefaults`,
   which can supply both the current `modality` and the `defaultModality` (the
   modality that gets resolved when the user wrote nothing). The verified
   plugin uses the same idiom:

   ```kotlin
   when (status.modality) {
       null           -> status.copyWithNewDefaults(modality = Modality.OPEN, defaultModality = Modality.OPEN)
       Modality.FINAL -> status.copyWithNewDefaults(defaultModality = Modality.OPEN)
       else           -> status
   }
   ```

   Recommend updating the SKILL.md example to use this pattern instead of
   `status.transform(modality = Modality.OPEN)` guarded by
   `status.modality == Modality.FINAL`, or add a gotcha note covering the
   `null` modality case.

2. **The function-type overload uses `FirNamedFunction`, not `FirSimpleFunction`.**
   `SKILL.md` correctly says `FirNamedFunction` in the prose, but mismatches
   on the type are easy to make. The actual signature in 2.3.21 is
   `transformStatus(status, function: FirNamedFunction, ...)`.

3. **`CompilerPluginRegistrar.pluginId` is `abstract` in 2.3.21.**
   The minimal example in `skills/compiler-plugin-bootstrap/example/` overrides it but the
   `verification/01-additional-checkers` registrar does not — that one only
   compiles because `CompilerPluginRegistrar` originally had a default impl
   for `pluginId`. As of 2.3.21 it is abstract; a `pluginId` override is
   required for any registrar.

4. **The sample's `mainClass` must include the package** — `com.example.MainKt`
   not `MainKt` — because the sample source declares `package com.example`.

## Files

- `plugin/src/main/kotlin/com/example/openplugin/OpenPluginComponentRegistrar.kt`
- `plugin/src/main/kotlin/com/example/openplugin/fir/OpenFirExtensionRegistrar.kt`
- `plugin/src/main/kotlin/com/example/openplugin/fir/MakeOpenStatusTransformer.kt`
- `plugin/src/main/resources/META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar`
- `sample/src/main/kotlin/Main.kt`
