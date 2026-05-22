package com.example.positive

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class PositiveExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::PositiveAttributeExtension
        +::PositiveCheckersExtension
        registerDiagnosticContainers(PositiveDiagnostics)
    }
}
