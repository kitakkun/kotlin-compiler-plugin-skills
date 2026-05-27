# Benchmark 02 — `@AutoStringify` Synthesis (Medium)

A compiler plugin that synthesises a `toAutoString(): String` method on annotated classes. Tests the canonical FIR-generation + IR-body-fill pattern used by kotlinx-serialization, parcelize, and lombok.

## Specification

Implement a plugin that provides:

```kotlin
// User-defined (no plugin involvement):
package com.example.app
annotation class AutoStringify

// Plugin synthesises a `toAutoString()` member on annotated classes:
@AutoStringify
class Person(val name: String, val age: Int)

// Then user can call:
fun main() {
    val p = Person("Alice", 30)
    println(p.toAutoString())   // → "Person(name=Alice, age=30)"
}
```

The synthesised method:
- Name: `toAutoString` (exact)
- Visibility: `public`
- Returns: `String`
- No parameters (other than implicit `this`)
- Body: produces a string of the form `<ClassName>(<prop1>=<val1>, <prop2>=<val2>, ...)` covering every constructor-property of the class, in declaration order.

Constraints:
- Only generated for classes annotated `@AutoStringify`
- Must be visible to source — the user can call `.toAutoString()` directly without a cast
- Should work with primitive props (`Int`, `Boolean`, `Double`) and `String`
- Doesn't need to handle nested objects, arrays, or generic types

## Project layout

```
evaluation/02-medium-auto-stringify/work/
├── settings.gradle.kts, gradle.properties, build.gradle.kts
├── gradle/, gradlew      ← copy from skills/kotlin-compiler-plugin/references/compiler-plugin-bootstrap/example/
├── plugin/
└── sample/
    └── src/main/kotlin/com/example/app/Main.kt
```

`Main.kt` should include at least:

```kotlin
package com.example.app

annotation class AutoStringify

@AutoStringify class Person(val name: String, val age: Int)
@AutoStringify class Box(val width: Int, val height: Int, val opaque: Boolean)
class Untagged(val foo: String)   // no @AutoStringify; method should NOT be available

fun main() {
    println(Person("Alice", 30).toAutoString())
    println(Box(10, 20, true).toAutoString())
    // Untagged().toAutoString() — would be a compile error if uncommented
}
```

## Acceptance criteria

| # | Criterion | How to verify |
|---|---|---|
| 1 | Plugin builds | `./gradlew :plugin:jar` |
| 2 | Sample compiles | `./gradlew :sample:compileKotlin` |
| 3 | `Person(...).toAutoString()` resolves at compile time | sample compiles with the call present |
| 4 | Synthesised method appears in IR | `javap -p sample/build/classes/kotlin/main/com/example/app/Person.class` shows a `toAutoString` method |
| 5 | Output for Person | `./gradlew :sample:run` prints `Person(name=Alice, age=30)` |
| 6 | Output for Box | output also contains `Box(width=10, height=20, opaque=true)` |
| 7 | Untagged class lacks the method | uncomment `Untagged(...).toAutoString()` and verify a compile error mentioning unresolved reference |
| 8 | Method not added to unannotated classes | `javap -p sample/build/classes/kotlin/main/com/example/app/Untagged.class` shows NO `toAutoString` |
| 9 | Method visibility is `public` | `javap` output shows `public` modifier on the synthesised method |
| 10 | No `IrValidation` errors | full build log contains no `e: IrValidation:` lines |

## Verification procedure

```bash
cd evaluation/02-medium-auto-stringify/work

# Build and run
./gradlew :sample:run --rerun-tasks --console=plain 2>&1 | tee /tmp/02-out.txt

# Output checks
grep -F 'Person(name=Alice, age=30)' /tmp/02-out.txt
grep -F 'Box(width=10, height=20, opaque=true)' /tmp/02-out.txt
grep -c 'IrValidation:' /tmp/02-out.txt    # should be 0

# Bytecode shape checks
javap -p sample/build/classes/kotlin/main/com/example/app/Person.class | grep -q 'public.*toAutoString'
javap -p sample/build/classes/kotlin/main/com/example/app/Untagged.class | grep -q 'toAutoString' && echo "FAIL: untagged has the method" || echo "OK: untagged is clean"
```

## Skills exercised

| Skill | Used for |
|---|---|
| `compiler-plugin-bootstrap` | project layout |
| `fir-extensions-overview` | wiring `FirExtensionRegistrar` |
| `fir-predicate-system` | predicate for `@AutoStringify`-annotated class detection |
| `fir-declaration-generation-extension` | synthesising the `toAutoString` method declaration (`createMemberFunction`, `getCallableNamesForClass`, `GeneratedDeclarationKey`) |
| `ir-plugincontext-usage` | looking up `String`'s built-in symbols (`StandardClassIds.String`, `irBuiltIns.stringType`) and the `toString` method of properties |
| `ir-body-modification` | filling the synthesised method's body with `IrStringConcatenation` of property accesses |

## Common failure modes (anti-cheat)

1. **`getCallableNamesForClass` not implemented** → `generateFunctions` is never called → method synthesised at FIR but not "discovered" → IR sees nothing → either compile error or method missing from bytecode.
2. **`GeneratedDeclarationKey` not used** → IR side can't identify "this declaration came from my plugin" → IR origin check fails.
3. **`createMemberFunction(...)` not called** → manual `FirSimpleFunction` construction goes wrong (status, type refs, origin).
4. **IR body not filled** → method synthesised but body is empty/null → runtime returns `""` or NPEs.
5. **Reading property values from IR** → agent might struggle to walk the class's properties from `IrClass.declarations`. Common error: incorrectly typed `IrField` access vs property getter call.
6. **`IrStringConcatenationImpl` not used** → agent reaches for `+` on `IrExpression` (doesn't exist) or builds a fragile chain.
7. **Property access uses `dispatchReceiver = ...`** → deprecated; should be `arguments[0] = ...` (KT-68003 unified arguments).
8. **`UnsafeDuringIrConstructionAPI` opt-in missing** → the IR walk produces opt-in warnings and may fail compilation.

## Estimated effort

A correctly-skilled agent should complete this in **2–3 rounds** of work. The IR-body-fill phase is the trickiest step; expect the agent to need at least one debug iteration on the string-concatenation construction.

## Score weighting

This task carries **2× the weight** of the small task in overall scoring, because it exercises a substantially larger set of skills.
