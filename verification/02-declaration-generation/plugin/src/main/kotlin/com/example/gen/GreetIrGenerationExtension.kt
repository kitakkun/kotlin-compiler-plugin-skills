package com.example.gen

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class GreetIrGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoidWithContext() {
            override fun visitFunctionNew(declaration: IrFunction): IrStatement {
                val processed = super.visitFunctionNew(declaration) as IrFunction
                val origin = processed.origin
                if (origin !is IrDeclarationOrigin.GeneratedByPlugin) return processed
                if (origin.pluginKey != WithCompanionGeneratedDeclarationKey) return processed
                if (processed.name != WithCompanionGenerator.GREET_NAME) return processed

                val builder = DeclarationIrBuilder(pluginContext, processed.symbol)
                processed.body = builder.irBlockBody {
                    +irReturn(irString("hello"))
                }
                return processed
            }
        })
    }
}
