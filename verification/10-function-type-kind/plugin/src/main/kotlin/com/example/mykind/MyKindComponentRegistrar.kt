package com.example.mykind

import com.example.mykind.ir.MyKindIrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalCompilerApi::class)
class MyKindComponentRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "com.example.my-kind"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrarAdapter.registerExtension(MyKindFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(MyKindIrGenerationExtension())
    }
}
