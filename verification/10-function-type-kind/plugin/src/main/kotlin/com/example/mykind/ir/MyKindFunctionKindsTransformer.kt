package com.example.mykind.ir

import com.example.mykind.fir.MyKindNames
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.builtins.functions.FunctionTypeKind
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.impl.IrAnnotationImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.AbstractIrTypeSubstitutor
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrStarProjection
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/**
 * Lowers the synthetic `com.example.mykind.synthetic.MyKindFunctionN` /
 * `KMyKindFunctionN` types back to the built-in `kotlin.FunctionN` /
 * `kotlin.reflect.KFunctionN` so the JVM backend can emit valid bytecode.
 *
 * Without this pass codegen aborts with a `ClassNotFoundException` for the
 * synthetic kind classes (they have no real class file). The user-facing
 * `@MyKind` annotation is preserved on every lambda/function reference so
 * runtime introspection still observes the marker.
 *
 * This is a direct adaptation of `PluginFunctionKindsTransformer` from the
 * Kotlin sandbox plugin (kotlin v2.3.21,
 * `plugins/plugin-sandbox/src/.../ir/PluginFunctionKindsTransformer.kt`).
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
class MyKindFunctionKindsTransformer(private val pluginContext: IrPluginContext) : IrVisitorVoid() {

    private companion object {
        val INVOKE: Name = Name.identifier("invoke")
    }

    private val annotationClassId: ClassId = MyKindNames.ANNOTATION_CLASS_ID
    private val finder = pluginContext.finderForBuiltins()

    private val annotationSymbol: IrClassSymbol
        get() = finder.findClass(annotationClassId)
            ?: error("Cannot find @MyKind annotation class on classpath: $annotationClassId")

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitValueParameter(declaration: IrValueParameter) {
        declaration.type = declaration.type.update()
        visitElement(declaration)
    }

    override fun visitFunction(declaration: IrFunction) {
        declaration.returnType = declaration.returnType.update()
        visitElement(declaration)
    }

    override fun visitVariable(declaration: IrVariable) {
        declaration.type = declaration.type.update()
        visitElement(declaration)
    }

    override fun visitExpression(expression: IrExpression) {
        expression.type = expression.type.update()
        visitElement(expression)
    }

    override fun visitCall(expression: IrCall) {
        updateInvokeReferenceIfNeeded(expression)
        visitElement(expression)
    }

    override fun visitFunctionExpression(expression: IrFunctionExpression) {
        if (expression.type.isMyKindFunction()) {
            expression.function.markWithMyKind()
        }
        super.visitFunctionExpression(expression)
    }

    override fun visitFunctionReference(expression: IrFunctionReference) {
        if (expression.type.isMyKindFunction()) {
            expression.symbol.owner.markWithMyKind()
        }
        super.visitFunctionReference(expression)
    }

    private fun IrType.isMyKindFunction(): Boolean {
        val cls = classOrNull?.owner ?: return false
        val pkg = cls.packageFqName?.asString() ?: return false
        if (pkg != MyKindNames.SYNTHETIC_PACKAGE.asString()) return false
        val name = cls.name.asString()
        return name.startsWith(MyKindNames.NON_REFLECT_PREFIX) ||
            name.startsWith(MyKindNames.REFLECT_PREFIX)
    }

    private fun IrFunction.markWithMyKind() {
        if (hasAnnotation(annotationClassId)) return
        val symbol = annotationSymbol
        annotations = annotations + IrAnnotationImpl.fromSymbolOwner(
            symbol.owner.defaultType,
            symbol.constructors.single(),
        )
    }

    private fun updateInvokeReferenceIfNeeded(call: IrCall) {
        val function = call.symbol.owner
        if (function.name != INVOKE) return
        val owner = function.parent as? IrClass ?: return
        val replacementClass = calculateUpdatedClass(owner) ?: return
        val newInvoke = replacementClass.functions.firstOrNull { it.name == INVOKE } ?: return
        call.symbol = newInvoke.symbol
    }

    private fun IrType.update(): IrType = substitutor.substitute(this)

    private val substitutor = TypeSubstitutor()

    private inner class TypeSubstitutor : AbstractIrTypeSubstitutor() {
        override fun substitute(type: IrType): IrType {
            if (type !is IrSimpleType) return type
            val newArguments = type.arguments.map { substituteArgument(it) }
            val newClassifier = calculateUpdatedClassifier(type.classifier) ?: type.classifier
            return IrSimpleTypeImpl(
                newClassifier,
                type.nullability,
                newArguments,
                type.annotations,
            )
        }

        private fun substituteArgument(typeArgument: IrTypeArgument): IrTypeArgument = when (typeArgument) {
            is IrStarProjection -> typeArgument
            is IrType -> substitute(typeArgument)
            is IrTypeProjection -> typeArgument
        }
    }

    private fun calculateUpdatedClass(irClass: IrClass): IrClass? =
        calculateUpdatedClassifier(irClass.symbol)?.owner as? IrClass

    /**
     * Maps a synthetic `MyKindFunctionN` to `kotlin.FunctionN` and a
     * `KMyKindFunctionN` to `kotlin.reflect.KFunctionN`.
     */
    private fun calculateUpdatedClassifier(classifier: IrClassifierSymbol): IrClassifierSymbol? {
        val irClass = classifier.owner as? IrClass ?: return null
        val fqNameString = irClass.fqNameWhenAvailable?.asString() ?: return null

        val (prefix, replacementKind) = when {
            fqNameString.startsWith(MyKindNames.FULL_NON_REFLECT_PREFIX) ->
                MyKindNames.FULL_NON_REFLECT_PREFIX to FunctionTypeKind.Function
            fqNameString.startsWith(MyKindNames.FULL_REFLECT_PREFIX) ->
                MyKindNames.FULL_REFLECT_PREFIX to FunctionTypeKind.KFunction
            else -> return null
        }

        val arity = fqNameString.removePrefix(prefix).toIntOrNull() ?: return null
        val replacementClassId = ClassId(
            replacementKind.packageFqName,
            Name.identifier("${replacementKind.classNamePrefix}$arity"),
        )
        return finder.findClass(replacementClassId)
    }
}
