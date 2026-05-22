package com.example.session

import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

internal val TRACKED_FQN = FqName("com.example.Tracked")

private val TRACKED_PREDICATE: DeclarationPredicate = DeclarationPredicate.create {
    annotated(TRACKED_FQN)
}

/**
 * Per-session component shared between the checker and the status transformer.
 *
 * Owns the predicate that matches @Tracked classes and exposes a stable
 * `Set<ClassId>` of every class that has been classified so far. The set is
 * populated lazily on the first `isTracked` query for a given symbol; both
 * extensions consume from the SAME instance, so the second extension sees
 * the entries cached by the first.
 */
class TrackedRegistry(session: FirSession) : FirExtensionSessionComponent(session) {

    // Shared mutable view of the tracked classes. Backed by a thread-safe set
    // so the checker and the status transformer can both observe entries
    // accumulated by the other one. Pure ClassId values, as required by the
    // verification spec.
    private val trackedClassIds: MutableSet<ClassId> = ConcurrentHashMap.newKeySet()

    // Per-session memoisation of the predicate match result. Using
    // firCachesFactory ensures the cache participates in IDE invalidation.
    private val matchCache: FirCache<FirClassLikeSymbol<*>, Boolean, Nothing?> =
        session.firCachesFactory.createCache { symbol, _ ->
            session.predicateBasedProvider.matches(TRACKED_PREDICATE, symbol)
        }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(TRACKED_PREDICATE)
    }

    /**
     * Returns true when the class is annotated with `@Tracked`, recording its
     * ClassId in the shared set so the other extension can observe it.
     */
    fun isTracked(symbol: FirClassLikeSymbol<*>): Boolean {
        val matched = matchCache.getValue(symbol, null)
        if (matched && symbol is FirRegularClassSymbol) {
            trackedClassIds.add(symbol.classId)
        }
        return matched
    }

    /** Read-only view of every ClassId classified as tracked so far in this session. */
    val trackedClassIdsSnapshot: Set<ClassId>
        get() = trackedClassIds.toSet()
}

val FirSession.trackedRegistry: TrackedRegistry by FirSession.sessionComponentAccessor()
