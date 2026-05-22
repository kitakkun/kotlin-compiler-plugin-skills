package com.example.dslcontext

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionAndScopeSessionHolder
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.builder.buildReceiverParameter
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.calls.ImplicitExtensionReceiverValue
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirReceiverParameterSymbol
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * For every function call inside an `@com.example.DslContext`-annotated callable,
 * add `com.example.Dsl` as an implicit extension receiver in the surrounding body
 * scope. This allows users to call top-level extension functions on `Dsl` (such as
 * `Dsl.greet()`) without writing the explicit receiver.
 *
 * Pattern follows `plugins/plugin-sandbox/.../AlgebraReceiverInjector.kt`. Note
 * that `addNewImplicitReceivers` is invoked AFTER each call site finishes
 * resolving (`FirExpressionsResolveTransformer.addReceiversFromExtensions`),
 * so the receiver becomes visible to *subsequent* calls in the same body — the
 * very first call in the body cannot use it.
 */
class DslContextReceiverInjector(session: FirSession) : FirExpressionResolutionExtension(session) {

    companion object {
        private val DSL_CONTEXT_FQN = FqName("com.example.DslContext")
        private val DSL_CLASS_ID = ClassId(FqName("com.example"), Name.identifier("Dsl"))

        private val DSL_CONTEXT_PREDICATE = DeclarationPredicate.create {
            annotated(DSL_CONTEXT_FQN)
        }
    }

    private object Key : GeneratedDeclarationKey() {
        override fun toString(): String = "DslContextReceiverInjectorKey"
    }

    private val pluginOrigin = FirDeclarationOrigin.Plugin(Key)

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(DSL_CONTEXT_PREDICATE)
    }

    override fun addNewImplicitReceivers(
        functionCall: FirFunctionCall,
        sessionHolder: SessionAndScopeSessionHolder,
        containingCallableSymbol: FirBasedSymbol<*>,
    ): List<ImplicitExtensionReceiverValue> {
        // Only inject for calls inside callables annotated with @DslContext.
        if (containingCallableSymbol !is FirCallableSymbol<*>) return emptyList()
        if (!session.predicateBasedProvider.matches(DSL_CONTEXT_PREDICATE, containingCallableSymbol)) {
            return emptyList()
        }

        // Resolve the Dsl class type via the symbol provider.
        val dslClassSymbol = session.symbolProvider.getClassLikeSymbolByClassId(DSL_CLASS_ID)
            ?: return emptyList()
        val dslType = dslClassSymbol.constructType()

        // Build a synthetic receiver parameter attached to the containing callable.
        val receiverParameter = buildReceiverParameter {
            resolvePhase = FirResolvePhase.BODY_RESOLVE
            moduleData = session.moduleData
            origin = pluginOrigin
            symbol = FirReceiverParameterSymbol()
            containingDeclarationSymbol = containingCallableSymbol
            typeRef = buildResolvedTypeRef {
                coneType = dslType
            }
        }

        return listOf(
            ImplicitExtensionReceiverValue(
                receiverParameter.symbol,
                dslType,
                sessionHolder.session,
                sessionHolder.scopeSession,
            )
        )
    }
}
