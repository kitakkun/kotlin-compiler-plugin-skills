# 16-ir-annotation-arguments — Verification Result

**Status: PASS**

## How verified

```
$ cd verification/16-ir-annotation-arguments
$ ../gradlew --no-daemon -q clean :sample:run
greeting greeting greeting
```

Verbose run (to confirm there are no compiler warnings from the plugin module):

```
$ ../gradlew --no-daemon clean :sample:run
> Task :sample:clean
> Task :sample:compileKotlin
> Task :sample:run
greeting greeting greeting

BUILD SUCCESSFUL in 7s
```

The sample's `tagged()` source body returns `"plugin did not run"`; the printed `greeting greeting greeting` can only
come from the plugin having read `name = "greeting"` and `times = 3` off the `@Tag` annotation at IR time.

### Scratch build: the 2.4.10 helpers are gone on 2.4.20

A temporary `plugin/src/main/kotlin/com/example/tag/ScratchOldApi.kt` (deleted afterwards, never committed) contained:

```kotlin
import org.jetbrains.kotlin.ir.util.getAnnotationStringValue
import org.jetbrains.kotlin.ir.util.getAnnotationValueOrNull
import org.jetbrains.kotlin.ir.util.getValueArgument

fun scratch(function: IrFunction, annotation: IrAnnotation, call: IrConstructorCall) {
    val a: String? = annotation.getAnnotationStringValue()
    val b: String = annotation.getAnnotationStringValue("name")
    val c: Int? = annotation.getAnnotationValueOrNull<Int>("times")
    val d = call.getValueArgument(Name.identifier("name"))
}
```

`../gradlew --no-daemon -q clean :plugin:compileKotlin` failed with exactly:

```
e: file:///.../plugin/src/main/kotlin/com/example/tag/ScratchOldApi.kt:7:37 Unresolved reference 'getAnnotationStringValue'.
e: file:///.../plugin/src/main/kotlin/com/example/tag/ScratchOldApi.kt:8:37 Unresolved reference 'getAnnotationValueOrNull'.
e: file:///.../plugin/src/main/kotlin/com/example/tag/ScratchOldApi.kt:9:37 Unresolved reference 'getValueArgument'.
e: file:///.../plugin/src/main/kotlin/com/example/tag/ScratchOldApi.kt:14:33 Unresolved reference 'getAnnotationStringValue' on receiver of type 'IrAnnotation'.
e: file:///.../plugin/src/main/kotlin/com/example/tag/ScratchOldApi.kt:15:32 Unresolved reference 'getAnnotationStringValue' on receiver of type 'IrAnnotation'.
e: file:///.../plugin/src/main/kotlin/com/example/tag/ScratchOldApi.kt:16:30 Unresolved reference 'getAnnotationValueOrNull' on receiver of type 'IrAnnotation'.
e: file:///.../plugin/src/main/kotlin/com/example/tag/ScratchOldApi.kt:17:18 Unresolved reference 'getValueArgument' on receiver of type 'IrConstructorCall'.
```

A second scratch file with only `fun scratchSymbol(annotation: IrAnnotation): IrConstructorSymbol = annotation.symbol`
compiled successfully but emitted:

```
w: .../ScratchSymbol.kt:6:79 This compiler API is deprecated
```

(that is the `@RequiresOptIn(level = WARNING)` message of `@DeprecatedCompilerApi`, not a `@Deprecated` warning — see
Skill feedback 3).

## Key source snippets

`plugin/src/main/kotlin/com/example/tag/TagIrGenerationExtension.kt` — the part that exercises the 2.4.20 API:

```kotlin
if (!processed.hasAnnotation(TAG_CLASS_ID)) return processed                       // hasAnnotation(ClassId)

val annotation = processed.getAnnotation(TAG_FQ_NAME) ?: error(...)
check(annotation.isAnnotation(TAG_CLASS_ID))                                        // isAnnotation(ClassId), new in 2.4.20
val tagClassSymbol = pluginContext.finderForSource(currentFile).findClass(TAG_CLASS_ID) ?: error(...)
check(annotation.classSymbol == tagClassSymbol)                                     // classSymbol instead of deprecated symbol
check(processed.hasAnnotation(tagClassSymbol))                                      // hasAnnotation(IrClassSymbol)

val name = annotation.getConstArgument<String>("name") ?: error(...)                // AdditionalIrUtils.kt:413
val times = processed.getAnnotationArgumentValue<Int>(TAG_FQ_NAME, "times") ?: error(...)  // IrUtils.kt:367

val rawName = (annotation.argumentMapping[Name.identifier("name")] as? IrConst)?.value      // raw view
val rawTimes = (annotation.argumentMapping[Name.identifier("times")] as? IrConst)?.value
check(rawName == name && rawTimes == times)

val builder = DeclarationIrBuilder(pluginContext, processed.symbol)
processed.body = builder.irBlockBody {
    +irReturn(irString(List(times) { name }.joinToString(" ")))
}
```

Imports that made it resolve (all `org.jetbrains.kotlin.ir.util.*`): `getAnnotation`, `getAnnotationArgumentValue`,
`getConstArgument`, `hasAnnotation`, `isAnnotation`. `argumentMapping` / `classSymbol` are members of
`org.jetbrains.kotlin.ir.expressions.IrAnnotation` and need no import.

`sample/src/main/kotlin/com/example/Main.kt`:

```kotlin
annotation class Tag(val name: String, val times: Int)

@Tag(name = "greeting", times = 3)
fun tagged(): String = "plugin did not run"

fun main() {
    println(tagged())
}
```

## Skill feedback

1. **The 2.4.20 annotation-argument API lives only in `CHANGES.md`; `guide.md` never shows how to read an argument.**
   `ir-call-rewriting/guide.md` says (section "Filtering by call site context"): "You'll need annotation-checking
   helpers; `IrAnnotationContainer.hasAnnotation(fqName)` is in `org.jetbrains.kotlin.ir.util`." That is the only
   annotation helper the guide names; `getConstArgument` / `getAnnotationArgumentValue` / `argumentMapping` /
   `classSymbol` / `isAnnotation(ClassId)` appear only in the frontmatter `description` and in `CHANGES.md`. An author
   who does not hit the unresolved-reference error first (e.g. a new plugin, not a migration) has no reason to open
   `CHANGES.md` and will guess. Suggest adding the two-line `getConstArgument` / `getAnnotationArgumentValue` example
   from `CHANGES.md` directly under "Filtering by call site context" in `guide.md`, plus a one-line note that
   `argumentMapping` returns `IrExpression?` (cast to `IrConst` for `.value`).

2. **Everything `CHANGES.md` claims for 2.4.20 was verified accurate.** The four removed helpers are `Unresolved
   reference` (exact text above); `getConstArgument<T>(name)` is in `org.jetbrains.kotlin.ir.util` (imported from
   `AdditionalIrUtils.kt`); `getAnnotationArgumentValue<T>(fqName, argName)` survives; `hasAnnotation(ClassId)`,
   `hasAnnotation(IrClassSymbol)`, `isAnnotation(ClassId)`, `classSymbol`, `argumentMapping` all resolve and return
   the expected values. The "Before / After" snippet in `CHANGES.md` compiles as written (the `After` half).

3. **`IrAnnotation.symbol` deprecation is not a `@Deprecated` — the warning text is different from what an author
   will search for.** `CHANGES.md` says `symbol` is "`@DeprecatedCompilerApi(deprecatedSince = _2_4_20)`" and the
   frontmatter says "a deprecation on `IrAnnotation.symbol`". In practice the compiler prints
   `w: ... This compiler API is deprecated` — because `@DeprecatedCompilerApi` is a `@RequiresOptIn(level = WARNING)`
   marker (`compiler/util/src/org/jetbrains/kotlin/DeprecatedCompilerApi.kt`), not `kotlin.Deprecated`. So there is
   no `'symbol' is deprecated. ...` message naming the member, and the usual `@Suppress("DEPRECATION")` does not
   silence it (an `@OptIn(DeprecatedCompilerApi::class)` would). Worth one sentence in `CHANGES.md` so authors can
   match the warning text to the cause.

4. **`classSymbol.owner` needs `@UnsafeDuringIrConstructionAPI`.** A first draft compared
   `annotation.classSymbol.owner.name` and got
   `w: This declaration needs opt-in. Its usage should be marked with '@org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI' ...`.
   `CHANGES.md` says "use the new `IrAnnotation.classSymbol: IrClassSymbol` to identify the annotation class" —
   correct, but it should say to compare the *symbol* (against `finderForSource(file).findClass(classId)`, or just use
   `hasAnnotation(ClassId)` / `isAnnotation(ClassId)`), not to dereference `.owner`.

5. **`ir-plugincontext-usage` guidance held up:** `pluginContext.referenceClass(classId)` warns
   `'fun referenceClass(classId: ClassId): IrClassSymbol?' is deprecated. Please use `finderForBuiltins()` or
   `finderForSource(fromFile)` instead.` on 2.4.20; `finderForSource(currentFile).findClass(classId)` (with
   `currentFile` from `IrElementTransformerVoidWithContext`) is warning-free. No change needed there.

6. Minor: the `ir-call-rewriting/guide.md` frontmatter lists the 2.4.20 symptoms only as a migration trigger ("If the
   user is upgrading from older Kotlin ... gets `Unresolved reference` on ..."). Since the guide body never mentions
   the replacement API, the router (`SKILL.md`) cannot direct a *new* plugin author to it either — consider a short
   "Reading annotation arguments (2.4.20+)" subsection in the guide body so the information is reachable without the
   error-first path.
