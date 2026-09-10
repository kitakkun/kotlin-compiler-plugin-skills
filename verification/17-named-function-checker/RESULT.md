# 17-named-function-checker — RESULT

**Status: PASS**

## How verified

```bash
cd verification/17-named-function-checker
../gradlew --no-daemon clean :sample:compileKotlin
```

Trimmed output:

```
> Task :plugin:compileKotlin
> Task :plugin:jar

> Task :sample:compileKotlin FAILED
e: file:///.../verification/17-named-function-checker/sample/src/main/kotlin/Sample.kt:7:5 [NO_SHOUTING] Function name 'SHOUT' must not be all upper-case

FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':sample:compileKotlin' (registered by plugin 'org.jetbrains.kotlin.jvm').
BUILD FAILED in 11s
```

- `Sample.kt:7` is `fun SHOUT() {` — column 5 is the `S` of `SHOUT` (after `fun `), so
  `SourceElementPositioningStrategies.NAME_IDENTIFIER` positioned the error on the name identifier.
- `Sample.kt:3` `fun quiet()` produced no diagnostic.
- `:plugin:compileKotlin` succeeded **without** `-Xcontext-parameters` (the plugin module's
  `build.gradle.kts` has no `freeCompilerArgs` at all).
- `[NO_SHOUTING]` appears in the output because the sample module passes
  `-Xrender-internal-diagnostic-names`.

## Scratch experiments (not committed; files restored afterwards)

### A. Adding `-Xcontext-parameters` to the plugin module

Appended to `plugin/build.gradle.kts`:

```kotlin
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
}
```

`../gradlew --no-daemon clean :plugin:compileKotlin` still succeeds and the compiler prints exactly:

```
w: The argument '-Xcontext-parameters' is redundant for the current language version 2.4.
```

Note: with `-q` the warning is not shown at all (Gradle's quiet log level hides `w:` lines); it
appeared with the default log level / `--info`.

### B. Using the pre-2.4.20 names `FirSimpleFunctionChecker` / `simpleFunctionCheckers`

`sed 's/FirNamedFunctionChecker/FirSimpleFunctionChecker/g; s/namedFunctionCheckers/simpleFunctionCheckers/g'`
on `NoShoutingCheckersExtension.kt`, then `../gradlew --no-daemon clean :plugin:compileKotlin`:

```
e: .../NoShoutingCheckersExtension.kt:9:63 Unresolved reference 'FirSimpleFunctionChecker'.
e: .../NoShoutingCheckersExtension.kt:19:5 'simpleFunctionCheckers' overrides nothing.
e: .../NoShoutingCheckersExtension.kt:19:46 Unresolved reference 'FirSimpleFunctionChecker'.
e: .../NoShoutingCheckersExtension.kt:23:28 Unresolved reference 'FirSimpleFunctionChecker'.
BUILD FAILED
```

(Line 9 is the import, line 19 the `override val simpleFunctionCheckers: Set<FirSimpleFunctionChecker>`,
line 23 the `object NoShoutingChecker : FirSimpleFunctionChecker(MppCheckerKind.Common)`.) This matches
CHANGES.md exactly: unresolved reference for the alias and `'... overrides nothing'` for the bucket.

## Key source snippets

`plugin/src/main/kotlin/com/example/shouting/NoShoutingCheckersExtension.kt`:

```kotlin
class NoShoutingCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = NoShoutingDeclarationCheckers
}

object NoShoutingDeclarationCheckers : DeclarationCheckers() {
    override val namedFunctionCheckers: Set<FirNamedFunctionChecker> = setOf(NoShoutingChecker)
}

object NoShoutingChecker : FirNamedFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirNamedFunction) {
        val name = declaration.name.asString()
        if (!name.any { it.isLetter() }) return
        if (name != name.uppercase()) return
        val source = declaration.source ?: return
        reporter.reportOn(source, NoShoutingDiagnostics.NO_SHOUTING, name)
    }
}
```

`plugin/src/main/kotlin/com/example/shouting/NoShoutingDiagnostics.kt`:

```kotlin
object NoShoutingDiagnostics : KtDiagnosticsContainer() {
    val NO_SHOUTING by error1<KtNamedFunction, String>(SourceElementPositioningStrategies.NAME_IDENTIFIER)
    override fun getRendererFactory(): BaseDiagnosticRendererFactory = NoShoutingDefaultErrorMessages
}

object NoShoutingDefaultErrorMessages : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("NoShouting") { map ->
        map.put(NoShoutingDiagnostics.NO_SHOUTING, "Function name ''{0}'' must not be all upper-case", CommonRenderers.STRING)
    }
}
```

`plugin/src/main/kotlin/com/example/shouting/NoShoutingExtensionRegistrar.kt`:

```kotlin
override fun ExtensionRegistrarContext.configurePlugin() {
    +::NoShoutingCheckersExtension
    registerDiagnosticContainers(NoShoutingDiagnostics)
}
```

`plugin/build.gradle.kts` — `kotlin("jvm") version "2.4.20"`, `jvmToolchain(21)`,
`compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.20")`, no free compiler args.

`sample/src/main/kotlin/Sample.kt`:

```kotlin
fun quiet() { println("quiet") }   // line 3 — ok
fun SHOUT() { println("SHOUT") }   // line 7 — NO_SHOUTING at 7:5
```

## Skill feedback

Everything in `guide.md` / `CHANGES.md` needed to build this probe was correct for 2.4.20: the
`FirNamedFunctionChecker` / `namedFunctionCheckers` names, the context-parameter `check` override,
`registerDiagnosticContainers`, `error1<KtNamedFunction, String>` + `CommonRenderers.STRING`,
`NAME_IDENTIFIER`, and `-Xrender-internal-diagnostic-names`. The build passed on the first attempt
with no API guesswork. Findings:

1. **Wrong warning text for a redundant `-Xcontext-parameters`** (guide.md, section
   "`-Xcontext-parameters` is no longer needed (stable since Kotlin 2.4.0)"). The guide quotes:
   `w: ... "-Xcontext-parameters" has no effect: the feature is enabled by default since language version 2.4`.
   The 2.4.20 compiler actually prints:
   `w: The argument '-Xcontext-parameters' is redundant for the current language version 2.4.`
   Anyone grepping a build log for `has no effect` will not find it. Replace the quoted line with the
   real one. (CHANGES.md's "may now warn as redundant" wording is fine.)

2. **The redundancy warning is invisible under `gradlew -q`.** Since the verification convention
   and many CI setups run with `-q`, a one-line note that `w:` lines are suppressed at Gradle's quiet
   log level would save a confused "the guide says it warns but I see nothing" round trip.

3. **Minor path inaccuracies.** guide.md says the `Fir*Checker` base classes "live in
   `kotlin/compiler/fir/checkers/src/.../checkers/`", and CHANGES.md cites
   `FirDeclarationCheckerAliases.kt:39` / `DeclarationCheckers.kt:26` without a directory. At v2.4.20
   both files are **generated** and live under `compiler/fir/checkers/gen/org/jetbrains/kotlin/fir/analysis/checkers/declaration/`
   (only `FirDeclarationChecker.kt` itself is under `src/`). Likewise CHANGES.md's
   `KtDiagnosticFactoryDsl.kt:25-28` (for `infoWithoutSource`) is in `compiler/frontend.common-psi/`,
   not `compiler/frontend.common/`. The `git show v2.4.20:<path>` lookups fail until the path is fixed.

4. **Nothing to add for the rename itself** — the CHANGES.md prediction "fails to compile against
   2.4.20 with an unresolved reference (and, for the bucket, 'namedFunctionCheckers' overrides
   nothing)" is confirmed verbatim, except that the message names the *old* identifier:
   `'simpleFunctionCheckers' overrides nothing.` (the sentence currently says `'namedFunctionCheckers'`,
   which is the new name and cannot be the one that "overrides nothing"). Fix the identifier in that
   sentence.
