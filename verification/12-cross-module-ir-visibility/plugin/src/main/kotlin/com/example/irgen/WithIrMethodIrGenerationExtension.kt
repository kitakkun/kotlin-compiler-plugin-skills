@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.example.irgen

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.createDispatchReceiverParameterWithClassParent
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class WithIrMethodIrGenerationExtension : IrGenerationExtension {
    companion object {
        private val ANNOTATION_FQN = FqName("com.example.WithIrMethod")
        private val GREET_NAME = Name.identifier("greet")
    }

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitClass(declaration: IrClass) {
                super.visitClass(declaration)
                if (!declaration.hasAnnotation(ANNOTATION_FQN)) return
                // Avoid double-adding if greet already exists for any reason.
                if (declaration.declarations.any { it.let { d -> d is org.jetbrains.kotlin.ir.declarations.IrSimpleFunction && d.name == GREET_NAME } }) {
                    return
                }

                val greetFun = declaration.addFunction {
                    name = GREET_NAME
                    returnType = pluginContext.irBuiltIns.stringType
                    visibility = DescriptorVisibilities.PUBLIC
                    modality = Modality.FINAL
                    origin = IrDeclarationOrigin.DEFINED
                }.apply {
                    // The bare `IrClass.addFunction { }` overload does NOT add a dispatch receiver,
                    // so without this the function would be emitted as a JVM-static method while
                    // metadata exposes it as a regular member — causing IncompatibleClassChangeError
                    // in downstream callers.
                    parameters = listOf(createDispatchReceiverParameterWithClassParent())
                    val builder = DeclarationIrBuilder(pluginContext, symbol)
                    body = builder.irBlockBody {
                        +irReturn(irString("ir-generated"))
                    }
                }

                // Critical: make the IR-added function visible to downstream modules' frontend
                // by writing it into the published `.kotlin_metadata` payload of this module.
                pluginContext.metadataDeclarationRegistrar.registerFunctionAsMetadataVisible(greetFun)
            }
        })
    }
}
