package com.example.session

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.copyWithNewDefaults
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.extensions.transform
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol

class TrackedOpener(session: FirSession) : FirStatusTransformerExtension(session) {

    override fun needTransformStatus(declaration: FirDeclaration): Boolean {
        // Consume from the SAME session component instance that the checker
        // queries. The component owns the predicate, the cache, and the
        // shared Set<ClassId> that drives both extensions.
        if (declaration !is FirRegularClass) return false
        return session.trackedRegistry.isTracked(declaration.symbol)
    }

    override fun transformStatus(
        status: FirDeclarationStatus,
        regularClass: FirRegularClass,
        containingClass: FirClassLikeSymbol<*>?,
        isLocal: Boolean,
    ): FirDeclarationStatus {
        // FIR represents not-yet-resolved modality as null with FINAL as the default
        // for top-level classes; flipping that default to OPEN is the canonical
        // way to express "make this class open" in a status transformer. When the
        // user has explicitly written `final`, we still flip it (Modality.FINAL → OPEN).
        return when (status.modality) {
            null -> status.copyWithNewDefaults(modality = Modality.OPEN, defaultModality = Modality.OPEN)
            Modality.FINAL -> status.transform(modality = Modality.OPEN)
            else -> status
        }
    }
}
