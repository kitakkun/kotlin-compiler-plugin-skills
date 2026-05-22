package com.example.positive

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.extensions.FirTypeAttributeExtension
import org.jetbrains.kotlin.fir.types.ConeAttribute
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Bridges `@com.example.Positive` annotations on types and the
 * [ConePositiveAttribute] used during FIR type inference / checking.
 *
 *   `extractAttributeFromAnnotation` — analysis time: `@Positive Int` source -> attribute on the type
 *   `convertAttributeToAnnotation` — serialization time: attribute on the type -> `@Positive` in metadata
 */
class PositiveAttributeExtension(session: FirSession) : FirTypeAttributeExtension(session) {

    override fun extractAttributeFromAnnotation(annotation: FirAnnotation): ConeAttribute<*>? {
        val classId = annotation.annotationTypeRef.coneTypeOrNull?.classId ?: return null
        if (classId != POSITIVE_CLASS_ID) return null
        return ConePositiveAttribute.INSTANCE
    }

    override fun convertAttributeToAnnotation(attribute: ConeAttribute<*>): FirAnnotation? {
        // CRITICAL: Never convert attributes that aren't ours — corrupts metadata.
        if (attribute !is ConePositiveAttribute) return null
        return buildAnnotation {
            annotationTypeRef = buildResolvedTypeRef {
                coneType = ConeClassLikeTypeImpl(
                    POSITIVE_CLASS_ID.toLookupTag(),
                    ConeTypeProjection.EMPTY_ARRAY,
                    isMarkedNullable = false,
                )
            }
            argumentMapping = FirEmptyAnnotationArgumentMapping
        }
    }

    companion object {
        private val PACKAGE_FQN = FqName("com.example")
        val POSITIVE_CLASS_ID = ClassId(PACKAGE_FQN, Name.identifier("Positive"))
    }
}
