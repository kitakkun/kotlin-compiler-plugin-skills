package com.example.checker

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class MustBeFinalExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::MustBeFinalCheckersExtension
        registerDiagnosticContainers(MustBeFinalDiagnostics)
    }
}
