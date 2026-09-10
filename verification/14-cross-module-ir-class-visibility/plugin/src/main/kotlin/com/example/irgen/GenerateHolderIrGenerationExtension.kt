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
 * For every `@GenerateHolder class Foo` in the module, synthesizes a brand-new top-level class
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
                // Collect first, then mutate, so we do not append to `declarations` while iterating.
                val annotated = declaration.declarations
                    .filterIsInstance<IrClass>()
                    .filter { it.hasAnnotation(ANNOTATION_FQN) }
                for (source in annotated) {
                    val holder = buildHolderClass(pluginContext, declaration, source)
                    declaration.declarations += holder
                    // Single call: the registrar walks constructors, functions, and properties itself.
                    pluginContext.metadataDeclarationRegistrar.registerClassAsMetadataVisible(holder)

                    // Optional extra: a nested class `Foo.Nested` inside the SOURCE class. The outer
                    // class is not plugin-generated, so the nested class is registered on its own.
                    val nested = buildNestedClass(pluginContext, source)
                    source.declarations += nested
                    pluginContext.metadataDeclarationRegistrar.registerClassAsMetadataVisible(nested)
                }
            }
        })
    }

    private fun buildHolderClass(pluginContext: IrPluginContext, file: IrFile, source: IrClass): IrClass {
        val irBuiltIns = pluginContext.irBuiltIns

        // Step 1: the class shell.
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

        // Step 3 (before the constructor so its body can assign the field): `val value: String`.
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

        // Step 2: `constructor(value: String)` that calls Any(), runs the initializer, then stores the field.
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

        // Step 4: `fun describe(): String = "holder:" + value`.
        holder.addFunction {
            name = Name.identifier("describe")
            returnType = irBuiltIns.stringType
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.FINAL
        }.apply {
            // The DSL overload of addFunction does not add a dispatch receiver (guide: "Common gotchas").
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
