package com.example.supertype

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class TaggedExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::TaggedSupertypeGenerator
    }
}
