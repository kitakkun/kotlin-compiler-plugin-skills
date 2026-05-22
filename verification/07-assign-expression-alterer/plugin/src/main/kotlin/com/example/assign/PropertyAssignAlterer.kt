package com.example.assign

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirFunctionCallOrigin
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.buildUnaryArgumentList
import org.jetbrains.kotlin.fir.expressions.calleeReference
import org.jetbrains.kotlin.fir.expressions.contextArguments
import org.jetbrains.kotlin.fir.expressions.dispatchReceiver
import org.jetbrains.kotlin.fir.expressions.explicitReceiver
import org.jetbrains.kotlin.fir.expressions.extensionReceiver
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.builder.buildPropertyAccessExpression
import org.jetbrains.kotlin.fir.extensions.FirAssignExpressionAltererExtension
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildSimpleNamedReference
import org.jetbrains.kotlin.fir.references.toResolvedVariableSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularPropertySymbol
import org.jetbrains.kotlin.fir.types.upperBoundIfFlexible
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Rewrites `prop = value` to `prop.assign(value)` whenever the property's resolved
 * return type is `com.example.Property<T>`.
 */
class PropertyAssignAlterer(session: FirSession) : FirAssignExpressionAltererExtension(session) {

    override fun transformVariableAssignment(variableAssignment: FirVariableAssignment): FirStatement? {
        // Only rewrite when LHS resolved to a regular property.
        val propertySymbol = variableAssignment.calleeReference?.toResolvedVariableSymbol()
            as? FirRegularPropertySymbol ?: return null

        // Check the property's return type is com.example.Property<T>.
        val targetClassSymbol = propertySymbol.resolvedReturnType
            .upperBoundIfFlexible()
            .toRegularClassSymbol(session) ?: return null
        if (targetClassSymbol.classId != PROPERTY_CLASS_ID) return null

        // Build `lhs.assign(rhs)` as an unresolved FunctionCall — the compiler will re-resolve it.
        val lhsRef = variableAssignment.calleeReference as FirNamedReference
        val lhsResolvedTypeRef = propertySymbol.resolvedReturnTypeRef
        val rhs = variableAssignment.rValue

        return buildFunctionCall {
            source = variableAssignment.source
            explicitReceiver = buildPropertyAccessExpression {
                source = lhsRef.source
                coneTypeOrNull = lhsResolvedTypeRef.coneType
                calleeReference = lhsRef
                explicitReceiver = variableAssignment.explicitReceiver
                dispatchReceiver = variableAssignment.dispatchReceiver
                extensionReceiver = variableAssignment.extensionReceiver
                contextArguments += variableAssignment.contextArguments
            }
            argumentList = buildUnaryArgumentList(rhs)
            calleeReference = buildSimpleNamedReference {
                source = variableAssignment.source
                name = ASSIGN_NAME
            }
            origin = FirFunctionCallOrigin.Regular
        }
    }

    companion object {
        private val PROPERTY_CLASS_ID = ClassId(FqName("com.example"), Name.identifier("Property"))
        private val ASSIGN_NAME = Name.identifier("assign")
    }
}
