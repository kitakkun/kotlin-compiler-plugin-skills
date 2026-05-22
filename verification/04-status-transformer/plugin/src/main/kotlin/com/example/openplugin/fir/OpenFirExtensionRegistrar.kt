package com.example.openplugin.fir

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class OpenFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::MakeOpenStatusTransformer
    }
}
