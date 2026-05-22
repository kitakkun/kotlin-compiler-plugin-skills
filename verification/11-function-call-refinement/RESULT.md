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
