package com.example.inlineplugin.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.extensions.transform
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.name.FqName

private val MAKE_INLINE_FQN = FqName("com.example.MakeInline")

private val MAKE_INLINE_PREDICATE = DeclarationPredicate.create {
    annotated(MAKE_INLINE_FQN)
}

class MakeInlineStatusTransformer(session: FirSession) : FirStatusTransformerExtension(session) {

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(MAKE_INLINE_PREDICATE)
    }

    override fun needTransformStatus(declaration: FirDeclaration): Boolean =
        session.predicateBasedProvider.matches(MAKE_INLINE_PREDICATE, declaration)

    override fun transformStatus(
        status: FirDeclarationStatus,
        function: FirNamedFunction,
        containingClass: FirClassLikeSymbol<*>?,
        isLocal: Boolean,
    ): FirDeclarationStatus {
        if (isLocal) return status
        // Use the `transform { ... }` helper which preserves all existing flags
        // (visibility, modality, isOperator, isInfix, ...) and lets us flip
        // arbitrary mutable flags such as isInline via the init block.
        return status.transform { isInline = true }
    }
}
