# Verification 16 — IR annotation-argument helpers (Kotlin 2.4.20)

## Goal

Verify the Kotlin 2.4.20 annotation-argument API described in `ir-call-rewriting/CHANGES.md` (`## Kotlin 2.4.10 → 2.4.20`):

1. The 2.4.10 `IrUtils.kt` helpers `IrAnnotation.getAnnotationStringValue()`, `getAnnotationStringValue(name)`,
   `IrAnnotation.getAnnotationValueOrNull<T>(name)` and `IrConstructorCall.getValueArgument(name: Name)` no longer exist
   (must be an `Unresolved reference` on 2.4.20 — checked in a scratch build that is not committed).
2. The replacements work end to end from an `IrGenerationExtension`:
   `IrAnnotation.getConstArgument<T>(name)`, `IrAnnotationContainer.getAnnotationArgumentValue<T>(fqName, argName)`,
   `IrAnnotation.argumentMapping`, `IrAnnotation.classSymbol`, `hasAnnotation(ClassId)` / `hasAnnotation(IrClassSymbol)`,
   `IrAnnotation.isAnnotation(ClassId)`.

The plugin reads both arguments of `@Tag(name = "greeting", times = 3)` on a sample function and replaces that function's
body with `return "<name> <name> ..."` (`name` repeated `times` times, space-separated).

## Project layout

```
16-ir-annotation-arguments/
├── settings.gradle.kts            # foojay toolchain plugin, include("plugin", "sample")
├── build.gradle.kts               # mavenCentral for all projects
├── plugin/                        # kotlin("jvm") 2.4.20, compileOnly kotlin-compiler-embeddable:2.4.20
│   └── src/main/kotlin/com/example/tag/
│       ├── TagComponentRegistrar.kt        # CompilerPluginRegistrar (pluginId, supportsK2 = true)
│       └── TagIrGenerationExtension.kt     # the IrGenerationExtension under test
│   └── src/main/resources/META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
└── sample/                        # kotlin("jvm") 2.4.20 + application; loads plugin via compilerPlugin configuration + -Xplugin=
    └── src/main/kotlin/com/example/Main.kt
```

Sample:

```kotlin
annotation class Tag(val name: String, val times: Int)

@Tag(name = "greeting", times = 3)
fun tagged(): String = "plugin did not run"

fun main() {
    println(tagged())
}
```

## PASS criterion

From inside this directory:

```
../gradlew --no-daemon -q clean :sample:run
```

prints exactly `greeting greeting greeting`. The plugin must read `name` through `IrAnnotation.getConstArgument<String>`
and `times` through `IrAnnotationContainer.getAnnotationArgumentValue<Int>`, and must not use any deprecated API
(no compiler warnings in `:plugin:compileKotlin`).

Additionally (scratch build, not committed): a source file referencing `getAnnotationStringValue` /
`getAnnotationValueOrNull` / `IrConstructorCall.getValueArgument(Name)` must fail `:plugin:compileKotlin` with
`Unresolved reference`; the exact error text is recorded in `RESULT.md`.

## FAIL criterion

- Any of the 2.4.20 replacement APIs does not resolve or does not return the annotation's values (the plugin would then
  `error(...)` during compilation, or the sample prints `plugin did not run`).
- The old helpers still resolve on 2.4.20 (the CHANGES.md claim would then be wrong).
- `:sample:run` prints anything other than `greeting greeting greeting`.

## Skills consulted

- `references/ir-call-rewriting/guide.md` + `CHANGES.md` (`## Kotlin 2.4.10 → 2.4.20`) + `EVIDENCE.md` — the annotation-argument API under test.
- `references/ir-body-modification/guide.md` — replacing the body via `DeclarationIrBuilder.irBlockBody { +irReturn(irString(...)) }`.
- `references/ir-plugincontext-usage/guide.md` — `finderForSource(irFile).findClass(classId)` instead of the deprecated `referenceClass`.
- `references/compiler-plugin-bootstrap/guide.md` — registrar / service file / `-Xplugin=` wiring (mirrored from probe 02).

## Reference patterns from kotlin v2.4.20 (paths)

- `compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/AdditionalIrUtils.kt:413-416` — `inline fun <reified T> IrAnnotation.getConstArgument(name: String): T?`
- `compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt:348` — `IrAnnotation.isAnnotation(classId: ClassId)`
- `compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt:357-364` — `hasAnnotation(FqName / ClassId / IrClassSymbol)`
- `compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt:367-370` — `IrAnnotationContainer.getAnnotationArgumentValue<T>(fqName, argumentName)`
- `compiler/ir/ir.tree/gen/org/jetbrains/kotlin/ir/expressions/IrAnnotation.kt:22-27` — `classSymbol`, `argumentMapping`, `@DeprecatedCompilerApi(_2_4_20) symbol`
- `compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/declarations/IrAnnotationContainer.kt:11` — `val annotations: List<IrAnnotation>`
- `compiler/util/src/org/jetbrains/kotlin/DeprecatedCompilerApi.kt` — `@RequiresOptIn(message = "This compiler API is deprecated", level = WARNING)`
- v2.4.10 `compiler/ir/ir.tree/src/org/jetbrains/kotlin/ir/util/IrUtils.kt:351-392` — the removed helpers
