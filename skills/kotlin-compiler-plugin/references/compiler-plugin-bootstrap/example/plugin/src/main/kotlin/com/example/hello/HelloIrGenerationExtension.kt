package com.example.hello

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class HelloIrGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val out = StringBuilder()
        out.appendLine("=== HelloPlugin: scanning module '${moduleFragment.name}' ===")
        moduleFragment.files.forEach { file ->
            out.appendLine("  [file] ${file.fileEntry.name}")
            file.declarations.forEach { decl ->
                val name = (decl as? IrDeclarationWithName)?.name?.asString() ?: "<unnamed>"
                out.appendLine("    [${decl::class.simpleName}] $name")
            }
        }
        // Write to a known location so we can verify plugin actually ran
        java.io.File("/tmp/hello-plugin-output.txt").appendText(out.toString())
        println(out.toString())
        System.err.println(out.toString())
    }
}
