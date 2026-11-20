package com.example.shouting

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirNamedFunctionChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction

class NoShoutingCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = NoShoutingDeclarationCheckers
}

object NoShoutingDeclarationCheckers : DeclarationCheckers() {
    override val namedFunctionCheckers: Set<FirNamedFunctionChecker> = setOf(NoShoutingChecker)
}

object NoShoutingChecker : FirNamedFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirNamedFunction) {
        val name = declaration.name.asString()
        // Names without any uppercase letter, such as `_1`, equal their uppercase form and must not
        // be reported.
        if (name.none { it.isUpperCase() }) return
        if (name != name.uppercase()) return
        val source = declaration.source ?: return
        reporter.reportOn(source, NoShoutingDiagnostics.NO_SHOUTING, name)
    }
}
