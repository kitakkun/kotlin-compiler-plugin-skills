package com.example.dslcontext

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrErrorCallExpression
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.FqName

/**
 * The [DslContextReceiverInjector] FIR extension makes `greet()` (an extension on
 * `com.example.Dsl`) resolve inside `@DslContext`-annotated functions. However, the
 * synthetic receiver it produces has no real runtime value, so fir2ir leaves an
 * `IrErrorCallExpression` (`Unresolved reference: this@runDsl`) at the receiver
 * slot. This IR pass rewrites those error expressions to a load of the first
 * parameter of the enclosing function whose type is `com.example.Dsl`, giving the
 * call a real runtime receiver.
 */
class DslContextIrGenerationExtension : IrGenerationExtension {

    private val dslContextFqn = FqName("com.example.DslContext")
    private val dslFqn = FqName("com.example.Dsl")

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.acceptVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitSimpleFunction(declaration: IrSimpleFunction) {
                if (declaration.hasAnnotation(dslContextFqn)) {
                    val dslParam = declaration.parameters.firstOrNull { it.type.classFqName == dslFqn }
                    if (dslParam != null) {
                        declaration.transform(ErrorReceiverRewriter(dslFqn, dslParam), null)
                    }
                }
                super.visitSimpleFunction(declaration)
            }
        })
    }
}

/**
 * Replaces every [IrErrorCallExpression] whose type is `com.example.Dsl` with a
 * load of the supplied parameter (which must also be of type `Dsl`).
 */
private class ErrorReceiverRewriter(
    private val dslFqn: FqName,
    private val dslParam: IrValueParameter,
) : IrTransformer<Nothing?>() {
    override fun visitErrorCallExpression(expression: IrErrorCallExpression, data: Nothing?): IrExpression {
        if (expression.type.classFqName == dslFqn) {
            return IrGetValueImpl(
                startOffset = expression.startOffset,
                endOffset = expression.endOffset,
                type = dslParam.type,
                symbol = dslParam.symbol,
            )
        }
        return super.visitErrorCallExpression(expression, data)
    }
}
