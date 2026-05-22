package com.example.positive

import org.jetbrains.kotlin.fir.types.ConeAttribute
import org.jetbrains.kotlin.fir.types.ConeAttributes
import kotlin.reflect.KClass

/**
 * Type attribute marking that an `Int` carries the `@com.example.Positive` refinement.
 * Two values: PRESENT (singleton). The attribute is attached to a `ConeKotlinType`
 * by the `FirTypeAttributeExtension` whenever a `@Positive` annotation appears on a
 * type usage, and is read back via the `ConeAttributes.positive` accessor.
 */
class ConePositiveAttribute private constructor() : ConeAttribute<ConePositiveAttribute>() {

    // Two `@Positive` annotated types unify to `@Positive`; mismatch drops the attribute.
    private fun combine(other: ConePositiveAttribute?): ConePositiveAttribute? =
        if (other === INSTANCE) INSTANCE else null

    override fun union(other: ConePositiveAttribute?): ConePositiveAttribute? = combine(other)
    override fun intersect(other: ConePositiveAttribute?): ConePositiveAttribute? = combine(other)
    override fun add(other: ConePositiveAttribute?): ConePositiveAttribute? = combine(other)

    // Subtype relation in the type-system core; the actual refinement check is done
    // by the FirFunctionCallChecker. Returning true here keeps the rest of the type
    // system happy when carrying the attribute through subtyping.
    override fun isSubtypeOf(other: ConePositiveAttribute?): Boolean = true

    override fun toString(): String = "@Positive"

    override val key: KClass<out ConePositiveAttribute> = ConePositiveAttribute::class

    // Refinement attributes must persist through declaration-type approximation.
    override val keepInInferredDeclarationType: Boolean = true

    override val implementsEquality: Boolean = true

    companion object {
        val INSTANCE: ConePositiveAttribute = ConePositiveAttribute()
    }
}

// REQUIRED accessor — without it nothing downstream can read the attribute back from a type.
val ConeAttributes.positive: ConePositiveAttribute? by ConeAttributes.attributeAccessor<ConePositiveAttribute>()
