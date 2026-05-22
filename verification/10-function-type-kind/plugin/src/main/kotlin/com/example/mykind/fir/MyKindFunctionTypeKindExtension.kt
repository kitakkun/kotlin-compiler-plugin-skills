package com.example.mykind.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirFunctionTypeKindExtension

/**
 * Registers the [MyKind] / [KMyKind] function-type pair with the FIR resolver so the
 * compiler treats `@com.example.MyKind () -> Unit` as a distinct type from `() -> Unit`.
 */
class MyKindFunctionTypeKindExtension(session: FirSession) : FirFunctionTypeKindExtension(session) {
    override fun FunctionTypeKindRegistrar.registerKinds() {
        registerKind(nonReflectKind = MyKind, reflectKind = KMyKind)
    }
}
