@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.example.irgen

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.declarations.addBackingField
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addDefaultGetter
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irConcat
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createDispatchReceiverParameterWithClassParent
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * For every top-level `@GenerateHolder class Foo` in the module (only direct `IrFile.declarations` are
 * inspected; annotated nested classes are out of scope for this probe), synthesizes a brand-new top-level class
 *
 * ```kotlin
 * class FooHolder(value: String) {
 *     val value: String = value
 *     fun describe(): String = "holder:" + value
 * }
 * ```
 *
 * next to `Foo` in the same file, and makes the whole class (constructor, property, function)
 * visible to downstream modules with ONE `registerClassAsMetadataVisible` call.
 */
class GenerateHolderIrGenerationExtension : IrGenerationExtension {
    companion object {
        private val ANNOTATION_FQN = FqName("com.example.GenerateHolder")
    }

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitFile(declaration: IrFile) {
                val annotated = declaration.declarations
                    .filterIsInstance<IrClass>()
                    .filter { it.hasAnnotation(ANNOTATION_FQN) }
                for (source in annotated) {
                    val holder = buildHolderClass(pluginContext, declaration, source)
                    declaration.declarations += holder
                    // The registrar also walks the holder's constructor, property and function, so
                    // this one call exposes all of them.
                    pluginContext.metadataDeclarationRegistrar.registerClassAsMetadataVisible(holder)

                    val nested = buildNestedClass(pluginContext, source)
                    source.declarations += nested
                    // The source class is not plugin-generated, so its generated nested class is
                    // registered on its own.
                    pluginContext.metadataDeclarationRegistrar.registerClassAsMetadataVisible(nested)
                }
            }
        })
    }

    private fun buildHolderClass(pluginContext: IrPluginContext, file: IrFile, source: IrClass): IrClass {
        val irBuiltIns = pluginContext.irBuiltIns

        val holder = pluginContext.irFactory.buildClass {
            name = Name.identifier(source.name.identifier + "Holder")
            kind = ClassKind.CLASS
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.FINAL
            origin = IrDeclarationOrigin.DEFINED
        }.apply {
            parent = file
            superTypes = listOf(irBuiltIns.anyType)
            createThisReceiverParameter()
        }

        val valueProperty = holder.addProperty {
            name = Name.identifier("value")
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.FINAL
        }
        val valueField = valueProperty.addBackingField {
            type = irBuiltIns.stringType
            isFinal = true
        }
        valueProperty.addDefaultGetter(holder, irBuiltIns)

        holder.addConstructor {
            isPrimary = true
            visibility = DescriptorVisibilities.PUBLIC
        }.apply {
            val valueParameter = addValueParameter("value", irBuiltIns.stringType)
            val builder = DeclarationIrBuilder(pluginContext, symbol)
            body = builder.irBlockBody {
                +irDelegatingConstructorCall(irBuiltIns.anyClass.owner.constructors.single())
                +IrInstanceInitializerCallImpl(
                    startOffset, endOffset,
                    classSymbol = holder.symbol,
                    type = irBuiltIns.unitType,
                )
                +irSetField(irGet(holder.thisReceiver!!), valueField, irGet(valueParameter))
            }
        }

        holder.addFunction {
            name = Name.identifier("describe")
            returnType = irBuiltIns.stringType
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.FINAL
        }.apply {
            // The addFunction DSL does not add a dispatch receiver, so the function sets one
            // explicitly.
            val dispatchReceiver = createDispatchReceiverParameterWithClassParent()
            parameters = listOf(dispatchReceiver)
            val builder = DeclarationIrBuilder(pluginContext, symbol)
            body = builder.irBlockBody {
                +irReturn(
                    irConcat().apply {
                        arguments += irString("holder:")
                        arguments += irGetField(irGet(dispatchReceiver), valueField)
                    }
                )
            }
        }

        return holder
    }

    /** `class Nested { fun ping(): String = "nested-in-<Outer>" }` placed inside [outer]. */
    private fun buildNestedClass(pluginContext: IrPluginContext, outer: IrClass): IrClass {
        val irBuiltIns = pluginContext.irBuiltIns
        val nested = pluginContext.irFactory.buildClass {
            name = Name.identifier("Nested")
            kind = ClassKind.CLASS
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.FINAL
            origin = IrDeclarationOrigin.DEFINED
        }.apply {
            parent = outer
            superTypes = listOf(irBuiltIns.anyType)
            createThisReceiverParameter()
        }
        nested.addConstructor {
            isPrimary = true
            visibility = DescriptorVisibilities.PUBLIC
        }.apply {
            val builder = DeclarationIrBuilder(pluginContext, symbol)
            body = builder.irBlockBody {
                +irDelegatingConstructorCall(irBuiltIns.anyClass.owner.constructors.single())
                +IrInstanceInitializerCallImpl(
                    startOffset, endOffset,
                    classSymbol = nested.symbol,
                    type = irBuiltIns.unitType,
                )
            }
        }
        nested.addFunction {
            name = Name.identifier("ping")
            returnType = irBuiltIns.stringType
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.FINAL
        }.apply {
            parameters = listOf(createDispatchReceiverParameterWithClassParent())
            val builder = DeclarationIrBuilder(pluginContext, symbol)
            body = builder.irBlockBody {
                +irReturn(irString("nested-in-" + outer.name.identifier))
            }
        }
        return nested
    }
}
