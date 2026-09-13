---
name: fir-type-attribute-extension
description: Attach plugin-defined attributes to types via annotations (e.g. `@Positive Int`, `@Pure (Int) -> Int`) so that the attribute participates in FIR type inference, subtyping, and metadata round-trips across module boundaries. Covers FirTypeAttributeExtension, the ConeAttribute base class (union/intersect/add/isSubtypeOf/keepInInferredDeclarationType), the mandatory `ConeAttributes.attributeAccessor<T>()` delegated property, and the round-trip between FirAnnotation and ConeAttribute (analysis-time extract / serialization-time convert). Read fir-extensions-overview and fir-additional-checkers-extension first. NOT for declaration-level annotations (use fir-predicate-system + a checker) or for changing modifiers (see fir-status-transformer-extension).
---

# FirTypeAttributeExtension

This is the K2 extension point for **type-level metadata**: information that travels *with the type* through inference, subtype checks, and metadata serialization. The canonical use case is refinement-style annotations — `@Positive Int` and `@Negative Int` are different from `Int` for the purpose of compatibility checking — but the same machinery is what `kotlin.NoInfer`, `kotlin.Exact`, and `kotlin.UnsafeVariance` use internally for compiler-defined attributes.

Source: [`kotlin/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirTypeAttributeExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.20/compiler/fir/tree/src/org/jetbrains/kotlin/fir/extensions/FirTypeAttributeExtension.kt). Reference impl: [`FirNumberSignAttributeExtension.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.20/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/types/FirNumberSignAttributeExtension.kt) + [`ConeNumberSignAttribute.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.20/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/types/ConeNumberSignAttribute.kt) + [`SignedNumberCallChecker.kt`](https://github.com/JetBrains/kotlin/blob/v2.4.20/plugins/plugin-sandbox/src/org/jetbrains/kotlin/plugin/sandbox/fir/checkers/SignedNumberCallChecker.kt) (under `kotlin/plugins/plugin-sandbox/src/`).

## What you get

```kotlin
abstract class FirTypeAttributeExtension(session: FirSession) : FirExtension(session) {
    abstract fun extractAttributeFromAnnotation(annotation: FirAnnotation): ConeAttribute<*>?
    abstract fun convertAttributeToAnnotation(attribute: ConeAttribute<*>): FirAnnotation?
    fun interface Factory : FirExtension.Factory<FirTypeAttributeExtension>
}
```

Two methods, two directions:

- **`extractAttributeFromAnnotation`** (analysis time) — the resolver hands you a `FirAnnotation` it found on a type usage (e.g. `@Positive` written on `Int`); you return a `ConeAttribute<*>` that carries the semantic info, or `null` if you don't recognise the annotation. The returned attribute is attached to the `ConeKotlinType` and travels with it through inference.
- **`convertAttributeToAnnotation`** (serialization time) — when writing module metadata (`.kotlin_module`), the serializer asks every plugin to convert any attached attribute back into a `FirAnnotation`. This is what lets a downstream module see your `@Positive` when it imports the compiled class. **Return `null` for attributes you didn't create** (the KDoc warns about this explicitly — converting another plugin's or the compiler's attributes corrupts metadata).

## The `ConeAttribute<T>` contract

```kotlin
abstract class ConeAttribute<out T : ConeAttribute<T>> {
    abstract fun union(other: T?): T?
    abstract fun intersect(other: T?): T?
    abstract fun add(other: T?): T?           // typealias expansion
    abstract fun isSubtypeOf(other: T?): Boolean
    abstract val key: KClass<out T>
    abstract val keepInInferredDeclarationType: Boolean
    open val implementsEquality: Boolean get() = false
    abstract override fun toString(): String
}
```

Each operation governs a specific part of the type system:

| Method / property | When it's called | What you typically return |
|---|---|---|
| `union` | combining types from branches (`if/else`, `when`) | most common case: `null` if attributes differ, `this` if they match |
| `intersect` | computing intersection types | same shape as `union` |
| `add` | typealias expansion stacking attributes (`typealias B = @X(2) (@X(1) A)` → adds `X(1) + X(2)`) | per-attribute logic (combining sign info, accumulating effects, etc.) |
| `isSubtypeOf` | subtype compatibility — does `T1` with attribute `a1` accept assignment from `T2` with `a2`? | `true` if compatible, `false` to forbid |
| `key` | registry lookup; **must** equal `YourAttribute::class` | `YourAttribute::class` |
| `keepInInferredDeclarationType` | controls whether the attribute survives declaration-type approximation (function return types, property types) | `true` for refinement attributes you want to persist; `false` for transient hints (compare: built-in `Exact`, `NoInfer` are transient) |
| `implementsEquality` | enables structural `equals`/`hashCode` comparison in `ConeAttributes.definitelyDifferFrom` | usually `true` if you wrote sensible `equals`/`hashCode` |

## End-to-end example: `@Positive` / `@Negative` Number sign tracking

User code:

```kotlin
package com.example.signs

@Target(AnnotationTarget.TYPE)
annotation class Positive

@Target(AnnotationTarget.TYPE)
annotation class Negative

fun takePositive(x: @Positive Int) {}
fun takeAny(x: Int) {}

fun test(p: @Positive Int, n: @Negative Int) {
    takePositive(p)        // ok
    takePositive(n)        // error: ILLEGAL_NUMBER_SIGN
    takeAny(p)             // ok — un-annotated Int accepts anything
}
```

### 1. The `ConeAttribute`

```kotlin
package com.example.signs.fir

import org.jetbrains.kotlin.fir.types.ConeAttribute
import org.jetbrains.kotlin.fir.types.ConeAttributes
import kotlin.reflect.KClass

class ConeNumberSignAttribute private constructor(val sign: Sign) : ConeAttribute<ConeNumberSignAttribute>() {
    enum class Sign {
        Positive {
            override fun combine(other: Sign?): Sign? = if (other == Positive) Positive else null
        },
        Negative {
            override fun combine(other: Sign?): Sign? = if (other == Negative) Negative else null
        };
        abstract fun combine(other: Sign?): Sign?
    }

    private fun combine(other: ConeNumberSignAttribute?): ConeNumberSignAttribute? =
        fromSign(sign.combine(other?.sign))

    override fun union(other: ConeNumberSignAttribute?) = combine(other)
    override fun intersect(other: ConeNumberSignAttribute?) = combine(other)
    override fun add(other: ConeNumberSignAttribute?) = combine(other)
    override fun isSubtypeOf(other: ConeNumberSignAttribute?) = true
    override fun toString() = "@${sign.name}"

    override val key: KClass<out ConeNumberSignAttribute> = ConeNumberSignAttribute::class
    override val keepInInferredDeclarationType: Boolean = true

    companion object {
        private val Positive = ConeNumberSignAttribute(Sign.Positive)
        private val Negative = ConeNumberSignAttribute(Sign.Negative)
        fun fromSign(sign: Sign?): ConeNumberSignAttribute? = when (sign) {
            Sign.Positive -> Positive
            Sign.Negative -> Negative
            null -> null
        }
    }
}

// REQUIRED: declare an accessor on ConeAttributes for your type
val ConeAttributes.numberSign: ConeNumberSignAttribute? by ConeAttributes.attributeAccessor<ConeNumberSignAttribute>()
```

The `attributeAccessor<T>()` delegated property is **mandatory** — `ConeAttributes` stores attributes in a type-keyed array internally, and the accessor is how downstream code (your checker, IR generation, etc.) fishes one out. Without it, even if `extractAttributeFromAnnotation` works, you can't read the result back. The `FirTypeAttributeExtension` KDoc states this requirement explicitly.

### 2. The extension

```kotlin
package com.example.signs.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.extensions.FirTypeAttributeExtension
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class NumberSignAttributeExtension(session: FirSession) : FirTypeAttributeExtension(session) {
    override fun extractAttributeFromAnnotation(annotation: FirAnnotation): ConeAttribute<*>? {
        val sign = when (annotation.annotationTypeRef.coneTypeOrNull?.classId) {
            POSITIVE -> ConeNumberSignAttribute.Sign.Positive
            NEGATIVE -> ConeNumberSignAttribute.Sign.Negative
            else -> return null
        }
        return ConeNumberSignAttribute.fromSign(sign)
    }

    override fun convertAttributeToAnnotation(attribute: ConeAttribute<*>): FirAnnotation? {
        if (attribute !is ConeNumberSignAttribute) return null   // not ours; defer
        val classId = when (attribute.sign) {
            ConeNumberSignAttribute.Sign.Positive -> POSITIVE
            ConeNumberSignAttribute.Sign.Negative -> NEGATIVE
        }
        return buildAnnotation {
            annotationTypeRef = buildResolvedTypeRef {
                coneType = ConeClassLikeTypeImpl(classId.toLookupTag(), ConeTypeProjection.EMPTY_ARRAY, isMarkedNullable = false)
            }
            argumentMapping = FirEmptyAnnotationArgumentMapping
        }
    }

    companion object {
        private val PKG = FqName("com.example.signs")
        val POSITIVE = ClassId(PKG, Name.identifier("Positive"))
        val NEGATIVE = ClassId(PKG, Name.identifier("Negative"))
    }
}
```

### 3. The checkers that enforce the attribute (call sites AND assignment sites)

This is where the attribute pays off. `FirTypeAttributeExtension` only attaches metadata — to actually reject `takePositive(n)` you need a checker that compares attributes between source and target. **One checker is not enough**: refinement attributes show up in two distinct places, and each needs its own checker class:

| Location | Checker base | Catches |
|---|---|---|
| function call argument vs parameter | `FirFunctionCallChecker` | `takePositive(makeNegative())` |
| local-variable / property initialiser type vs RHS type | `FirPropertyChecker` | `val x: @Positive Int = makeNegative()` |
| function return-type vs returned expression | `FirReturnExpressionChecker` | `fun f(): @Positive Int = makeNegative()` |

Plugins enforcing refinement attributes typically register at minimum the first two; a strict implementation also covers `FirReturnExpressionChecker`. Without `FirPropertyChecker`, the `val x: @Positive Int = makeNegative()` case silently compiles even though `x` carries a wrong-sign value.

The `FirFunctionCallChecker` form (call sites):

```kotlin
package com.example.signs.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.resolvedType

object SignedNumberCallChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val argMapping = expression.resolvedArgumentMapping ?: return
        for ((arg, param) in argMapping.entries) {
            val expected = param.returnTypeRef.coneType.attributes.numberSign ?: continue
            val actual = arg.resolvedType.attributes.numberSign
            if (expected != actual) {
                reporter.reportOn(arg.source, NumberSignDiagnostics.ILLEGAL_NUMBER_SIGN, expected.sign.name, actual?.sign?.name ?: "None")
            }
        }
    }
}
```

And the paired `FirPropertyChecker` for assignment sites:

```kotlin
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.declarations.FirProperty

object SignedNumberPropertyChecker : FirPropertyChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirProperty) {
        val expected = declaration.returnTypeRef.coneType.attributes.numberSign ?: return
        val initializer = declaration.initializer ?: return
        val actual = initializer.resolvedType.attributes.numberSign
        if (expected != actual) {
            reporter.reportOn(initializer.source, NumberSignDiagnostics.ILLEGAL_NUMBER_SIGN, expected.sign.name, actual?.sign?.name ?: "None")
        }
    }
}
```

Both checkers share the same diagnostic factory (`ILLEGAL_NUMBER_SIGN`); the difference is just where they fire. Bundle both into your `DeclarationCheckers` / `ExpressionCheckers` containers.

> **Caveat — `FirPropertyChecker` over-flags numeric literal initialisers.** `val p: @Positive Int = 5` carries `expected = Positive` on the property and `actual = null` on the literal `5`'s `resolvedType` (literals don't pick up the property's annotation). The checker shown above will then report `ILLEGAL_NUMBER_SIGN: expected Positive, found None` for code the user clearly intended. Production use cases need a smarter rule — e.g. allow `actual = null` when the initialiser is a constant whose runtime value the plugin can statically validate (`5 > 0` ⇒ Positive), or restrict the checker to non-literal initialisers, or require an explicit cast / helper to attach the attribute. The simple form above is included to illustrate the assignment-site mechanism; design the real predicate for your domain.

(Diagnostic factory plumbing per [`fir-additional-checkers-extension`](../fir-additional-checkers-extension/guide.md).)

### 4. Register both extensions

```kotlin
class SignsFirRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::NumberSignAttributeExtension
        +::SignsAdditionalCheckers          // wraps SignedNumberCallChecker
        registerDiagnosticContainers(NumberSignDiagnostics)
    }
}
```

## How the round-trip works

The pair of methods is essential because **attributes don't survive metadata serialization automatically** — they're plugin-internal data structures. The compiler runs your extension twice per round trip:

```
Source: @Positive Int     ─┐
                           │ analysis-time:
                           │   FirAnnotation @Positive  ──extractAttributeFromAnnotation──▶  ConeNumberSignAttribute(Positive)
                           │                                                                       │
                           │                                                                       │ attached to type
                           │                                                                       ▼
                           │   inference / subtyping / call-site checking                    ConeKotlinType(Int, attrs={numberSign: Positive})
                           │
                           │ serialization-time (writing .class metadata):
                           │   ConeNumberSignAttribute(Positive)  ──convertAttributeToAnnotation──▶  FirAnnotation @Positive
                           │                                                                              │
                           │                                                                              ▼
                           │                                                              embedded as @TypeAnnotation in Kotlin metadata
                           ▼
Other module reads .class with the @Positive annotation, and (since they have your plugin loaded too) the same extractAttributeFromAnnotation runs again.
```

If you skip `convertAttributeToAnnotation`, an attribute attached to a public function's signature is **silently lost when consumers compile against your library** — they see plain `Int`, not `@Positive Int`. Always implement both.

## Wiring caveats

`FirTypeAttributeExtension` is in `AVAILABLE_EXTENSIONS` and registered like any other FIR extension via `+::YourExtension`. One point worth noting: **type attribute extensions are also enabled for library FIR sessions** (the resolver running over compiled dependencies), so your `extractAttributeFromAnnotation` may be called against annotations on types it sees through .class metadata, not just user source. Keep the function pure; don't depend on source-only state.

## Common gotchas

### Forgetting `attributeAccessor` makes the attribute invisible

The runtime registry uses the `key: KClass<...>` to look up attached attributes. Without `val ConeAttributes.foo: FooAttribute? by ConeAttributes.attributeAccessor<FooAttribute>()`, your checker code has no way to read the attribute back from a type — `attributes.numberSign` won't compile, and constructing your own getter via `attributes[FooAttribute::class]` is internal API. The accessor declaration is the one piece every FirTypeAttributeExtension consumer needs.

### `keepInInferredDeclarationType = false` strips your attribute silently

When the compiler infers a function's return type or a property's type, it runs `ConeAttributes.filterNecessaryToKeep()` to drop attributes that don't survive approximation. If yours has `keepInInferredDeclarationType = false`, the attribute is silently removed from the inferred type — you'll see `Int` where you expected `@Positive Int`, and not understand why. Default to `true` for any refinement attribute you want users to interact with; `false` is for transient hints used during inference itself.

### `union`/`intersect` returning non-null when types are unrelated

If two branches of an `if` expression have `@Positive Int` and `@Negative Int`, your `union` decides what the result type carries. Returning `Positive` (or `Negative`) is wrong — the merged value isn't known to satisfy either contract. Return `null` (drop the attribute) so the user gets plain `Int`, which is sound. The `combine`-returns-`null`-on-mismatch pattern in the example does this correctly.

### Common-supertype calculation in generics drops attributes

A known limitation (visible in `signedNumbersCheckers.kt:28-29` in plugin-sandbox): `select(positiveInt, positiveDouble)` — where `select<T>(x: T, y: T): T` — does not preserve the `@Positive` attribute even though both arguments carry it, because the common-supertype computation goes through the type-parameter approximation path that doesn't yet propagate plugin attributes. Don't promise users that generic helpers preserve refinement attributes; document the limitation.

### `convertAttributeToAnnotation` for someone else's attribute corrupts metadata

The KDoc spells this out: "Please don't convert attributes which you didn't create. If [attribute] came from compiler or another plugin just return null." Because the resolver iterates every type-attribute extension, returning a `FirAnnotation` for an attribute you don't own means the metadata file ends up with both your spurious annotation AND the real owner's annotation — both modules then re-extract conflicting attributes. Always start `convertAttributeToAnnotation` with `if (attribute !is YourAttributeType) return null`.

## Relation to other extensions

- **Foundation** → [`fir-extensions-overview`](../fir-extensions-overview/guide.md), [`compiler-plugin-bootstrap`](../compiler-plugin-bootstrap/guide.md)
- **The checker that enforces the attribute** → [`fir-additional-checkers-extension`](../fir-additional-checkers-extension/guide.md) (especially `FirFunctionCallChecker`)
- **If you also need the attribute to influence IR generation** (e.g. emit different bytecode for `@Pure` calls) → bridge the attribute via `IrGenerationExtension`; on the IR side, `IrType` does not carry plugin attributes by default, so you'd typically read the FIR-side info via `pluginContext.firSymbol` lookups
- **For declaration-level attributes (not type-level)** → use a regular annotation + predicate ([`fir-predicate-system`](../fir-predicate-system/guide.md)) and a checker

## What this skill does NOT cover

- IR-side handling of type attributes (the IR has its own attribute system; bridging requires reading the FIR symbol)
- Compiler-defined attributes (`Exact`, `NoInfer`, `UnsafeVariance`) — those are in `compiler/fir/cones/.../CompilerConeAttributes.kt`
- Attribute participation in nullability/`flexible` type wrappers — your attribute is preserved through these but the unwrapping rules are subtle; consult `ConeAttributes.union` source if needed
- Class-level annotations propagating to all member types automatically (no, they don't — type attributes only attach to type usages where the annotation is written)
