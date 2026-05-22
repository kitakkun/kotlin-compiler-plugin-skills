package com.example.openplugin.fir

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.copyWithNewDefaults
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.name.FqName

private val OPEN_FQN = FqName("com.example.Open")

private val OPEN_PREDICATE = DeclarationPredicate.create {
    annotated(OPEN_FQN) or ancestorAnnotated(OPEN_FQN)
}

class MakeOpenStatusTransformer(session: FirSession) : FirStatusTransformerExtension(session) {

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(OPEN_PREDICATE)
    }

    override fun needTransformStatus(declaration: FirDeclaration): Boolean =
        session.predicateBasedProvider.matches(OPEN_PREDICATE, declaration)

    override fun transformStatus(
        status: FirDeclarationStatus,
        regularClass: FirRegularClass,
        containingClass: FirClassLikeSymbol<*>?,
        isLocal: Boolean,
    ): FirDeclarationStatus = makeOpen(status)

    override fun transformStatus(
        status: FirDeclarationStatus,
        function: FirNamedFunction,
        containingClass: FirClassLikeSymbol<*>?,
        isLocal: Boolean,
    ): FirDeclarationStatus {
        if (isLocal) return status
        return makeOpen(status)
    }

    override fun transformStatus(
        status: FirDeclarationStatus,
        property: FirProperty,
        containingClass: FirClassLikeSymbol<*>?,
        isLocal: Boolean,
    ): FirDeclarationStatus {
        if (isLocal) return status
        return makeOpen(status)
    }

    private fun makeOpen(status: FirDeclarationStatus): FirDeclarationStatus {
        return when (status.modality) {
            null -> status.copyWithNewDefaults(modality = Modality.OPEN, defaultModality = Modality.OPEN)
            Modality.FINAL -> status.copyWithNewDefaults(defaultModality = Modality.OPEN)
            else -> status
        }
    }
}
