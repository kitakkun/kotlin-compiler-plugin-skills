package com.example.samreceiver.fir

import org.jetbrains.kotlin.builtins.functions.FunctionTypeKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.containingClassLookupTag
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.resolve.FirSamConversionTransformerExtension
import org.jetbrains.kotlin.fir.resolve.createFunctionType
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.functionTypeService
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.runIf

class WithReceiverSamConversionTransformer(session: FirSession) : FirSamConversionTransformerExtension(session) {
    override fun getCustomFunctionTypeForSamConversion(function: FirNamedFunction): ConeLookupTagBasedType? {
        // Annotation lookup is on the SAM's containing class, not the abstract method.
        val containingClassSymbol = function.containingClassLookupTag()?.toRegularClassSymbol(session) ?: return null
        return runIf(containingClassSymbol.resolvedAnnotationClassIds.any { it == MARKER_CLASS_ID }) {
            val parameterTypes = function.valueParameters.map { it.returnTypeRef.coneType }
            // Need at least one parameter to promote.
            if (parameterTypes.isEmpty()) return null
            val kind = session.functionTypeService.extractSingleSpecialKindForFunction(function.symbol)
                ?: FunctionTypeKind.Function
            createFunctionType(
                kind,
                parameters = parameterTypes.subList(1, parameterTypes.size),
                receiverType = parameterTypes[0],
                rawReturnType = function.returnTypeRef.coneType,
            )
        }
    }

    companion object {
        private val MARKER_CLASS_ID = ClassId(FqName("com.example"), Name.identifier("WithReceiver"))
    }
}
