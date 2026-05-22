# Verification 01 — `FirAdditionalCheckersExtension`

## Goal

Verify that `FirAdditionalCheckersExtension` can reject code with a custom diagnostic. Build a plugin that emits an error on classes annotated with `@com.example.MustBeFinal` if they are also declared `open`.

## Sample requirements

- `annotation class MustBeFinal` (in `com.example`)
- `@MustBeFinal open class Bad` — must trigger the diagnostic
- `@MustBeFinal class Ok` — must NOT trigger (only `open` matters)

## PASS criterion

`./gradlew :sample:compileKotlin` fails with the custom error message naming `Bad`. `Ok` compiles cleanly. The plugin uses a `KtDiagnosticsContainer` registered via `FirExtensionRegistrar.registerDiagnosticContainers(...)`.

## Skills to consult

- `fir-additional-checkers-extension`
- `fir-predicate-system`
- `compiler-plugin-bootstrap`
