package com.example.tag

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.getAnnotationArgumentValue
import org.jetbrains.kotlin.ir.util.getConstArgument
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isAnnotation
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * For every function annotated with `@com.example.Tag(name = ..., times = ...)`, replaces the body with
 * `return "<name> <name> ..."` (`name` repeated `times` times, space-separated).
 *
 * Both annotation arguments are read through the Kotlin 2.4.20 APIs:
 * - `name` via `IrAnnotation.getConstArgument<String>("name")`
 * - `times` via `IrAnnotationContainer.getAnnotationArgumentValue<Int>(fqName, "times")`
 * and cross-checked against the raw `IrAnnotation.argumentMapping` view.
 */
class TagIrGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoidWithContext() {
            override fun visitFunctionNew(declaration: IrFunction): IrStatement {
                val processed = super.visitFunctionNew(declaration) as IrFunction
                if (!processed.hasAnnotation(TAG_CLASS_ID)) return processed

                val annotation = processed.getAnnotation(TAG_FQ_NAME)
                    ?: error("hasAnnotation(ClassId) was true but getAnnotation(FqName) returned null on ${processed.name}")
                check(annotation.isAnnotation(TAG_CLASS_ID)) { "isAnnotation(ClassId) disagreed with hasAnnotation(ClassId)" }
                // finderForSource records the incremental-compilation lookup from this file.
                // Symbols are compared instead of owners because owner access needs the unsafe
                // IR-construction opt-in.
                val tagClassSymbol = pluginContext.finderForSource(currentFile).findClass(TAG_CLASS_ID)
                    ?: error("findClass(${TAG_CLASS_ID}) returned null")
                check(annotation.classSymbol == tagClassSymbol) { "classSymbol does not match findClass(TAG_CLASS_ID)" }
                check(processed.hasAnnotation(tagClassSymbol)) { "hasAnnotation(IrClassSymbol) disagreed with hasAnnotation(ClassId)" }

                val name = annotation.getConstArgument<String>("name")
                    ?: error("@Tag on ${processed.name} has no const 'name' argument")
                val times = processed.getAnnotationArgumentValue<Int>(TAG_FQ_NAME, "times")
                    ?: error("@Tag on ${processed.name} has no const 'times' argument")

                val rawName = (annotation.argumentMapping[Name.identifier("name")] as? IrConst)?.value
                val rawTimes = (annotation.argumentMapping[Name.identifier("times")] as? IrConst)?.value
                check(rawName == name && rawTimes == times) {
                    "argumentMapping disagrees with the helpers: name=$rawName/$name times=$rawTimes/$times"
                }

                val builder = DeclarationIrBuilder(pluginContext, processed.symbol)
                processed.body = builder.irBlockBody {
                    +irReturn(irString(List(times) { name }.joinToString(" ")))
                }
                return processed
            }
        })
    }

    private companion object {
        val TAG_CLASS_ID = ClassId(FqName("com.example"), Name.identifier("Tag"))
        val TAG_FQ_NAME: FqName = TAG_CLASS_ID.asSingleFqName()
    }
}
