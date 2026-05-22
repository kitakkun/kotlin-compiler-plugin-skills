package com.example.gen

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createCompanionObject
import org.jetbrains.kotlin.fir.plugin.createDefaultPrivateConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class WithCompanionGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {

    companion object {
        private val WITH_COMPANION_FQN = FqName("com.example.WithCompanion")
        private val WITH_COMPANION_PREDICATE = DeclarationPredicate.create {
            annotated(WITH_COMPANION_FQN)
        }
        val GREET_NAME: Name = Name.identifier("greet")
    }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(WITH_COMPANION_PREDICATE)
    }

    private fun matchesMarker(classSymbol: FirClassSymbol<*>): Boolean =
        session.predicateBasedProvider.matches(WITH_COMPANION_PREDICATE, classSymbol)

    private fun isOurGenerated(classSymbol: FirClassSymbol<*>): Boolean {
        val origin = classSymbol.origin as? FirDeclarationOrigin.Plugin ?: return false
        return origin.key == WithCompanionGeneratedDeclarationKey
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext,
    ): Set<Name> {
        if (!matchesMarker(classSymbol)) return emptySet()
        return setOf(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext,
    ): FirClassLikeSymbol<*>? {
        if (name != SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) return null
        if (!matchesMarker(owner)) return null
        return createCompanionObject(owner = owner, key = WithCompanionGeneratedDeclarationKey).symbol
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext,
    ): Set<Name> {
        // For our synthesised companion, advertise INIT (constructor) and `greet`.
        if (isOurGenerated(classSymbol)) {
            return setOf(SpecialNames.INIT, GREET_NAME)
        }
        return emptySet()
    }

    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        val owner = context.owner
        if (!isOurGenerated(owner)) return emptyList()
        return listOf(
            createDefaultPrivateConstructor(
                owner = owner,
                key = WithCompanionGeneratedDeclarationKey,
            ).symbol,
        )
    }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?,
    ): List<FirNamedFunctionSymbol> {
        if (callableId.callableName != GREET_NAME) return emptyList()
        val owner = context?.owner ?: return emptyList()
        if (!isOurGenerated(owner)) return emptyList()

        val function = createMemberFunction(
            owner = owner,
            key = WithCompanionGeneratedDeclarationKey,
            name = GREET_NAME,
            returnType = session.builtinTypes.stringType.coneType,
        )
        return listOf(function.symbol)
    }
}
