// This file MUST fail to compile to demonstrate that `@MyKind () -> Unit`
// and plain `() -> Unit` are distinct function types.
//
// Default conversion behaviour: `supportsConversionFromSimpleFunctionType`
// is `true` on [MyKind], so a plain lambda is implicitly coerced into a
// `@MyKind` lambda (this is the same ergonomics built-in `Function` kinds
// have). The reverse direction however is NOT supported: a typed `@MyKind`
// lambda value is not assignable to a plain `() -> Unit` parameter, because
// the synthetic `MyKindFunction0` is not a subtype of `kotlin.Function0`.
//
// Compile this file alone (e.g. by moving it into `src/main/kotlin`) to see
// the type-mismatch diagnostic the FIR resolver reports.

import com.example.MyKind

fun expectsPlain(block: () -> Unit) { block() }

fun crossKindCallSiteMismatch() {
    val myKindLambda: @MyKind () -> Unit = @MyKind { println("MyKind") }
    expectsPlain(myKindLambda) // ERROR: argument type mismatch — distinct kinds
}
