package com.example.supertype

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.resolve.getContainingDeclaration
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class TaggedSupertypeGenerator(session: FirSession) : FirSupertypeGenerationExtension(session) {

    companion object {
        private val TAGGED_FQN = FqName("com.example.Tagged")
        private val MARKER_CLASS_ID = ClassId(FqName("com.example"), Name.identifier("Marker"))
        private val TAGGED_PREDICATE = DeclarationPredicate.create { annotated(TAGGED_FQN) }
    }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(TAGGED_PREDICATE)
    }

    override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean {
        // Match the companion object whose containing class is annotated with @Tagged
        if (declaration !is FirRegularClass) return false
        if (!declaration.isCompanion) return false
        val parent = declaration.symbol.getContainingDeclaration(session) as? FirClassSymbol<*>
            ?: return false
        return session.predicateBasedProvider.matches(TAGGED_PREDICATE, parent)
    }

    override fun computeAdditionalSupertypes(
        classLikeDeclaration: FirClassLikeDeclaration,
        resolvedSupertypes: List<FirResolvedTypeRef>,
        typeResolver: TypeResolveService,
    ): List<ConeKotlinType> {
        // Avoid adding Marker if already present
        if (resolvedSupertypes.any { it.coneType.classId == MARKER_CLASS_ID }) return emptyList()
        // Resolve the symbol just to confirm Marker exists on the classpath; bail otherwise.
        session.symbolProvider.getClassLikeSymbolByClassId(MARKER_CLASS_ID) ?: return emptyList()
        return listOf(
            MARKER_CLASS_ID.constructClassLikeType(emptyArray(), isMarkedNullable = false)
        )
    }
}
