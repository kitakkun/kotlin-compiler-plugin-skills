# Verification 11 — `FirFunctionCallRefinementExtension`

## Goal

Verify that `FirFunctionCallRefinementExtension` (annotated `@FirExtensionApiInternals`) refines a call's return type at the call site. A function `fun box(): Box<*>` annotated `@com.example.RefineMe` should — at the call site — get a refined return type `Box<RefinedSchema>` where `RefinedSchema` is a synthetic local class.

This is the **highest-complexity, unstable** verification. The upstream Kotlin sandbox tests for this extension are gated `// RUN_PIPELINE_TILL: FRONTEND` — they exercise FIR but never run IR/codegen.

## Sample requirements

- `annotation class RefineMe`
- `interface Box<T>`
- `@RefineMe fun box(): Box<*> = object : Box<Any> {}`
- `fun main() { val b = box(); /* observe refined type via something */ }`

## PASS criterion

- `../gradlew --no-daemon clean :sample:run` **compiles and runs** (exit code 0): the synthetic local class produced by `transform()` survives fir2ir and JVM codegen. This is necessary but not sufficient, since a run that never refines also exits 0; the second bullet is what shows refinement happened.
- `intercept()` / `transform()` fire (visible via the plugin's debug logging, e.g. with `-Pkotlin.compiler.execution.strategy=in-process`, or via `FIR_DUMP`), and the dump shows `box()`'s return type refined to `Box<RefinedSchema>` at the call site.

## Requirements discovered while making this pass

- The generated local class has `FirDeclarationOrigin.Plugin` origin, so its declared-member scope comes **only** from registered `FirDeclarationGenerationExtension`s; a primary constructor hand-built into `klass.declarations` is invisible to `primaryConstructorIfAny` and fir2ir fails with an NPE in `ClassMemberGenerator.convertClassContent`. The plugin therefore pairs the refinement extension with a companion `FirDeclarationGenerationExtension` that supplies the constructor (the kotlin-dataframe `TokenContentGenerator` pattern). Through Kotlin 2.3.21 this probe was recorded as PARTIAL because of that NPE; it was a plugin bug, not a compiler limitation.
- Since Kotlin 2.4.20 every generated local declaration must carry a distinct source element (`KtFakeSourceElementKind.PluginGenerated.Custom(marker)` / `.Default`). The KDoc contract asks for it; this probe did not test what happens when it is omitted.
- The generated local class must be emitted as a statement inside a `run { }` lambda; otherwise the Mode B `JvmInventNamesForLocalClasses` failure from 2.3.21 returns (see RESULT.md).

## Known limitations

- Production `kotlin-dataframe` plugin pairs this extension with extensive IR-side rewriting that's beyond the scope of this verification.

## Skills to consult

- `fir-function-call-refinement-extension`
- `compiler-plugin-bootstrap`
