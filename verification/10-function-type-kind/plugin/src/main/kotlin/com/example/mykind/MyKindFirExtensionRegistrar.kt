package com.example.mykind

import com.example.mykind.fir.MyKindFunctionTypeKindExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class MyKindFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::MyKindFunctionTypeKindExtension
    }
}
