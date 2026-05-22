package com.example.checker

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.name.FqName

private val MUST_BE_FINAL_FQN = FqName("com.example.MustBeFinal")

private val MUST_BE_FINAL_PREDICATE = DeclarationPredicate.create {
    annotated(MUST_BE_FINAL_FQN)
}

class MustBeFinalCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {

    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirRegularClassChecker> = setOf(MustBeFinalRegularClassChecker)
    }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(MUST_BE_FINAL_PREDICATE)
    }
}

object MustBeFinalRegularClassChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        if (!context.session.predicateBasedProvider.matches(MUST_BE_FINAL_PREDICATE, declaration)) return
        if (declaration.status.modality != Modality.OPEN) return
        val src = declaration.source ?: return
        reporter.reportOn(src, MustBeFinalDiagnostics.MUST_BE_FINAL_OPEN)
    }
}
