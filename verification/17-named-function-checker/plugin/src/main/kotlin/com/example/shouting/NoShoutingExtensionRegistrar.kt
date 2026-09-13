package com.example.shouting

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class NoShoutingExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::NoShoutingCheckersExtension
        registerDiagnosticContainers(NoShoutingDiagnostics)
    }
}
