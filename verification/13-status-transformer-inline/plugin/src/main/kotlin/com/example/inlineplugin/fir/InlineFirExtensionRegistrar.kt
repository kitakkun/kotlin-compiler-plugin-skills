package com.example.inlineplugin.fir

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class InlineFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::MakeInlineStatusTransformer
    }
}
