package com.example.positive

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.resolvedType

/**
 * `FirAdditionalCheckersExtension` that bundles the call-site checker which enforces
 * the `@Positive` refinement at function call sites — the attribute itself is just
 * metadata; this checker is what turns a mismatch into a compile error.
 *
 * Note: only a `FirFunctionCallChecker` is registered; a `FirPropertyChecker` would
 * additionally flag assignments like `val p: @Positive Int = 5`, but the user-stated
 * goal explicitly wants such typed declarations (with literal RHS) to be accepted —
 * the literal `5` is understood as carrying `@Positive` by virtue of the declared type.
 */
class PositiveCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirFunctionCallChecker> =
            setOf(PositiveCallChecker)
    }
}

/**
 * For every argument of a function call: if the parameter type carries `@Positive`,
 * the argument's resolved type must also carry it. Otherwise emit `EXPECTED_POSITIVE_INT`.
 */
object PositiveCallChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val argumentMapping = expression.resolvedArgumentMapping ?: return
        for ((argument, parameter) in argumentMapping.entries) {
            val expected = parameter.returnTypeRef.coneType.attributes.positive ?: continue
            val actual = argument.resolvedType.attributes.positive
            if (expected != actual) {
                reporter.reportOn(argument.source, PositiveDiagnostics.EXPECTED_POSITIVE_INT)
            }
        }
    }
}
