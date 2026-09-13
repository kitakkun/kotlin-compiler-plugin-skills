# 14-cross-module-ir-class-visibility — Verification Result

**Status: PASS**

A whole class synthesized at IR time (constructor + `val` property + member function),
registered with a single `registerClassAsMetadataVisible(irClass)` call, is
referenceable from a downstream module's source. The downstream module does not apply
the plugin. The constructor, the property, and the function are all resolved by
module-b's FIR through module-a's published metadata, and all three work at runtime.

The optional nested-class case (`Foo.Nested` generated inside the source-declared `Foo`,
registered with its own call) also passes.

## How verified

From a fully clean state (Kotlin 2.4.20, JDK 21 toolchain):

```
$ ../gradlew --no-daemon -q clean :module-b:run
value=x describe=holder:x
nested-in-Foo
```

`module-b/src/main/kotlin/com/example/Main.kt`:

```kotlin
fun main() {
    val holder = FooHolder("x")                                        // IR-generated constructor
    println("value=${holder.value} describe=${holder.describe()}")    // IR-generated property + function
    println(Foo.Nested().ping())                                       // IR-generated nested class
}
```

`module-b` has no `-Xplugin=` and no `compilerPlugin` configuration; it only declares
`implementation(project(":module-a"))`.

### Negative control

With the single line
`pluginContext.metadataDeclarationRegistrar.registerClassAsMetadataVisible(holder)`
commented out (everything else unchanged), `clean :module-b:run` fails at compile time:

```
e: .../module-b/src/main/kotlin/com/example/Main.kt:6:18 Unresolved reference 'FooHolder'.
> Task :module-b:compileKotlin FAILED
```

So the one registrar call is the switch that makes the class — and, transitively, its
constructor, property, and function — visible to downstream FIR.

### Bytecode check

```
$ javap -p module-a/build/classes/kotlin/main/com/example/FooHolder.class
public final class com.example.FooHolder {
  private final java.lang.String value;
  public final java.lang.String getValue();
  public com.example.FooHolder(java.lang.String);
  public final java.lang.String describe();
}
$ javap -p 'module-a/build/classes/kotlin/main/com/example/Foo$Nested.class'
public final class com.example.Foo$Nested {
  public com.example.Foo$Nested();
  public final java.lang.String ping();
}
```

`describe()` and `ping()` are instance methods (dispatch receiver attached), so the
metadata written by the registrar and the bytecode agree — no
`IncompatibleClassChangeError` at runtime.

## Key source

`plugin/src/main/kotlin/com/example/irgen/GenerateHolderIrGenerationExtension.kt`:

```kotlin
override fun visitFile(declaration: IrFile) {
    val annotated = declaration.declarations.filterIsInstance<IrClass>().filter { it.hasAnnotation(ANNOTATION_FQN) }
    for (source in annotated) {
        val holder = buildHolderClass(pluginContext, declaration, source)
        declaration.declarations += holder
        // Single call: the registrar walks constructors, functions, and properties itself.
        pluginContext.metadataDeclarationRegistrar.registerClassAsMetadataVisible(holder)

        val nested = buildNestedClass(pluginContext, source)
        source.declarations += nested
        pluginContext.metadataDeclarationRegistrar.registerClassAsMetadataVisible(nested)
    }
}
```

`buildHolderClass` follows guide steps 1–5 in this order:

```kotlin
val holder = pluginContext.irFactory.buildClass { name = ...; kind = ClassKind.CLASS; ... }.apply {
    parent = file
    superTypes = listOf(irBuiltIns.anyType)
    createThisReceiverParameter()
}
// property first, so the constructor body can assign its backing field
val valueProperty = holder.addProperty { name = Name.identifier("value"); ... }
val valueField = valueProperty.addBackingField { type = irBuiltIns.stringType; isFinal = true }
valueProperty.addDefaultGetter(holder, irBuiltIns)

holder.addConstructor { isPrimary = true; visibility = DescriptorVisibilities.PUBLIC }.apply {
    val valueParameter = addValueParameter("value", irBuiltIns.stringType)
    body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
        +irDelegatingConstructorCall(irBuiltIns.anyClass.owner.constructors.single())
        +IrInstanceInitializerCallImpl(startOffset, endOffset, classSymbol = holder.symbol, type = irBuiltIns.unitType)
        +irSetField(irGet(holder.thisReceiver!!), valueField, irGet(valueParameter))
    }
}

holder.addFunction { name = Name.identifier("describe"); returnType = irBuiltIns.stringType; ... }.apply {
    val dispatchReceiver = createDispatchReceiverParameterWithClassParent()   // NOT added by the DSL overload
    parameters = listOf(dispatchReceiver)
    body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
        +irReturn(irConcat().apply {
            arguments += irString("holder:")
            arguments += irGetField(irGet(dispatchReceiver), valueField)
        })
    }
}
```

## Nested class (optional case)

Tried and it works: `class Nested { fun ping(): String }` built with `parent = source`
(the source-declared `Foo`), appended to `source.declarations`, then registered with
its **own** `registerClassAsMetadataVisible(nested)` call — because the outer class is
source-declared and is not itself registered, the recursive walk of section 6 never
reaches the nested class, so it must be registered directly. This is the same shape as
upstream `GeneratedTopLevelClassIrGenerator` case (6) "nested class inside a
source-declared outer". Module-b resolves `Foo.Nested()` and `ping()` and prints
`nested-in-Foo`. The registrar found the containing FIR class of `Foo` via
`irClass.parent.toFirContainingDeclaration()` without any extra wiring.

Not tried: an `inner` class (needs an outer-typed dispatch receiver on the constructor
and the `[own…, captured…]` type-argument convention; see upstream cases 9–14).

## Skill feedback

The guide was sufficient to get a PASS on the first compile — the numbered steps and the
constraint list in section 6 are accurate for 2.4.20. Concrete issues found:

1. **The "IR-only pattern" intro contradicts section 6.** The intro says the IR-only
   class is "Source-invisible from the same module (FIR has no record of it) ... Used
   when a plugin needs runtime helpers that user code never refers to directly" and
   "Pattern 2 has more limitations (no source visibility, no IDE awareness, no checker
   support)". Since 2.4.20 that is only true *within the same module*: with one
   `registerClassAsMetadataVisible` call, downstream modules' source (and their IDE)
   see the class like any library class (verified here: constructor, property, function,
   and a nested class all resolve from module-b). The intro and the two-pattern
   comparison should be qualified the same way probe 12 asked for
   `registerFunctionAsMetadataVisible` ("no source visibility" -> "no source visibility
   in the defining module; downstream modules see it once registered").

2. **Step 4 (`addFunction`) still shows the receiver-less overload.** The snippet in
   "### 4. Add member functions" calls `newClass.addFunction { ... }` without
   `parameters = listOf(createDispatchReceiverParameterWithClassParent())`. The warning
   lives only under "Common gotchas" with the heading "...when adding to an existing
   class", which reads as if it does not apply to a freshly built class. It applies
   identically (it is a property of the `addFunction { }` overload, not of the class),
   and with `registerClassAsMetadataVisible` the mismatch would be baked into the
   metadata for every function of the class. Suggest: put the `parameters = ...` line
   into the step-4 snippet itself and retitle the gotcha to drop "to an existing class".
   (Not re-tested here; the mechanism is the same overload probe 12 hit.)

3. **Step 3 (`Add fields`) should use `addBackingField`.** The guide builds the field with
   `factory.buildField { name = this@apply.name; ... }.apply { parent = newClass }` and
   assigns `backingField` by hand. `IrProperty.addBackingField { type = ... }` (same package,
   `declarationBuilders.kt:214`, and what upstream `GeneratedTopLevelClassIrGenerator`
   uses) does that plus sets `origin = PROPERTY_BACKING_FIELD`, `visibility = PRIVATE`,
   `correspondingPropertySymbol`, and `parent` in one call. The guide's variant compiles,
   but it is easy to forget `correspondingPropertySymbol` / the origin, and the guide
   never mentions `addBackingField` at all.

4. **Step order 2 -> 3 is backwards for a class whose constructor initializes a field**,
   which is the normal case for a holder/data-carrier class. The constructor body needs
   the `IrField` (`irSetField(irGet(thisReceiver), field, irGet(param))`), so the
   property/field must be built before the constructor. The guide's constructor example
   is a no-arg `Any()` delegation only and never shows `addValueParameter` +
   `irSetField`, so an author has to work out the parameter-to-field wiring alone.
   Suggest a one-line note ("build fields before the constructor if the constructor
   assigns them") and adding the `addValueParameter` + `irSetField` two lines to step 2.

5. **Section 6 does not say what to do for a generated class nested in a
   source-declared outer.** The bullet "Do not also call the per-member
   `register*AsMetadataVisible` on members of a class you registered as a whole" plus
   "nested/inner classes are registered recursively" could be read as "never register a
   nested class yourself". When the outer is *source* code (not registered), the nested
   generated class must be registered directly with `registerClassAsMetadataVisible(nested)`
   — verified to work here; upstream case (6) does the same. Worth one sentence.

6. Things that matched exactly and need no change: the named-argument
   `IrInstanceInitializerCallImpl(startOffset, endOffset, classSymbol = ..., type = ...)`
   form; `org.jetbrains.kotlin.ir.util.constructors` + `anyClass.owner.constructors.single()`
   under `@OptIn(UnsafeDuringIrConstructionAPI::class)`; `addDefaultGetter(parentClass,
   builtIns)` living in `org.jetbrains.kotlin.ir.builders.declarations`; `createThisReceiverParameter()`;
   appending to `file.declarations` *before* the registrar call; the negative-control
   behavior ("without this, downstream modules can't see it at all").

## Files

- `plugin/src/main/kotlin/com/example/irgen/GenerateHolderComponentRegistrar.kt`
- `plugin/src/main/kotlin/com/example/irgen/GenerateHolderIrGenerationExtension.kt`
- `plugin/src/main/resources/META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar`
- `module-a/src/main/kotlin/com/example/Foo.kt` (`annotation class GenerateHolder`, `@GenerateHolder class Foo`)
- `module-b/src/main/kotlin/com/example/Main.kt` (uses `FooHolder("x")`, `.value`, `.describe()`, `Foo.Nested().ping()`)
