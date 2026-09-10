# Verification: 11-function-call-refinement

## Result: PARTIAL — frontend works, IR codegen fails

The plugin's `FirFunctionCallRefinementExtension.intercept()` and `transform()`
both fire, and the FIR resolver successfully refines the call's return type
from `Box<*>` to `Box<RefinedSchema>` (a synthetic local class). However,
the sample fails during fir2ir / JVM codegen with an NPE that is
**internal to the compiler's local-class handling** — the IR backend cannot
process the synthetic local class produced by this extension.

## What was verified

### Confirmed (frontend layer)

When compiling the sample, the plugin's stderr logs fire:

```
[RefineCallExtension] intercept fired for com/example/box
[RefineCallExtension] transform fired for com/example/box, returnType=com/example/Box<<local>/RefinedSchema>
```

This confirms:

1. **`intercept(callInfo, symbol)` is invoked** for a call to a function
   annotated `@RefineMe`, and returning a `CallReturnType` with a refined
   `FirResolvedTypeRef` causes the resolver to substitute it.
2. **`transform(call, originalSymbol)` is invoked** during call completion
   and the resolver's reported `call.resolvedType` is the refined
   `Box<RefinedSchema>` — proving the type substitution propagated.
3. **The frontend (FIR) accepts the refined type end-to-end.** The FIR dump
   produced before the IR phase contains:

   ```
   val b: Box<RefinedSchema> = run<Box<RefinedSchema>>(block = ...)
   ```

   i.e. the local variable's static type is `Box<RefinedSchema>`, the very
   thing the SKILL doc claims this extension exists to do.

### Not verified (IR layer)

The compilation **fails before producing class files**. Two distinct IR-stage
failures appear depending on whether the synthetic schema class is included
as a statement in the wrapping `run { ... }` lambda:

#### Failure mode A: schema class included as a body statement

```
java.lang.NullPointerException
  at org.jetbrains.kotlin.fir.backend.generators.ClassMemberGenerator.convertClassContent(ClassMemberGenerator.kt:70)
  at org.jetbrains.kotlin.fir.backend.Fir2IrVisitor.visitRegularClass(Fir2IrVisitor.kt:205)
```

`convertClassContent`, line 70 in 2.3.21:

```kotlin
val irPrimaryConstructor = primaryConstructor?.let {
    declarationStorage.getCachedIrConstructorSymbol(it)!!.owner
}
```

`getCachedIrConstructorSymbol` returns `null`. The `!!` panics.

This happens because the schema class is referenced from the variable's type
(`Box<RefinedSchema>`) **before** it is encountered as a statement in the
lambda body. The first reference triggers
`Fir2IrClassifierStorage.createAndCacheLocalIrClassOnTheFly`, which only
caches the IrClass — it does **not** create or cache the IR constructor. By
the time fir2ir visits the same FirRegularClass as a body statement,
`getCachedIrLocalClass` returns the stub IR class but
`getCachedIrConstructorSymbol(primaryConstructor)` is empty, hence the NPE.

The schema class **does** have a primary constructor in its FIR
`declarations` list (built via `buildPrimaryConstructor` and added during
`buildRegularClass`). The constructor is also reachable via
`klass.primaryConstructorIfAny(session)`. The bug is that the on-the-fly
local-class IR creation path does not wire the constructor into the IR cache.

#### Failure mode B: schema class NOT included as a body statement

Removing the schema class from the lambda body's `statements` clears the
NPE but the next phase (JVM codegen) errors out:

```
java.lang.IllegalStateException: Local class-like declaration should have its name computed in InventNamesForLocalClasses: <stub>.RefinedSchema
Ensure that any lowering that transforms elements with local class-like declaration info (classes, function references) invokes `copyAttributes` on the transformed element.
  at org.jetbrains.kotlin.backend.jvm.mapping.IrTypeMapper.computeClassLikeDeclarationInternalName(IrTypeMapper.kt:102)
```

The schema IrClass exists but is parented to a stub function
(`Fir2IrClassifiersGenerator.temporaryParent`, an `IrSimpleFunction` named
`<stub>` with an external package fragment for a parent). It is not part of
the module fragment's IR tree, so `JvmInventNamesForLocalClasses` lowering
never visits it and `IrClass.localClassType` is never set. JVM bytecode
mapping then panics when trying to write a name for the type reference.

#### Why an `IrGenerationExtension` workaround does not work

`moduleFragment.acceptChildrenVoid` in an `IrGenerationExtension.generate(...)`
implementation cannot reach the schema class — its parent chain ends at an
`IrExternalPackageFragmentImpl`, not at any file in the module fragment.
Pre-setting `localClassType` from a plugin-visible IR pass requires reaching
the class first, which requires walking via call type arguments rather than
the regular tree, and re-parenting it correctly into a real declaration —
all of which goes well beyond what the SKILL claims this extension supports.

## What was tried

1. **Sandbox-style schema class with no primary constructor** (matches
   `DataFrameLikeCallsRefinementExtension.kt` from `kotlin/plugins/plugin-sandbox`).
   Frontend works. IR `LocalDeclarationsLowering` then fails with
   `AssertionError: Expected at least one constructor calling super`.

2. **Schema class with a primary constructor added via `replaceDeclarations(...)`
   after `buildRegularClass`.** Frontend works. IR fails with the convertClassContent
   NPE described above (mode A), regardless of whether the constructor was created via
   the `createConstructor` plugin-utils helper or built manually.

3. **Schema class with constructor added inside `buildRegularClass { declarations += ... }`.**
   Same NPE.

4. **Schema class as a body statement vs not as a body statement.** The
   `IllegalStateException` and the NPE are the only two outcomes.

5. **Resolved supertype ref vs `FirImplicitAnyTypeRef(null)`** for the schema
   class's `superTypeRefs`. No change.

6. **Auxiliary `IrGenerationExtension` to set `localClassType` on the
   schema class.** The class is not reachable via `moduleFragment.acceptChildrenVoid`
   — see above.

## Reference behaviour

The Kotlin sandbox tests for the **same** extension
(`plugins/plugin-sandbox/testData/diagnostics/receivers/callShapeBasedInjector.kt`)
are gated with `// RUN_PIPELINE_TILL: FRONTEND` — i.e. the sandbox test
suite **never exercises IR or codegen** for this extension. The
production-grade `kotlin-dataframe` plugin uses the extension, but it is
paired with a substantial body of dataframe-specific IR rewriting that
together avoid the issue (and the plugin's tests against `:dataframe` jars
exercise full codegen). Reproducing the dataframe IR-side workaround is
~thousands of lines of code outside the scope of this verification.

The SKILL doc warns that the extension is `@FirExtensionApiInternals` and
"highly unstable", and that `transform` "needs to generate call to `let`
... and put all generated declarations used in `FirResolvedTypeRef` in
statements." Doing exactly that produces the NPE in Mode A; not doing it
produces the IllegalStateException in Mode B. **There appears to be no
combination of FIR-level construction that makes the extension work
end-to-end through JVM codegen in 2.3.21 without an accompanying IR-side
plugin.**

## Recommendation for the SKILL doc

The SKILL.md "How to choose" entry currently reads:

> Refine the return type of a call to narrow the static type at the call
> site → this skill

This claim is **achievable at the FIR layer** (verified) but **not
achievable end-to-end with only this extension**. The skill doc should be
updated to:

1. State that `FirFunctionCallRefinementExtension` alone cannot produce
   compilable bytecode for the data-frame-style schema-inference pattern.
   It must be paired with one of:
   - An `IrGenerationExtension` that locates and reparents the synthetic
     local classes into a real IR file/class so JVM lowerings see them, and
     also wires up their constructors before fir2ir runs out (likely
     impossible without compiler patches), or
   - A pre-existing top-level placeholder type that the plugin reuses (e.g.
     a sealed type with a fixed set of subclasses), refining only to those
     types — this avoids "synthetic local class" entirely.
2. Move the "How to choose" entry from "this skill" to "this skill **plus**
   `ir-call-rewriting`/significant IR-side plumbing" or remove it as a
   recommended approach.
3. Strengthen the existing stability warning: "highly unstable" understates
   it — the IR side does not work for the documented use case in 2.3.21.

## Build & run

```
$ cd verification/11-function-call-refinement
$ ../gradlew :sample:compileKotlin
[RefineCallExtension] intercept fired for com/example/box
[RefineCallExtension] transform fired for com/example/box, returnType=com/example/Box<<local>/RefinedSchema>
...
Caused by: java.lang.NullPointerException
  at org.jetbrains.kotlin.fir.backend.generators.ClassMemberGenerator.convertClassContent(ClassMemberGenerator.kt:70)
...
BUILD FAILED
```

## Files

- `plugin/src/main/kotlin/com/example/refineplugin/RefinePluginComponentRegistrar.kt`
- `plugin/src/main/kotlin/com/example/refineplugin/fir/RefineFirExtensionRegistrar.kt`
- `plugin/src/main/kotlin/com/example/refineplugin/fir/RefineCallExtension.kt`
- `plugin/src/main/resources/META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar`
- `sample/src/main/kotlin/Main.kt`

## Re-run on Kotlin 2.4.20

### Result: PASS — frontend refinement works AND the sample compiles, links and runs

All `build.gradle.kts` pins were bumped from 2.3.21 to 2.4.20 (`kotlin("jvm")`,
`kotlin-compiler-embeddable`). Nothing else changed initially.

### Failure seen with the unchanged 2.3.21 plugin code

`../gradlew --no-daemon -q clean :sample:run` failed while compiling the sample —
the same **Mode A** NPE as on 2.3.21, one line lower after upstream drift:

```
e: org.jetbrains.kotlin.util.FileAnalysisException: While analysing .../sample/src/main/kotlin/Main.kt:11:5:
   org.jetbrains.kotlin.utils.exceptions.KotlinIllegalArgumentExceptionWithAttachments:
   Exception was thrown during transformation of class org.jetbrains.kotlin.fir.expressions.impl.FirFunctionCallImpl
Caused by: java.lang.NullPointerException
  at org.jetbrains.kotlin.fir.backend.generators.ClassMemberGenerator.convertClassContent(ClassMemberGenerator.kt:76)
  at org.jetbrains.kotlin.fir.backend.Fir2IrVisitor.visitRegularClass(Fir2IrVisitor.kt:205)
  ...
  at org.jetbrains.kotlin.fir.backend.Fir2IrVisitor.visitAnonymousFunction(Fir2IrVisitor.kt:513)      <- the `run { }` lambda
  at org.jetbrains.kotlin.fir.backend.generators.CallAndReferenceGenerator.convertArgument(CallAndReferenceGenerator.kt:1149)
  at org.jetbrains.kotlin.fir.backend.generators.CallAndReferenceGenerator.convertToIrCall(CallAndReferenceGenerator.kt:733)
  at org.jetbrains.kotlin.fir.backend.Fir2IrVisitor.visitLocalVariable(Fir2IrVisitor.kt:563)              <- `val b = box()`
```

So this is **not** a 2.4.20 regression: the 2.3.21 result above was already
PARTIAL with exactly this NPE. The 2.4.20-specific changes named in the task
(`KtFakeSourceElementKind.PluginGenerated` becoming sealed, the new distinct-source-element
KDoc, `CandidateFactory.replaceFromPluginsIfNeeded` / `FirExpressionsResolveTransformer`
line drift) do not touch this code path at all: the 2.3.21 plugin never referenced
`PluginGenerated` as a value and never set `source` on its generated declarations, so it
still compiled against 2.4.20 unchanged; and the sandbox reference
`DataFrameLikeCallsRefinementExtension.kt` is byte-identical between v2.4.10 and v2.4.20.

### Root cause (the null)

`ClassMemberGenerator.convertClassContent` dereferences the cached IR constructor of the
class's primary constructor:

```kotlin
// kotlin/compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/generators/ClassMemberGenerator.kt:73-76 (v2.4.20)
val primaryConstructor = allDeclarations.firstOrNull { it is FirConstructor && it.isPrimary } as FirConstructor?
val irPrimaryConstructor = primaryConstructor?.let { declarationStorage.getCachedIrConstructorSymbol(it)!!.owner }
```

`allDeclarations` starts from `klass.declarations` (`:66`), where the plugin had put its
hand-built `buildPrimaryConstructor { ... }` — so `primaryConstructor` is non-null. But
`getCachedIrConstructorSymbol` (`Fir2IrDeclarationStorage.kt:479-481`, a plain
`constructorCache[constructor]` lookup) returns `null`, because the IR constructor was
never created. The creation site is `Fir2IrConverter.processClassMembers`, which is
reached for a local class either on the on-the-fly path
(`Fir2IrClassifierStorage.createAndCacheLocalIrClassOnTheFly` → `Fir2IrClassifiersGenerator.createLocalIrClassOnTheFly`,
`Fir2IrClassifiersGenerator.kt:194-208`) when the class is first met through the variable's
*type* `Box<RefinedSchema>`, or via `visitRegularClass` (`Fir2IrVisitor.kt:198-210`). It does
**not** look at `klass.declarations` for the primary constructor — it asks the declared member
scope:

```kotlin
// kotlin/compiler/fir/fir2ir/src/org/jetbrains/kotlin/fir/backend/Fir2IrConverter.kt:230-232 (v2.4.20)
val irConstructor = klass.primaryConstructorIfAny(session)?.let {
    declarationStorage.createAndCacheIrConstructor(it.fir, { irClass }, isLocal = klass.isLocal)
}
```

`primaryConstructorIfAny(session)` → `constructors(session)` →
`session.declaredMemberScope(this, memberRequiredPhase = null).processDeclaredConstructors`
(`kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/declarations/declarationUtils.kt:37-41,173-175`).
And the declared member scope of a class whose origin is `FirDeclarationOrigin.Plugin`
(`generated = true`, `kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/declarations/FirDeclarationOrigin.kt:74`)
is built **exclusively from `FirDeclarationGenerationExtension`s** and ignores
`klass.declarations`:

```kotlin
// kotlin/compiler/fir/providers/src/org/jetbrains/kotlin/fir/scopes/impl/FirDeclaredMemberScopeProvider.kt:82-94 (v2.4.20)
private fun createDeclaredMemberScope(klass: FirClass, ...): FirContainingNamesAwareScope {
    val origin = klass.origin
    return when {
        origin.generated -> {
            FirGeneratedClassDeclaredMemberScope.create(useSiteSession, klass.symbol,
                regularDeclaredScope = null, scopeForGeneratedClass = true) ?: FirTypeScope.Empty
        }
        else -> { val baseScope = FirClassDeclaredMemberScopeImpl(useSiteSession, klass, ...) ... }
```

With no declaration-generation extension registered,
`FirGeneratedClassDeclaredMemberScope.create` (`FirGeneratedScopes.kt:50`) returns `null`,
the scope is `FirTypeScope.Empty`, `primaryConstructorIfAny` is `null`, no IR constructor
is created or cached, and the later `!!` in `convertClassContent` throws. The
`FirConstructor` branch of `processMemberDeclaration` explicitly skips primary
constructors too ("the primary constructor was already created in `processClassMembers`",
`Fir2IrConverter.kt:532-534`), so there is no second chance.

The same two lines exist at v2.3.21 (`FirDeclaredMemberScopeProvider.kt:88-94`,
`Fir2IrConverter.kt:240`), which is why the 2.3.21 write-up saw the identical NPE. The
2.3.21 analysis ("on-the-fly local-class IR creation does not wire the constructor") was
wrong about *where* the constructor got lost: it is the plugin-origin declared-member-scope
rule, not the on-the-fly path.

This is also exactly why kotlin-dataframe works: its schema/scope/token classes carry no
constructor in `declarations` either — the constructor comes from a
`FirDeclarationGenerationExtension` (`TokenContentGenerator.getCallableNamesForClass` adds
`SpecialNames.INIT` for classes with `callShapeData`, `generateConstructors` returns
`createConstructor(context.owner, DataFrameTokenContentKey, isPrimary = true)`;
`kotlin/plugins/kotlin-dataframe/kotlin-dataframe.k2/src/org/jetbrains/kotlinx/dataframe/plugin/extensions/TokenContentGenerator.kt:98-101,151-152`).
`processClassMembers` and `convertClassContent` both pick generated members up through
`klass.generatedMembers(session)` (`generatedDeclarationsUtils.kt:31-51`,
`ClassMemberGenerator.kt:67-70`).

### Fix (plugin only; `Main.kt` untouched)

1. **Removed the hand-built primary constructor** from `RefinedSchema.declarations`.
2. **Added `RefineSchemaConstructorGenerator : FirDeclarationGenerationExtension`** and
   registered it (`+::RefineSchemaConstructorGenerator`). For every local class whose origin
   is `FirDeclarationOrigin.Plugin(RefinePluginKey)` it reports `SpecialNames.INIT` from
   `getCallableNamesForClass` and returns
   `createConstructor(owner, RefinePluginKey, isPrimary = true, generateDelegatedNoArgConstructorCall = true)`
   from `generateConstructors`. No IR-side body filler is needed: fir2ir synthesizes the
   constructor body from `FirConstructor.delegatedConstructor` (`Any()`) plus an
   `IrInstanceInitializerCall` even when `body == null`
   (`ClassMemberGenerator.convertFunctionContent`, `ClassMemberGenerator.kt:136-163`).
3. **Distinct source elements (2.4.20 KDoc contract)** — the schema class now gets
   `source = callInfo.callSite.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated.Custom(RefineSourceElementKind.SchemaClass(name)))`
   and the wrapper lambda `PluginGenerated.Default`, mirroring
   `FunctionCallTransformer.kt` at v2.4.20. This was not needed for the crash but is what
   the guide / KDoc (`FirFunctionCallRefinementExtension.kt:35-44`,
   `KtSourceElement.kt:889-917`) now require.
4. `sample/build.gradle.kts`: `mainClass` corrected to `com.example.MainKt` (`Main.kt`
   declares `package com.example`; on 2.3.21 the build never got as far as `:sample:run`, so
   this was latent).

The schema class is still emitted as a statement of the `run { }` lambda (the
2.3.21 "Mode B" `JvmInventNamesForLocalClasses` failure returns if it is not).

### Passing output

```
$ cd verification/11-function-call-refinement
$ ../gradlew --no-daemon clean :sample:run -Pkotlin.compiler.execution.strategy=in-process
> Task :plugin:compileKotlin
> Task :plugin:jar
> Task :sample:compileKotlin
[RefineCallExtension] intercept fired for com/example/box
[RefineCallExtension] transform fired for com/example/box, returnType=com/example/Box<<local>/RefinedSchema>
> Task :sample:run

BUILD SUCCESSFUL in 8s
```

(`-Pkotlin.compiler.execution.strategy=in-process` only makes the plugin's stderr visible;
the plain `../gradlew --no-daemon -q clean :sample:run` also exits 0. The one printed line is
empty because `object : Box<Any> {}` has an empty `Class.simpleName`.)

The generated local class reaches bytecode (`javap` on our own output):

```
sample/build/classes/kotlin/main/com/example/MainKt$main$b$1$RefinedSchema.class

public final class com.example.MainKt$main$b$1$RefinedSchema {
  public com.example.MainKt$main$b$1$RefinedSchema();
    Code:
       0: aload_0
       1: invokespecial #8   // Method java/lang/Object."<init>":()V
       4: return
}
```

The 2.3.21 "Recommendation for the SKILL doc" above is therefore superseded: this
extension **can** produce compilable bytecode for the schema-inference pattern on its own,
provided the generated local class's members (at minimum its constructor) are supplied
through a `FirDeclarationGenerationExtension` rather than through `declarations`.

## Skill feedback

**Would the guide + CHANGES have led a plugin author to the fix? No.** Read carefully, they
are accurate about the 2.4.20 API delta but silent on the fact that actually breaks the
build, and in one place they point the wrong way.

What was missing:

- **The load-bearing rule is absent from the skill entirely.** Nothing in
  `fir-function-call-refinement-extension/guide.md`, its `CHANGES.md`, or
  `fir-declaration-generation-extension/guide.md` says that a class with
  `FirDeclarationOrigin.Plugin` origin has its declared member scope computed only from
  `FirDeclarationGenerationExtension`s (`FirDeclaredMemberScopeProvider.kt:88-94`), so
  members added to `FirRegularClass.declarations` by hand are invisible to
  `primaryConstructorIfAny` / `constructors(session)` / `unsubstitutedScope()` and to fir2ir's
  `processClassMembers`. This single fact explains the 2.3.21 PARTIAL result, the NPE here,
  and why kotlin-dataframe pairs `FunctionCallTransformer` with `TokenContentGenerator`. The
  guide should state it under "Generated declarations must be **local**" and add a gotcha:
  "hand-built `declarations` on a plugin-origin class are ignored — supply members via a
  companion `FirDeclarationGenerationExtension` keyed on your `GeneratedDeclarationKey`".
- **The guide never mentions that a companion `FirDeclarationGenerationExtension` is part of
  the pattern.** It presents kotlin-dataframe as "this extension for types, an IR transformer
  for the actual schema-aware codegen" and names only `FunctionCallTransformer.kt` as the
  reference impl. The constructor/property *providers* (`TokenContentGenerator`) and the
  need for at least one super-calling constructor so `LocalDeclarationsLowering` accepts the
  class are not described. The "End-to-end shape" sketch builds a bare
  `buildRegularClass { ... }` and says nothing about members.
- **The sandbox reference (`DataFrameLikeCallsRefinementExtension.kt`) is misleading for
  codegen.** The guide recommends reading it "for the full builder code", but its classes
  have no constructor and its tests are `RUN_PIPELINE_TILL: FRONTEND`; copying it produces
  the `LocalDeclarationsLowering` assertion on the first IR run (see "What was tried" #1
  above). The guide should flag that the sandbox impl is frontend-only.
- **The 2.4.20 section of `CHANGES.md` and the guide's new "distinct source elements"
  gotcha are accurate but orthogonal to this failure.** They correctly describe the sealed
  `PluginGenerated` API, `FirDistinctSourceElementsHandler`, and the dataframe migration, and
  a plugin author following them writes correct `source` assignments (we did). The closing
  line — the intercept/transform sites are "unchanged apart from line drift" — is true and
  correctly implies no migration for a plugin that never used `PluginGenerated` as a value.
  An author whose plugin already died in fir2ir gets no help from this section, and the
  task framing ("known 2.4.20 changes in this area") invites a wild-goose chase through
  `CandidateFactory` / `FirExpressionsResolveTransformer`, which are irrelevant.
- **The stability framing steers away from the fix.** The frontmatter's "the IR-codegen
  incompleteness" and the 2.3.21 RESULT both concluded the IR side "does not work"; with the
  missing rule above it does. That language should be revised to: codegen works when the
  generated local classes get their members from a `FirDeclarationGenerationExtension` and
  are emitted as statements of the wrapper lambda.

Minor: `ClassId.isLocal` produces a K2 deprecation warning ("use `firClassLikeSymbol.isLocal`");
the declaration-generation guide's examples could point at
`org.jetbrains.kotlin.fir.declarations.utils.isLocal` as the symbol-level accessor.

## Files (2.4.20 re-run)

- `plugin/src/main/kotlin/com/example/refineplugin/fir/RefineCallExtension.kt` — constructor removed from `declarations`; `RefineSchemaConstructorGenerator` and `RefineSourceElementKind` added; `PluginGenerated.Custom`/`Default` sources set
- `plugin/src/main/kotlin/com/example/refineplugin/fir/RefineFirExtensionRegistrar.kt` — registers the generator
- `plugin/build.gradle.kts`, `sample/build.gradle.kts` — 2.4.20 pins; `mainClass = com.example.MainKt`
