---
name: fir-additional-checkers-extension
description: Add custom Kotlin diagnostics (errors and warnings shown in the IDE and on the command line) via FirAdditionalCheckersExtension — declaration, expression, type, and language-version-settings checkers, KtDiagnosticFactory declaration, KtDiagnosticsContainer registration, and reporter usage. Use when the goal is to flag bad source code with a red squiggle, deprecate a usage pattern, or enforce a project-specific rule. Read fir-extensions-overview first. NOT for transforming code (see ir-* skills) or for synthesising new declarations (see fir-declaration-generation-extension). If the user reports a `check(...)` override stops compiling after a Kotlin upgrade, or a `KtDiagnosticFactoryToRendererMap` error, ALSO Read CHANGES.md in this skill's directory.
---

# FirAdditionalCheckersExtension

This is the K2 extension point that gates **diagnostics** — the warnings and errors the compiler reports against user source. Anything you'd want to be a `w:` or `e:` line in `kotlinc` output, or a red/yellow squiggle in the IDE, is implemented here.

Source: [`kotlin/compiler/fir/checkers/src/org/jetbrains/kotlin/fir/analysis/extensions/FirAdditionalCheckersExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/checkers/src/org/jetbrains/kotlin/fir/analysis/extensions/FirAdditionalCheckersExtension.kt).

## What you get

```kotlin
abstract class FirAdditionalCheckersExtension(session: FirSession) : FirExtension(session) {
    open val declarationCheckers: DeclarationCheckers = DeclarationCheckers.EMPTY
    open val expressionCheckers: ExpressionCheckers = ExpressionCheckers.EMPTY
    open val typeCheckers: TypeCheckers = TypeCheckers.EMPTY
    open val languageVersionSettingsCheckers: LanguageVersionSettingsCheckers = LanguageVersionSettingsCheckers.EMPTY
    fun interface Factory : FirExtension.Factory<FirAdditionalCheckersExtension>
}
```

Four checker buckets:

| Bucket | Override when checking |
|---|---|
| `declarationCheckers` | classes, functions, properties, files, type aliases |
| `expressionCheckers` | call sites, when-branches, returns, assignments, operator usages |
| `typeCheckers` | written type references in source (param types, return types, supertypes) |
| `languageVersionSettingsCheckers` | configuration-level checks (compiler flags, language version compatibility) |

You override exactly the buckets you need; the rest stay `EMPTY`.

## End-to-end example: forbid `@MustBeFinal` open classes

This will report an error when a class is annotated `@MustBeFinal` but declared `open`.

### 1. Annotation (defined in user code, not the plugin)

```kotlin
package com.example.mustbefinal
annotation class MustBeFinal
```

### 2. Diagnostic factory + container

Diagnostics are not strings — they are *factory* objects that capture a stable identifier, a severity, and the parameter shape.

```kotlin
package com.example.mustbefinal.fir

import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.psi.KtClass

object MustBeFinalDiagnostics : KtDiagnosticsContainer() {
    val MUST_BE_FINAL_OPEN by error0<KtClass>(SourceElementPositioningStrategies.MODALITY_MODIFIER)

    override fun getRendererFactory() = KtDefaultErrorMessagesMustBeFinal
}

object KtDefaultErrorMessagesMustBeFinal : org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory() {
    override val MAP by org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap("MustBeFinal") { map ->
        map.put(MustBeFinalDiagnostics.MUST_BE_FINAL_OPEN, "Class annotated @MustBeFinal must not be open")
    }
}
```

`error0<KtClass>(...)` declares a 0-argument error factory whose source element is a `KtClass` (the declaration itself). Variants:

| Helper | Severity | Argument count |
|---|---|---|
| `error0` / `warning0` | error / warning | 0 |
| `error1` / `warning1` | error / warning | 1 typed argument |
| `error2` / `warning2` | error / warning | 2 typed arguments |
| `error3` / `warning3` | error / warning | 3 typed arguments |

Choose arity by how many runtime values you want to interpolate into the message.

### Multi-argument factories — renderer arguments are required

Factories of arity ≥1 take **typed arguments at the call site** AND require renderer arguments when registered in the `KtDiagnosticFactoryToRendererMap`. The 0-arg case `map.put(factory, "fixed message")` works directly; for `error1` / `error2` you must supply a renderer per parameter:

```kotlin
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers

object MySignDiagnostics : KtDiagnosticsContainer() {
    // expected vs actual sign — both are String
    val ILLEGAL_SIGN by error2<KtElement, String, String>(SourceElementPositioningStrategies.DEFAULT)

    override fun getRendererFactory() = MySignErrorRenderers
}

object MySignErrorRenderers : org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory() {
    override val MAP by org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap("MySign") { map ->
        map.put(
            MySignDiagnostics.ILLEGAL_SIGN,
            "expected {0}, got {1}",
            CommonRenderers.STRING,    // renderer for slot {0}
            CommonRenderers.STRING,    // renderer for slot {1}
        )
    }
}
```

The slots `{0}` / `{1}` are filled at report time:

```kotlin
reporter.reportOn(source, MySignDiagnostics.ILLEGAL_SIGN, "Positive", "Negative")
```

Forgetting the trailing `CommonRenderers.STRING` arguments produces a confusing "no candidate matches the call" error pointing at `map.put`. The arity of `put` overloads scales with the factory's type parameters; pick the matching one by counting type arguments.

`CommonRenderers` (in `org.jetbrains.kotlin.diagnostics.rendering`) provides `STRING`, `THROWABLE`, `NAME`, etc. For domain objects, write a small `DiagnosticParameterRenderer` (see the `kotlinx-serialization` plugin's checker for a worked example).

### 3. Checker

```kotlin
package com.example.mustbefinal.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val MUST_BE_FINAL_ANNOTATION = ClassId(
    FqName("com.example.mustbefinal"),
    Name.identifier("MustBeFinal"),
)

object MustBeFinalChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        if (!declaration.hasAnnotation(MUST_BE_FINAL_ANNOTATION, context.session)) return
        if (declaration.status.modality != org.jetbrains.kotlin.descriptors.Modality.OPEN) return
        val src = declaration.source ?: return
        reporter.reportOn(src, MustBeFinalDiagnostics.MUST_BE_FINAL_OPEN)
    }
}
```

Each `Fir*Checker` base class corresponds to a FIR node type. The generic base `FirDeclarationChecker<D>` / `FirExpressionChecker<E>` live under `kotlin/compiler/fir/checkers/src/org/jetbrains/kotlin/fir/analysis/checkers/{declaration,expression}/`, but the per-node aliases below (`FirNamedFunctionChecker`, `FirRegularClassChecker`, ...) and the `DeclarationCheckers` / `ExpressionCheckers` buckets are **generated** into `kotlin/compiler/fir/checkers/gen/org/jetbrains/kotlin/fir/analysis/checkers/{declaration,expression}/` (`FirDeclarationCheckerAliases.kt`, `FirExpressionCheckerAliases.kt`, `DeclarationCheckers.kt`, `ExpressionCheckers.kt`) — look there, not under `src/`, when you need the exact alias name:

| Base class | Triggered for | `check()` parameter type |
|---|---|---|
| `FirRegularClassChecker` | every concrete class/interface declaration | `FirRegularClass` |
| `FirNamedFunctionChecker` | every named function | `FirNamedFunction` ⚠️ |
| `FirPropertyChecker` | every property | `FirProperty` |
| `FirFunctionCallChecker` | every call expression | `FirFunctionCall` |
| `FirReturnExpressionChecker` | every `return` | `FirReturnExpression` |
| `FirTryExpressionChecker` | every `try`/`catch`/`finally` expression | `FirTryExpression` (bucket: `expressionCheckers.tryExpressionCheckers`) |
| `FirTypeRefChecker` | every written type reference | `FirTypeRef` |
| `FirFileChecker` | every source file | `FirFile` |
| `FirBasicDeclarationChecker` | catch-all for any declaration | `FirDeclaration` |

⚠️ **`FirNamedFunctionChecker` is a typealias** — `typealias FirNamedFunctionChecker = FirDeclarationChecker<FirNamedFunction>`. Up to Kotlin 2.4.10 this alias was called `FirSimpleFunctionChecker` (the FIR node had already been renamed from `FirSimpleFunction` to `FirNamedFunction`, but the checker alias kept the old wording); Kotlin 2.4.20 renamed the alias to `FirNamedFunctionChecker` and the matching `DeclarationCheckers.simpleFunctionCheckers` bucket to `namedFunctionCheckers`, with no deprecated alias left behind. Your `check()` override must use `declaration: FirNamedFunction` — `FirSimpleFunction` won't compile. See `CHANGES.md` for the cross-version pattern.

`MppCheckerKind` is a **session-routing** flag, not an expect/actual filter:
- `Common` runs in the declaration's owning session — what most checkers want.
- `Platform` runs in the leaf-platform session against sources from all modules. Use only when your check needs the fully-resolved platform view.

If you want to skip `expect` declarations specifically, guard with `if (declaration.isExpect) return` inside the body.

### 4. Bundle the checkers

```kotlin
package com.example.mustbefinal.fir

import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers

object MustBeFinalDeclarationCheckers : DeclarationCheckers() {
    override val regularClassCheckers: Set<FirRegularClassChecker> = setOf(MustBeFinalChecker)
}
```

Each `*Checkers` container has typed fields for each kind of node (`regularClassCheckers`, `namedFunctionCheckers` — called `simpleFunctionCheckers` before 2.4.20 — etc.). Override only the ones you populate.

### 5. Extension class

```kotlin
package com.example.mustbefinal.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

class MustBeFinalCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = MustBeFinalDeclarationCheckers
}
```

### 6. Wire it up

In your `FirExtensionRegistrar`:

```kotlin
class MyFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::MustBeFinalCheckersExtension
        registerDiagnosticContainers(MustBeFinalDiagnostics)
    }
}
```

`registerDiagnosticContainers(...)` is **not optional**. Without it, your factory is unknown to the FIR pipeline and reporting it raises `IllegalStateException: Diagnostic factory was not registered`. Pass every `KtDiagnosticsContainer` your checkers reference.

## Why context parameters?

`FirDeclarationChecker.check` is declared with **context parameters**:

```kotlin
// kotlin/compiler/fir/checkers/src/.../FirDeclarationChecker.kt:14-18
abstract class FirDeclarationChecker<D : FirDeclaration> {
    // Invariant on purpose: the KDoc says "We don't declare it as `in D` because we
    // want to prevent accidentally adding more general checkers to sets of specific
    // checkers." Useful to know if you're trying to register a generic-checker container.
    context(context: CheckerContext, reporter: DiagnosticReporter)
    abstract fun check(declaration: D)
}
```

The override **must** use the matching context-parameter form. A regular three-parameter `fun check(declaration, context, reporter)` does *not* override the abstract member — it's a separate method, and the abstract one stays unimplemented. (Older plugin code from before context parameters were stable on `FirDeclarationChecker.check` used a regular `fun check(declaration, context, reporter)` value-parameter signature; current Kotlin 2.4.x is context-parameters all the way down.)

`reporter.reportOn(source, factory)` is the idiomatic call inside a `context(... DiagnosticReporter)`-bearing function — no trailing `context` argument because it's already in scope.

### `-Xcontext-parameters` is no longer needed (stable since Kotlin 2.4.0)

Context parameters are a stable language feature since Kotlin 2.4.0 (`LanguageFeature.ContextParameters` has `sinceVersion = KOTLIN_2_4`), so the `context(context: CheckerContext, reporter: DiagnosticReporter)` override above compiles without any extra flag on the target version. If the plugin module still carries the flag from a 2.3.x-era build, the compiler reports it as redundant:

```
w: The argument '-Xcontext-parameters' is redundant for the current language version 2.4.
```

(Exact 2.4.20 text, emitted through `CliDiagnostics.REDUNDANT_CLI_ARG`. Gradle's `-q` / `--quiet` log level hides `w:` lines, so if you build with `-q` you will not see it at all.) Drop it from `build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

tasks.withType<KotlinCompile>().configureEach {
    // No longer needed on Kotlin 2.4+; keep only while the same source set must also build on 2.3.x.
    // compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
}
```

Keep the flag only if the same plugin source set must also compile against a 2.3.x compiler, where the feature was still experimental and the override would otherwise fail with `The feature "context parameters" is experimental and should be enabled explicitly`. See `CHANGES.md` for the 2.3 → 2.4 note.

There is a **complementary flag** for the consumer/sample module that controls diagnostic factory-name visibility in compile output — see the `-Xrender-internal-diagnostic-names` section below.

## Reporter API patterns

Inside a checker function carrying `context(context: CheckerContext, reporter: DiagnosticReporter)`:

```kotlin
// 0-argument factory:
reporter.reportOn(source, MyDiagnostics.MY_ERROR)

// 1-argument factory (string interpolation slot):
reporter.reportOn(source, MyDiagnostics.MY_ERROR_1, "extra info")

// With a positioning strategy override (rare; usually set on the factory itself):
reporter.reportOn(source, MyDiagnostics.MY_ERROR, positioningStrategy = SourceElementPositioningStrategies.NAME_IDENTIFIER)
```

`source` is normally `declaration.source`, `expression.source`, etc. The factory's positioning strategy decides which subrange of that source range the squiggle covers (the modality keyword, just the name, the whole declaration, etc.). Common strategies in `SourceElementPositioningStrategies`:

| Strategy | Squiggle covers |
|---|---|
| `DEFAULT` | the whole element |
| `NAME_IDENTIFIER` | just the declaration's name |
| `MODALITY_MODIFIER` | the `open` / `abstract` / `sealed` / `final` keyword |
| `VISIBILITY_MODIFIER` | the `public` / `private` / `internal` / `protected` keyword |
| `INLINE_FUN_MODIFIER` | the `inline` keyword on a function |
| `OPERATOR` | the `operator` keyword on a function |
| `TYPE_PARAMETERS_LIST` | `<...>` of a generic declaration |
| `VAL_OR_VAR_NODE` | `val` / `var` keyword of a property |
| `OVERRIDE_MODIFIER` | the `override` keyword |
| `DECLARATION_RETURN_TYPE` | the declared return type of a callable |

The full catalogue lives at `kotlin/compiler/frontend.common-psi/src/org/jetbrains/kotlin/diagnostics/SourceElementPositioningStrategies.kt` — when you need a strategy not listed above (e.g. `DECLARATION_SIGNATURE`, `SUPERTYPES_LIST`), check the source for the exact `val` name.

### `-Xrender-internal-diagnostic-names` — making factory names appear in compile output (consumer module)

By default, the compiler renders only the diagnostic's *message* in console output, not the factory name you defined (e.g. `MUST_BE_FINAL_OPEN` from the example above). So grepping the build log for the factory name won't match unless you opt the consumer module into name rendering with the `-Xrender-internal-diagnostic-names` flag:

```kotlin
// sample/build.gradle.kts (the consumer of the plugin, not the plugin itself)
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xrender-internal-diagnostic-names")
}
```

With the flag set, the compiler prints lines like `e: Foo.kt:3:1 [MUST_BE_FINAL_OPEN] Class annotated @MustBeFinal must not be open` — the factory name appears in `[brackets]` before the message. Without it, only the human-readable message appears. The flag is intended for debugging and CI assertions; production users don't need it.

## Common patterns

### Looking up annotations

```kotlin
declaration.hasAnnotation(ANNOTATION_CLASS_ID, context.session)        // boolean
declaration.getAnnotationByClassId(ANNOTATION_CLASS_ID, context.session) // FirAnnotation?
```

(Some private helpers in the compiler are named `findAnnotation`, but there is no public top-level `findAnnotation` extension — use `getAnnotationByClassId` or `hasAnnotationSafe`.)

For frequent annotation checks, use the predicate system instead — see [`fir-predicate-system`](../fir-predicate-system/guide.md).

### Resolving a referenced type

```kotlin
val typeRef = declaration.returnTypeRef
val classId = typeRef.coneType.classId  // org.jetbrains.kotlin.name.ClassId? or null
```

### Cross-module classes

```kotlin
val symbol = context.session.symbolProvider.getClassLikeSymbolByClassId(MyClassId)
```

### Recursing into expressions

`FirNamedFunctionChecker` only fires on the function declaration; to inspect its body, walk the FIR tree from `function.body`. For per-expression checks the `expressionCheckers` bucket is more efficient — the FIR pipeline visits expressions for you.

## Severity choice

| Use | Helper |
|---|---|
| Code that *will* break or is definitely wrong | `error*` |
| Code that is suspicious, deprecated, or stylistically discouraged | `warning*` |
| Compiler-internal sanity check that should never fire on user code | use the generic logger, not a diagnostic |

## Common gotchas

### `IllegalStateException: Diagnostic factory '...' was not registered`

You forgot `registerDiagnosticContainers(...)` in the registrar, or you registered a different container than the one your checker reports against. Also fires if you copy a factory between containers without updating the import.

### Diagnostic message is `"<no message>"` or shows the raw factory name

You did not provide a `BaseDiagnosticRendererFactory` mapping for that factory. Add an entry to your container's `getRendererFactory().MAP`.

### IDE shows the diagnostic but `kotlinc` doesn't (or vice versa)

The IDE uses the same FIR plugin path *only* if your plugin is wired through `KotlinCompilerPluginSupportPlugin` — see [`gradle-plugin-integration`](../gradle-plugin-integration/guide.md). With raw `-Xplugin=`, the IDE will not run your checker.

### Checker fires on every member of every class, performance suffers

Use the predicate system to filter by annotation up-front:

```kotlin
override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(LookupPredicate.create { annotated(setOf(MUST_BE_FINAL_ANNOTATION_FQ)) })
}
```

then check `session.predicateBasedProvider.matches(predicate, declaration)` instead of walking annotations every time. Details in [`fir-predicate-system`](../fir-predicate-system/guide.md).

### Reporting on `null` or fake source crashes / produces confusing diagnostics

`reporter.reportOn(source, factory, ...)` requires non-null source — a `null` triggers `IllegalArgumentException("source must not be null")` from `requireNotNull` (`compiler/frontend.common/src/.../KtDiagnosticReportHelpers.kt:144`). Synthesised declarations often have `source = null`. Always guard:

```kotlin
val src = declaration.source ?: return
reporter.reportOn(src, MyDiagnostics.MY_ERROR)   // inside a context(DiagnosticReporter) checker, no trailing context arg
```

Fake source elements (from desugaring — for-loops, delegated property accessors, generated enum members, etc.) have `source.kind is KtFakeSourceElementKind`. Reporting on them produces confusing squiggles on synthetic ranges. Bail explicitly when relevant:

```kotlin
if (declaration.source?.kind is KtFakeSourceElementKind) return
```

To filter to only user-written declarations, prefer `declaration.origin.fromSource` over the more restrictive `origin == FirDeclarationOrigin.Source` (which excludes Java sources, plugin-generated, data-class members, etc.).

### `error0<KtClass>` vs `error0<PsiElement>`

The type parameter restricts the source element type at the report site. Picking a too-narrow type (`KtNamedFunction` when your factory will fire from both `KtNamedFunction` and `KtPropertyAccessor`) causes a compile error at the report call. Use `PsiElement` for maximally generic factories, narrow types when you want type-safety.

### `PsiElement` import path: shaded under `kotlin-compiler-embeddable`

When using `PsiElement` as the source-element type parameter (`error0<PsiElement>`), the import path **must be the shaded one**:

```kotlin
// Wrong — fails to compile against kotlin-compiler-embeddable:
import com.intellij.psi.PsiElement

// Right — IntelliJ classes are relocated under org.jetbrains.kotlin in the embeddable artifact:
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
```

The `kotlin-compiler-embeddable` JAR shades `com.intellij.*` to avoid clashes with JetBrains IDE plugins; if you depend on `kotlin-compiler-embeddable` (which compiler-plugin modules typically do), the unshaded `com.intellij.psi.PsiElement` is not on the classpath and the import fails to resolve.

Worse: when this import fails inside a `context(... DiagnosticReporter)`-bearing checker function, the compiler error you actually see may say "context parameter is unresolved" or "no context argument found" — masking the real problem (broken `PsiElement` import). If you see context-parameter errors after adding a `error0<PsiElement>` factory, **check this import first**.

> ⚠️ **This shaded `PsiElement` reference is `reified`, so it is baked into your plugin's bytecode** — which means a plugin built against the shaded `kotlin-compiler-embeddable` cannot also run under the **un-shaded** `kotlin-compiler` that the official test framework uses, and vice versa (`NoClassDefFoundError: com/intellij/psi/PsiElement`). If you test with both the Gradle sample (Pattern A, embeddable) and the official framework (Pattern B, un-shaded), read the "reified `PsiElement`" exception in [`compiler-plugin-testing`](../compiler-plugin-testing/guide.md): compile un-shaded for the framework, then `shadowJar`-relocate `com.intellij` → `org.jetbrains.kotlin.com.intellij` for distribution and for the `-Xplugin=` sample.

### Walking a class's members: `.declarations` requires `@OptIn(DirectDeclarationsAccess::class)`

Direct access to `FirRegularClass.declarations` (or `FirFile.declarations`, `FirScript.declarations`) is gated by `@RequiresOptIn(DirectDeclarationsAccess::class)` since Kotlin 2.x. The opt-in's KDoc warns:

> Direct access to .declarations can be risky for various reasons:
> - one doesn't see any plugin-generated declarations
> - in IDE mode, there are no guarantees about a reached resolve phase

If your checker needs to iterate properties or member functions, **prefer the scope-based APIs**:

```kotlin
// Wrong (warning + misses plugin-generated members in some phases):
@OptIn(DirectDeclarationsAccess::class)
val props = (declaration as FirRegularClass).declarations.filterIsInstance<FirProperty>()

// Right (sees plugin-generated members, phase-safe):
import org.jetbrains.kotlin.fir.declarations.declaredProperties
import org.jetbrains.kotlin.fir.declarations.declaredFunctions

val props = declaration.symbol.declaredProperties(context.session)
val funs = declaration.symbol.declaredFunctions(context.session)
```

Note: both are **functions** taking `session: FirSession` (plus an optional `memberRequiredPhase`), not property accessors. Their package is `org.jetbrains.kotlin.fir.declarations`, not the `.utils` subpackage.

Or for full traversal:

```kotlin
declaration.symbol.processAllDeclarations(context.session) { memberSymbol ->
    when (memberSymbol) {
        is FirPropertySymbol -> /* … */
        is FirNamedFunctionSymbol -> /* … */
    }
}
```

The opt-in path (`@OptIn(DirectDeclarationsAccess::class)`) is acceptable for renderers / debug dumps where you genuinely want only the source declarations and won't run during plugin coordination, but checkers should default to the scope-based API.

**Detecting an existing companion** is a related case where `declaredProperties` / `declaredFunctions` don't help — they walk callable members, not nested classifiers. To check whether a class already has a companion (e.g. before deciding whether your `FirDeclarationGenerationExtension` should synthesise one):

```kotlin
val hasUserCompanion = (declaration as FirRegularClass).companionObjectSymbol != null
```

`companionObjectSymbol` is a member `val` on `FirRegularClass` (and on `FirRegularClassSymbol`) — **no import is needed** beyond `FirRegularClass` itself. (Earlier drafts of this skill mentioned an `org.jetbrains.kotlin.fir.declarations.utils.companionObjectSymbol` import; that path does not exist.) It does NOT require the `DirectDeclarationsAccess` opt-in and returns `null` if no companion exists — including the case where your plugin will later synthesise one. For a "generate-only-if-absent" idiom, this is the correct pre-check.

## Relation to other extensions

- **Synthesise declarations from annotations** → [`fir-declaration-generation-extension`](../fir-declaration-generation-extension/guide.md)
- **Make annotated classes implement an interface** → [`fir-supertype-generation-extension`](../fir-supertype-generation-extension/guide.md)
- **Make annotated functions `open`** → [`fir-status-transformer-extension`](../fir-status-transformer-extension/guide.md)
- **Need to filter by annotation efficiently** → [`fir-predicate-system`](../fir-predicate-system/guide.md)
- **Test the diagnostic** → [`compiler-plugin-testing`](../compiler-plugin-testing/guide.md) (FIR diagnostic tests)

## What this skill does NOT cover

- Predicate registration syntax in depth — see [`fir-predicate-system`](../fir-predicate-system/guide.md)
- The full `Fir*Checker` alias catalogue — see the generated `kotlin/compiler/fir/checkers/gen/org/jetbrains/kotlin/fir/analysis/checkers/declaration/FirDeclarationCheckerAliases.kt` and `.../expression/FirExpressionCheckerAliases.kt` for the live list
- IDE integration of diagnostics (Analysis API for in-IDE highlighting) — see Kotlin's official docs
- Suppressing existing built-in diagnostics — that requires a different mechanism (`FirSuppressionExtension`-like, not currently exposed publicly)
