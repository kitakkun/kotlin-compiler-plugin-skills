# Benchmark 06 — `@JsonSerialize` Mini-Serializer (Extreme)

A compiler plugin that synthesises round-trippable JSON serialization for annotated classes. Four user-facing annotations (`@JsonSerialize`, `@JsonRename`, `@JsonIgnore`, `@JsonRequired`) plus implicit recursive nesting of `@JsonSerialize` types, four cross-checking diagnostics that consider annotation × type × default-value relationships, FIR declaration generation paired with IR body filling, an IR-only metadata-visible `parse()` companion factory, and a multi-module verification phase. Modeled on `kotlinx.serialization` semantics but trimmed to what an evaluation can verify.

This evaluation deliberately combines the patterns previously tested in isolation:
- Benchmark 02 covered FIR-generates-method + IR-fills-body for one method on one class.
- Benchmark 04 covered `FirTypeAttributeExtension` + a single function-call checker.
- Benchmark 05 covered multi-version Gradle setup.

Benchmark 06 adds: multiple annotations whose semantics interact, recursive type traversal, IR-only metadata-visible declarations, and cross-module compilation where the consumer module sees plugin-generated members through compiled metadata only (not from source).

## Specification

### The user-facing API

All five annotations are declared by the consumer (not by the plugin):

```kotlin
package com.example.json

@Target(AnnotationTarget.CLASS)
annotation class JsonSerialize

@Target(AnnotationTarget.PROPERTY)
annotation class JsonRename(val name: String)

@Target(AnnotationTarget.PROPERTY)
annotation class JsonIgnore

@Target(AnnotationTarget.PROPERTY)
annotation class JsonRequired
```

User-side usage:

```kotlin
@JsonSerialize
class User(
    @JsonRename("user_name") val name: String,
    val age: Int,
    @JsonIgnore val passwordHash: String,
    val active: Boolean,
)

@JsonSerialize
class Order(
    val id: Int,
    val items: List<LineItem>,
    val customer: User,           // nested @JsonSerialize — recursive serialization
    @JsonRename("paid_amount") val paid: Double,
)

@JsonSerialize
class LineItem(
    val sku: String,
    val qty: Int,
)
```

### Generated members

For every class annotated `@JsonSerialize`, the plugin synthesises:

1. **`fun toJson(): String`** — instance method, returns the class's fields as a JSON object string. FIR declares the signature, IR fills the body.
2. **`companion object`** — **the FIR side** synthesises a companion object on annotated classes that don't declare one (so the IR side has somewhere to attach `parse()`). Existing user-written companions are left alone.
3. **`fun Companion.parse(json: String): T?`** — companion-object factory. **The FIR side does NOT declare it**; it is generated purely on the IR side using `IrFactory` and registered as metadata-visible via `IrPluginContext.metadataDeclarationRegistrar.registerFunctionAsMetadataVisible`. Consequence: within the **same compilation pass** of `module-A`, FIR cannot see `parse()` (because it was added after FIR ran), so any reference to `User.parse(...)` from `module-A`'s own source files in that compilation will fail with `Unresolved reference 'parse'`. After compilation, `parse()` is in module-A's metadata and IS visible to FIR resolution in **downstream modules** that import the compiled `module-A` (this is the whole point of `registerFunctionAsMetadataVisible`).

Note: this property holds for the *first-pass compilation of module-A's own source set*. If `module-A` ever compiles incrementally against its own previously-emitted `.class` (rare but possible in some IC setups), FIR would see `parse()` from the prior metadata. The evaluation's negative test (criterion 14) is set up as a fresh `module-A` compilation, not incremental.

### JSON output format

Concrete shape of `toJson()` output (no whitespace, deterministic field order = constructor declaration order):

| Property type | Example output | Constraint |
|---|---|---|
| `String` | `"name":"Alice"` | the SCORER and TEST FIXTURES use ASCII-only strings without `"`, `\`, control chars, or non-ASCII. The agent does NOT need to implement JSON escaping. |
| `Int`, `Long` | `"age":30` | integers within `Int` range |
| `Boolean` | `"active":true` | `true` / `false` literal |
| `Double` | `"paid_amount":99.95` | finite, non-`NaN`/`Infinity`, non-scientific-notation values only. Test fixtures use values like `99.95`, `0.5`, `100.0` whose `toString()` round-trips cleanly via `String.toDouble()`. |
| Nested `@JsonSerialize T` | `"customer":{...}` | recursive call to `customer.toJson()` |
| `List<T>` of `@JsonSerialize` elements | `"items":[{...},{...}]` | only single-level lists; `List<List<T>>` is out of scope |
| `List<T>` of primitives (String/Int/Bool/Double) | `"tags":["x","y"]` or `"counts":[1,2]` | only single-level lists |
| `T?` nullable, when null | `"foo":null` | |
| `T?` nullable, when non-null | same as `T` | |

**Constraints / what's NOT required**:
- Only **constructor-declared `val`/`var` properties** are serialized. Body-declared properties (`class Foo { val x: Int = 0 }`) are out of scope; the agent doesn't need to walk them, and the diagnostics are also constructor-parameter-only.
- No JSON escaping required (test fixtures avoid problem characters).
- No `Map`, `Set`, primitive arrays, or generic/sealed-class polymorphism.
- No nested generics (`List<List<T>>`, `Map<K,V>`).
- `Double.NaN` / `Infinity` are not handled; fixtures use finite decimal values only.

Properties carrying `@JsonIgnore` are **omitted entirely**. `@JsonRename("x")` substitutes the JSON key. The output is wrapped in `{...}` and properties are joined by `,`.

Example: `User("Alice", 30, "secret", true).toJson()` → `{"user_name":"Alice","age":30,"active":true}` (note: `passwordHash` omitted; `name` renamed).

**IR-time type detection hint for nested vs primitive lists**: at IR codegen, the agent has the property's `IrType`. To distinguish `List<User>` from `List<Int>`, inspect `irType.classOrNull` for the `List` class id, then `(irType as IrSimpleType).arguments[0].typeOrNull?.classOrNull?.owner?.hasAnnotation(JsonSerializeFqn)` to detect whether the element type carries `@JsonSerialize`. (Use `pluginContext.finderForBuiltins().findClass(...)` for resolved class lookups; the deprecated `referenceClass`/`referenceFunctions` should not appear in solution code.)

### `parse(json: String): T?` semantics

`Companion.parse(...)` parses the JSON shape produced by `toJson()` and returns an instance of the class. On any error (malformed JSON, missing required field, type mismatch), returns `null`. The agent doesn't need to handle every JSON edge case — only the round-trip of values produced by their own `toJson()`.

The function is **IR-only**: its declaration is synthesised purely on the IR side using `IrFactory`, and registered as metadata-visible. It is therefore:

- **NOT callable from source code in the same module** (FIR doesn't see it). A test in `module-A` that tries `User.Companion.parse("...")` from source will fail to compile.
- **Callable from source code in `module-B`** that imports `module-A`, because module-A's `.kotlin_module` metadata exposes it to FIR resolution in downstream modules.

This is the canonical "metadata-only" pattern that current skills exercise nowhere.

### Diagnostics (compile-time errors)

Four diagnostics that consider annotation × type × default-value relationships. The semantic principle for `@JsonRequired`: **"this property must produce a real value with no escape hatch"** — neither nullability nor default values are allowed because both let the user dodge the requirement.

1. **`JSON_REQUIRED_WITH_DEFAULT`** — error when `@JsonRequired` is on a property whose constructor parameter has a default value. A default value lets the user omit the field; `@JsonRequired` says "must provide it". Contradictory.

```kotlin
@JsonSerialize class Bad(@JsonRequired val email: String = "anon")    // ERROR
```

Squiggle on the `@JsonRequired` annotation.

2. **`JSON_REQUIRED_ON_NULLABLE`** — error when `@JsonRequired` is on a property whose type is nullable (`T?`). A nullable type lets the value be `null`, which is itself an escape hatch from "must produce a real value". Contradictory.

```kotlin
@JsonSerialize class Bad(@JsonRequired val email: String?)            // ERROR
```

Squiggle on the `@JsonRequired` annotation.

The four-way truth table:

| Type | Default | Diagnostic |
|---|---|---|
| `String` | none | OK |
| `String` | `"anon"` | `JSON_REQUIRED_WITH_DEFAULT` |
| `String?` | none | `JSON_REQUIRED_ON_NULLABLE` |
| `String?` | `null` | both fire — both diagnostics MUST be reported on this property |

3. **`JSON_IGNORE_AND_REQUIRED_CONFLICT`** — error when both `@JsonIgnore` and `@JsonRequired` appear on the same property. The two are contradictory: ignored fields can't be required.

Squiggle on the `@JsonRequired` annotation.

4. **`JSON_RENAME_EMPTY`** — error when `@JsonRename("")` (empty string). The output JSON key would be empty.

Squiggle on the `@JsonRename` annotation.

These don't fire on valid usage, of course.

### Cross-module verification

Two-module Gradle layout:

- **`module-A`** — defines the annotations and `@JsonSerialize` classes. Compiles with the plugin loaded.
- **`module-B`** — depends on `module-A`. Calls `User.parse("...")` and `User("...").toJson()` from source. Also has its own `@JsonSerialize` class that nests `User` from module-A.

`module-B` must compile successfully and demonstrate runtime round-trip:

```kotlin
// module-B/src/main/kotlin/com/example/app/Main.kt
import com.example.model.User      // from module-A
import com.example.model.Order
import com.example.model.LineItem

fun main() {
    val u = User("Alice", 30, "secret", true)
    println(u.toJson())                                                     // {"user_name":"Alice","age":30,"active":true}

    val parsed = User.parse(u.toJson())                                     // calls IR-only function
    require(parsed != null && parsed.name == "Alice")
    println("parsed ok: ${parsed.name}")

    val order = Order(
        id = 7,
        items = listOf(LineItem("SKU-1", 2), LineItem("SKU-2", 1)),
        customer = u,
        paid = 99.95,
    )
    println(order.toJson())
    val orderRoundTrip = Order.parse(order.toJson())
    require(orderRoundTrip != null && orderRoundTrip.id == 7 && orderRoundTrip.items.size == 2)
    println("order ok: ${orderRoundTrip.items[0].sku}")
}
```

The fact that `User.parse(...)` resolves cleanly in module-B is the verification that metadata-visible IR-only generation works end-to-end.

### Project layout

```
evaluation/06-extreme-json-serialize/work/
├── settings.gradle.kts, gradle.properties, build.gradle.kts
├── gradle/, gradlew, gradlew.bat                   ← copy from skills/compiler-plugin-bootstrap/example/
├── plugin/
│   └── src/main/kotlin/com/example/json/
│       ├── JsonPluginNames.kt
│       ├── JsonComponentRegistrar.kt
│       ├── JsonCommandLineProcessor.kt
│       ├── fir/
│       │   ├── JsonGeneratedDeclarationKey.kt
│       │   ├── JsonDeclarationGenerator.kt          ← FirDeclarationGenerationExtension (synthesises companion + toJson signature)
│       │   ├── JsonAdditionalCheckers.kt            ← FirAdditionalCheckersExtension
│       │   ├── JsonChecker.kt                       ← inspects properties + their annotations
│       │   ├── JsonDiagnostics.kt                   ← 4 KtDiagnosticFactory factories
│       │   └── JsonFirExtensionRegistrar.kt
│       └── ir/
│           ├── JsonIrGenerationExtension.kt         ← fills toJson body, generates parse
│           └── ... (helpers for type matching, primitive lookup)
├── module-A/
│   └── src/main/kotlin/com/example/model/
│       ├── Annotations.kt                           ← the 4 annotations (user-defined, NOT in plugin)
│       └── Models.kt                                ← User, Order, LineItem
└── module-B/
    └── src/main/kotlin/com/example/app/
        ├── Main.kt                                  ← exercises toJson + parse
        └── NestedModel.kt                           ← @JsonSerialize class that nests User from module-A
```

`module-B` depends on `module-A` (`api` or `implementation`).

## Acceptance criteria

| # | Criterion | How to verify |
|---|---|---|
| 1 | Plugin builds | `./gradlew :plugin:jar` succeeds |
| 2 | `module-A` compiles | `./gradlew :module-A:compileKotlin` succeeds |
| 3 | `module-A`'s `User.class` declares `toJson()` method | `javap -p module-A/build/classes/kotlin/main/com/example/model/User.class` shows `public final java.lang.String toJson()` |
| 4 | `module-A`'s `User.Companion.class` declares `parse(java.lang.String)` method | `javap -p module-A/build/classes/kotlin/main/com/example/model/User\$Companion.class` shows the method |
| 5 | `module-B` compiles successfully | `./gradlew :module-B:compileKotlin` succeeds (proves metadata-visibility round-trip works) |
| 6 | `module-B:run` exit 0 with correct JSON output | output contains `{"user_name":"Alice","age":30,"active":true}` (passwordHash omitted, name renamed) |
| 7 | `module-B:run` shows successful `User.parse` round-trip | output contains `parsed ok: Alice` |
| 8 | Order serialization handles nested + list | `Order` instance toJson output contains `"items":[{...},{...}]`, `"customer":{...}`, `"paid_amount":99.95` |
| 8b | `Order.parse` round-trip succeeds (mandatory) | output contains `order ok: SKU-1` (verifies parse handles nested `@JsonSerialize` and `List<@JsonSerialize>`) |
| 9 | `JSON_REQUIRED_WITH_DEFAULT` fires correctly | a temp file with `@JsonSerialize class X(@JsonRequired val x: String = "anon")` produces this diagnostic |
| 10 | `JSON_REQUIRED_ON_NULLABLE` fires correctly | a temp file with `@JsonSerialize class X(@JsonRequired val x: String?)` produces this diagnostic |
| 11 | Neither `JSON_REQUIRED_*` diagnostic fires on valid usage | `@JsonRequired val x: String` (non-null, no default) compiles cleanly |
| 12 | `JSON_IGNORE_AND_REQUIRED_CONFLICT` fires | a temp file with both annotations on the same property produces this diagnostic |
| 13 | `JSON_RENAME_EMPTY` fires | a temp file with `@JsonRename("")` produces this diagnostic |
| 14 | `module-A` source cannot call `parse()` directly within the same compilation pass | adding a probe file `probe.kt` to `module-A/src/main/kotlin/` that calls `User.parse("")`, then a fresh `./gradlew :module-A:compileKotlin --rerun-tasks` (NOT incremental), produces `Unresolved reference 'parse'`. The probe file must be removed after the test so it doesn't break criterion 2 in subsequent runs. |
| 15 | `@JsonSerialize` class without companion gets one synthesised; existing companions are preserved | inspect both cases via `javap` |
| 16 | Diagnostics include the factory name when `-Xrender-internal-diagnostic-names` is on | grep for `JSON_REQUIRED_WITH_DEFAULT`, `JSON_REQUIRED_ON_NULLABLE`, etc. in build output |
| 17 *(optional)* | `parse()` returns `null` on malformed JSON | `User.parse("{not json}")` returns null at runtime |
| 18 *(optional)* | A class nesting another `@JsonSerialize` class from a different module compiles and round-trips | the `module-B/NestedModel.kt` exercises this |

Criteria 1-16 (including 8b) are mandatory; 17-18 are bonus. Note: the original "criterion 17" (grep for `registerFunctionAsMetadataVisible` in source) was dropped — it's behaviourally redundant with criterion 5 (module-B compiles only if metadata visibility actually works).

## Verification procedure

```bash
cd evaluation/06-extreme-json-serialize/work

# Build
./gradlew :plugin:jar :module-A:compileKotlin :module-B:compileKotlin --console=plain 2>&1 | tee /tmp/06-build.txt

# Bytecode shape
javap -p module-A/build/classes/kotlin/main/com/example/model/User.class | grep -E 'toJson|parse'
javap -p module-A/build/classes/kotlin/main/com/example/model/User\$Companion.class | grep -E 'parse'

# Run module-B
./gradlew :module-B:run --console=plain 2>&1 | tee /tmp/06-out.txt
grep -F '{"user_name":"Alice","age":30,"active":true}' /tmp/06-out.txt
grep -F 'parsed ok: Alice' /tmp/06-out.txt
grep -F 'order ok: SKU-1' /tmp/06-out.txt

# Diagnostic checks (build separate temp sources, compile each, expect specific diagnostic)
# JSON_REQUIRED_WITH_DEFAULT:        @JsonRequired val x: String = "anon"
# JSON_REQUIRED_ON_NULLABLE:         @JsonRequired val x: String?
# JSON_IGNORE_AND_REQUIRED_CONFLICT: @JsonIgnore @JsonRequired val x: String
# JSON_RENAME_EMPTY:                 @JsonRename("") val x: String

# Negative test (criterion 14): probe that module-A source cannot resolve parse()
# Procedure:
#   1. Add `module-A/src/main/kotlin/_probe.kt` with content:
#        package com.example.model
#        fun _probe() { User.parse("") }
#   2. Run: ./gradlew :module-A:compileKotlin --rerun-tasks
#      Expect FAIL with "Unresolved reference 'parse'" in the output.
#   3. DELETE _probe.kt and re-run criterion 2 / 5 to confirm clean rebuild.
# (--rerun-tasks ensures FIR runs against fresh source, not against the previously-emitted metadata.)
```

## Skills exercised

| Skill | Used for |
|---|---|
| `compiler-plugin-bootstrap` | project layout, multi-module, META-INF/services |
| `fir-extensions-overview` | extension wiring across declaration generation + checker |
| `fir-predicate-system` | predicate matching for `@JsonSerialize` (and the property-level annotations) |
| `fir-declaration-generation-extension` | synthesising companion + `toJson()` signature |
| `fir-additional-checkers-extension` | four diagnostics with cross-property/annotation checks |
| `ir-plugincontext-usage` | looking up `StringBuilder`, `String.toString()`, primitives, `metadataDeclarationRegistrar` |
| `ir-body-modification` | filling `toJson()` body with property iteration, JSON construction, recursion into nested @JsonSerialize types |
| `ir-synthetic-class-generation` | building the IR-only `parse()` function (the metadata-visible one) |
| `ir-call-rewriting` | NOT primary, but the agent may rewrite calls to `toJson()` on nested types if that simplifies recursion |
| `compiler-plugin-debugging` | likely needed when round-trip fails |
| `gradle-plugin-integration` | multi-module Gradle setup |

## Forbidden reference material

- All `verification/` subdirectories — they contain working reference implementations of the FIR / IR extensions; off-limits per evaluation policy.
- All `evaluation/0*/work/` directories of previous evaluations
- `/Users/kitakkun/Documents/GitHub/kotlin-lang/plugins/kotlinx-serialization/` — this is the canonical reference and the spiritual ancestor of this evaluation's design. Reading it amounts to copying. The agent must produce equivalent code from skill docs only.
- `/Users/kitakkun/Documents/GitHub/kotlin-lang/plugins/parcelize/` — also off-limits (similar pattern of FIR declaration + IR fill + companion-style factory).

`skills/compiler-plugin-bootstrap/example/` is allowed for project boilerplate.

## Common failure modes (anti-cheat)

1. **`toJson()` declared at FIR but body never filled in IR** — method exists in bytecode but throws `NotImplementedError` or returns `""`. Common cause: wrong `IrDeclarationOrigin` filter in IR generator.
2. **`parse()` generated at FIR instead of IR-only** — defeats the purpose; module-A source can then call it and metadata-visible registration becomes redundant. Verify by attempting `User.parse(...)` from module-A and observing `Unresolved reference`.
3. **Forgetting `registerFunctionAsMetadataVisible`** — the IR-only `parse` is generated but invisible to module-B. Module-B then fails compile with `Unresolved reference 'parse'`.
4. **Hard-coding property names instead of respecting `@JsonRename`** — output uses `"name"` instead of `"user_name"`. Fix: read `@JsonRename` from each property's annotations during IR body fill.
5. **Including `@JsonIgnore` properties in output** — `passwordHash` appears in JSON. Fix: skip the property during IR iteration if `IrProperty.hasAnnotation(JSON_IGNORE_FQ)`.
6. **Recursion via `toString()` instead of `toJson()`** — nested `@JsonSerialize` shows `Order(id=1, items=[...])` (the data-class default toString) instead of `{"id":1,"items":[...]}`. Fix: when recursing, look up the nested class's `toJson` symbol via `pluginContext.finderForSource(...).findFunctions(...)` (the modern API; `referenceFunctions` is deprecated and should not appear in solution code).
7. **`JSON_REQUIRED_WITH_DEFAULT` checker doesn't actually inspect the default-value expression** — naive implementations grep for the annotation and report unconditionally; the correct check is `firProperty.correspondingValueParameterFromPrimaryConstructor?.defaultValue != null`. Fix: walk the constructor parameter and look at its `FirExpression?` default.
8. **Diagnostic factory map registered without `registerDiagnosticContainers`** — produces `Diagnostic factory was not registered` at consumer compile time.
9. **Using IR API that doesn't generate metadata** — e.g. `IrFactory.createSimpleFunction` without setting `IrDeclarationOrigin` correctly, or skipping the metadata registrar. The function exists in bytecode but `module-B` still can't resolve it because the metadata layer didn't get the entry.
10. **Recursive `toJson()` via reflection at runtime** — the agent might be tempted to write a runtime helper that calls `obj.javaClass.getMethod("toJson").invoke(obj)`. This works but defeats the IR-generation aspect. The evaluation expects compile-time IR linking via `pluginContext.finderForSource(file).findFunctions(...)` (or `finderForBuiltins`) lookup of `toJson` on the nested type.
11. **Empty `@JsonRename("")` allowed at compile time** — produces `"":"value"` in JSON output. Fix: `JSON_RENAME_EMPTY` checker on the annotation argument.
12. **Module-B compilation fails with "Companion is not a class"** — caused by generating only the `parse()` method without ensuring the companion object exists (some classes don't have one in source). Fix: companion synthesis is part of the FIR declaration-generation step.

## Estimated effort

A correctly-skilled agent should complete the core (criteria 1-16) in **10-15 rounds**. The IR body fill for recursion is the trickiest single piece: looking up the `toJson` symbol on a nested type at IR time requires understanding `pluginContext.referenceFunctions(...)` (or the modern `finderFor*` API) and constructing the call with the right receiver. The metadata-visible registration is mechanically simple but requires reading the relevant `ir-plugincontext-usage` section carefully.

If the agent finishes in fewer than 10 rounds, suspect one of the failure modes above — most commonly criterion 13 (silently generating `parse` at FIR, defeating the IR-only requirement) or recursion via `toString()` instead of `toJson()`.

## Score weighting

This task carries **5× the weight** of the small task in overall scoring, reflecting the combinatorial complexity of multiple interacting annotations + FIR + IR + metadata-visible IR-only declarations + multi-module verification.

## Pre-existing reference

`skills/compiler-plugin-bootstrap/example/` for project boilerplate. The agent may also extend the multi-module pattern from `evaluation/05-multiversion-final-checker/`'s STRATEGY documentation but **not** copy its implementation source. (The 05 work/ directory is forbidden per the policy above.)
