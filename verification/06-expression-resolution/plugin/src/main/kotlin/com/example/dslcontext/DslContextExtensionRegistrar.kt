package com.example.dslcontext

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class DslContextExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::DslContextReceiverInjector
    }
}
